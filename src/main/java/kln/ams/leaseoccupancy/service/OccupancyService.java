package kln.ams.leaseoccupancy.service;

import kln.ams.leaseoccupancy.client.IdentityServiceClient;
import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.UnitDetailsResponse;
import kln.ams.leaseoccupancy.config.AuthContext;
import kln.ams.leaseoccupancy.dto.OccupancyCreateRequest;
import kln.ams.leaseoccupancy.dto.OccupancyStatusUpdateRequest;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.entity.OccupancyStatusHistory;
import kln.ams.leaseoccupancy.entity.Occupant;
import kln.ams.leaseoccupancy.exception.InvalidTenantException;
import kln.ams.leaseoccupancy.exception.LeaseNotActiveException;
import kln.ams.leaseoccupancy.exception.LeaseNotFoundException;
import kln.ams.leaseoccupancy.exception.OccupancyNotFoundException;
import kln.ams.leaseoccupancy.exception.OccupancyRuleException;
import kln.ams.leaseoccupancy.exception.OccupancyStatusTransitionException;
import kln.ams.leaseoccupancy.repository.LeaseRepository;
import kln.ams.leaseoccupancy.repository.OccupancyRepository;
import kln.ams.leaseoccupancy.repository.OccupancyStatusHistoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing physical Occupancy, status transitions, move-in/move-out,
 * and occupant queries per canonical contract.
 */
@Service
public class OccupancyService {

    private final OccupancyRepository occupancies;
    private final LeaseRepository leases;
    private final IdentityServiceClient identity;
    private final PropertyUnitServiceClient property;
    private final UnitLockService locks;
    private final OccupancyStatusHistoryRepository statusHistory;

    public OccupancyService(
            OccupancyRepository occupancies,
            LeaseRepository leases,
            IdentityServiceClient identity,
            PropertyUnitServiceClient property,
            UnitLockService locks,
            OccupancyStatusHistoryRepository statusHistory) {
        this.occupancies = occupancies;
        this.leases = leases;
        this.identity = identity;
        this.property = property;
        this.locks = locks;
        this.statusHistory = statusHistory;
    }

    /**
     * LEASE-008: Register Occupancy / Move-In.
     */
    @Transactional
    public Occupancy register(OccupancyCreateRequest request) {
        locks.acquireUnitLock(request.unitId());

        UnitDetailsResponse unit = property.getUnitDetails(request.unitId());
        if (unit != null && unit.isUnderMaintenance()) {
            throw new OccupancyRuleException("Unit is under maintenance");
        }

        Lease lease = leases.findById(request.leaseId())
                .orElseThrow(() -> new LeaseNotFoundException(request.leaseId()));

        if (!lease.getUnitId().equals(request.unitId())) {
            throw new OccupancyRuleException("Lease " + request.leaseId() + " does not belong to unit " + request.unitId());
        }

        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new LeaseNotActiveException("Lease " + request.leaseId() + " is not ACTIVE");
        }

        if (request.startDate().isBefore(lease.getStartDate()) || request.startDate().isAfter(lease.getEndDate())) {
            throw new OccupancyRuleException("Occupancy start date must be within active lease period ["
                    + lease.getStartDate() + " to " + lease.getEndDate() + "]");
        }

        boolean permitted = lease.getTenantId().equals(request.residentId())
                || lease.getOccupants().stream().map(Occupant::getResidentId).anyMatch(request.residentId()::equals);

        if (!permitted) {
            throw new OccupancyRuleException("Resident is not a permitted occupant of this lease");
        }

        if (!identity.validateUser(request.residentId()).isValidTenant()) {
            throw new InvalidTenantException(request.residentId());
        }

        if (occupancies.existsByUnitIdAndResidentIdAndStatus(request.unitId(), request.residentId(), OccupancyStatus.ACTIVE)) {
            throw new OccupancyRuleException("Resident already has an active occupancy in this unit");
        }

        if (unit != null && unit.capacityLimit() > 0
                && occupancies.countByUnitIdAndStatus(request.unitId(), OccupancyStatus.ACTIVE) >= unit.capacityLimit()) {
            throw new OccupancyRuleException("Unit occupancy capacity has been reached");
        }

        Occupancy occupancy = new Occupancy();
        occupancy.setUnitId(request.unitId());
        occupancy.setResidentId(request.residentId());
        occupancy.setLeaseId(request.leaseId());
        occupancy.setMoveInDate(request.startDate());
        occupancy.setNotes(request.notes());
        occupancy.setStatus(OccupancyStatus.ACTIVE);

        Occupancy saved = occupancies.save(occupancy);
        recordStatus(saved, null, OccupancyStatus.ACTIVE, "Move-in completed");
        return saved;
    }

    /**
     * LEASE-009: List Occupants by Unit with optional status filter, history inclusion, and pagination.
     */
    @Transactional(readOnly = true)
    public Page<Occupancy> listOccupantsForUnit(UUID unitId, OccupancyStatus status, boolean includeHistory, Pageable pageable) {
        property.getUnitDetails(unitId);

        if (status != null) {
            return occupancies.findByUnitIdAndStatus(unitId, status, pageable);
        }

        if (includeHistory) {
            return occupancies.findByUnitId(unitId, pageable);
        }

        return occupancies.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE, pageable);
    }

    /**
     * LEASE-010: Resident Occupancy History.
     */
    @Transactional(readOnly = true)
    public List<Occupancy> historyForResident(UUID residentId) {
        return occupancies.findByResidentIdOrderByMoveInDateDesc(residentId);
    }

    /**
     * LEASE-011: Change Occupancy Status / Record Move-Out.
     */
    @Transactional
    public Occupancy updateStatus(UUID occupancyId, OccupancyStatusUpdateRequest request) {
        Occupancy occupancy = occupancies.findById(occupancyId)
                .orElseThrow(() -> new OccupancyNotFoundException(occupancyId));

        locks.acquireUnitLock(occupancy.getUnitId());

        OccupancyStatus current = occupancy.getStatus();
        OccupancyStatus target = request.status();
        assertValidTransition(current, target);

        occupancy.setStatus(target);
        if (target == OccupancyStatus.ENDED) {
            occupancy.setMoveOutDate(request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now());
        }

        Occupancy saved = occupancies.save(occupancy);
        recordStatus(saved, current, target, request.reason());
        return saved;
    }

    private void recordStatus(Occupancy occupancy, OccupancyStatus from, OccupancyStatus to, String reason) {
        OccupancyStatusHistory entry = new OccupancyStatusHistory();
        entry.setOccupancyId(occupancy.getId());
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(AuthContext.subjectOrSystem());
        entry.setReason(reason);
        statusHistory.save(entry);
    }

    private void assertValidTransition(OccupancyStatus current, OccupancyStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == OccupancyStatus.ACTIVE || target == OccupancyStatus.CANCELLED;
            case ACTIVE -> target == OccupancyStatus.ENDED;
            case ENDED, CANCELLED -> false;
        };

        if (!valid) {
            throw new OccupancyStatusTransitionException(
                    "Cannot transition occupancy from " + current + " to " + target);
        }
    }
}
