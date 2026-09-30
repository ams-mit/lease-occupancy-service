package kln.ams.leaseoccupancy.service;

import kln.ams.leaseoccupancy.client.ResidentServiceClient;
import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.UnitCapacityResponse;
import kln.ams.leaseoccupancy.client.UnitDetailsResponse;
import kln.ams.leaseoccupancy.config.AuthContext;
import kln.ams.leaseoccupancy.dto.ActiveOccupancyResponse;
import kln.ams.leaseoccupancy.dto.LeaseCreateRequest;
import kln.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.entity.LeaseStatusHistory;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.entity.Occupant;
import kln.ams.leaseoccupancy.exception.CapacityLimitExceededException;
import kln.ams.leaseoccupancy.exception.InvalidLeaseDatesException;
import kln.ams.leaseoccupancy.exception.InvalidLeaseStatusTransitionException;
import kln.ams.leaseoccupancy.exception.InvalidTenantException;
import kln.ams.leaseoccupancy.exception.LeaseConflictException;
import kln.ams.leaseoccupancy.exception.LeaseNotFoundException;
import kln.ams.leaseoccupancy.exception.UnitUnderMaintenanceException;
import kln.ams.leaseoccupancy.exception.UnitNotEligibleException;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.repository.LeaseRepository;
import kln.ams.leaseoccupancy.repository.LeaseSpecifications;
import kln.ams.leaseoccupancy.repository.LeaseStatusHistoryRepository;
import kln.ams.leaseoccupancy.repository.OccupancyRepository;
import kln.ams.leaseoccupancy.repository.OccupancyStatusHistoryRepository;
import kln.ams.leaseoccupancy.entity.OccupancyStatusHistory;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing Lease lifecycle, status transitions, conflict validation,
 * and capacity enforcement per canonical contract.
 */
@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final ResidentServiceClient residents;
    private final PropertyUnitServiceClient propertyUnitServiceClient;
    private final UnitLockService unitLockService;
    private final OccupancyRepository occupancyRepository;
    private final LeaseStatusHistoryRepository statusHistory;
    private final OccupancyStatusHistoryRepository occupancyStatusHistory;

    public LeaseService(
            LeaseRepository leaseRepository,
            ResidentServiceClient residents,
            PropertyUnitServiceClient propertyUnitServiceClient,
            UnitLockService unitLockService,
            OccupancyRepository occupancyRepository,
            LeaseStatusHistoryRepository statusHistory,
            OccupancyStatusHistoryRepository occupancyStatusHistory) {
        this.leaseRepository = leaseRepository;
        this.residents = residents;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
        this.unitLockService = unitLockService;
        this.occupancyRepository = occupancyRepository;
        this.statusHistory = statusHistory;
        this.occupancyStatusHistory = occupancyStatusHistory;
    }

    @Transactional
    public Lease createLease(LeaseCreateRequest request) {
        if (request.startDate() == null || request.endDate() == null || request.endDate().isBefore(request.startDate())) {
            throw new InvalidLeaseDatesException("startDate must be before or equal to endDate");
        }

        if (request.occupants() == null || request.occupants().isEmpty()) {
            throw new InvalidLeaseDatesException("At least one occupant/responsible party is required");
        }

        UnitDetailsResponse unit = propertyUnitServiceClient.getUnitDetails(request.unitId());
        if (unit == null) throw new DependencyUnavailableException("property-unit-service", null);
        if (unit.isUnderMaintenance()) throw new UnitUnderMaintenanceException(request.unitId());
        if (!unit.isEligibleForNewOccupancy()) throw new UnitNotEligibleException(request.unitId());

        // Validate unit capacity and overlap with property service
        validateUnitCapacityAndOverlap(request.unitId(), request.startDate(), request.endDate(), null);

        // Occupant IDs are Resident Management profile IDs, not Identity Access user IDs.
        for (LeaseCreateRequest.OccupantInput occupantInput : request.occupants()) {
            if (!residents.isValidResident(occupantInput.residentId()))
                throw new InvalidTenantException(occupantInput.residentId());
        }

        UUID primaryTenantId = request.occupants().get(0).residentId();

        Lease lease = new Lease();
        lease.setUnitId(request.unitId());
        lease.setTenantId(primaryTenantId);
        lease.setStartDate(request.startDate());
        lease.setEndDate(request.endDate());
        lease.setStatus(LeaseStatus.DRAFT);
        lease.setCustomNotes(request.notes());

        for (int i = 0; i < request.occupants().size(); i++) {
            UUID residentId = request.occupants().get(i).residentId();
            lease.addOccupant(new Occupant(residentId, i == 0));
        }

        Lease saved = leaseRepository.save(lease);
        recordStatus(saved, null, LeaseStatus.DRAFT, "Lease created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Lease> listLeases(
            UUID unitId,
            UUID residentId,
            UUID ownerId,
            LeaseStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        Specification<Lease> spec = Specification
                .where(LeaseSpecifications.hasUnitId(unitId))
                .and(LeaseSpecifications.hasTenantOrOccupantId(residentId))
                .and(LeaseSpecifications.hasStatus(status))
                .and(LeaseSpecifications.startDateOnOrAfter(startDate))
                .and(LeaseSpecifications.endDateOnOrBefore(endDate));

        if (ownerId != null) {
            Set<UUID> matchingUnits = leaseRepository.findAll(spec).stream()
                    .map(Lease::getUnitId).distinct()
                    .filter(candidate -> propertyUnitServiceClient.getOwnerIds(candidate).contains(ownerId))
                    .collect(Collectors.toSet());
            spec = spec.and(LeaseSpecifications.unitIdIn(matchingUnits));
        }

        return leaseRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Lease getLease(UUID leaseId) {
        return leaseRepository.findWithOccupants(leaseId)
                .orElseThrow(() -> new LeaseNotFoundException(leaseId));
    }

    @Transactional(readOnly = true)
    public List<LeaseStatusHistory> statusHistory(UUID leaseId) {
        // Ensure lease exists
        getLease(leaseId);
        return statusHistory.findByLeaseIdOrderByChangedAtAsc(leaseId);
    }

    @Transactional(readOnly = true)
    public List<Lease> historyForUnit(UUID unitId) {
        propertyUnitServiceClient.getUnitDetails(unitId);
        return leaseRepository.findByUnitIdOrderByStartDateDesc(unitId);
    }

    @Transactional
    public Lease updateStatus(UUID leaseId, LeaseStatusUpdateRequest request) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new LeaseNotFoundException(leaseId));

        LeaseStatus target = request.status();
        LeaseStatus previous = lease.getStatus();
        assertValidTransition(previous, target);

        // Activation prerequisites:
        // 1. Acquire pessimistic lock on the unit record to serialize concurrent activations
        // 2. Verify target unit is not under maintenance
        // 3. Execute interval overlap rule & multi-occupancy capacity check against property-unit-service
        // 4. Validate tenant and occupants
        if (target == LeaseStatus.ACTIVE) {
            unitLockService.acquireUnitLock(lease.getUnitId());

            LocalDate today = LocalDate.now();
            if (today.isBefore(lease.getStartDate()) || today.isAfter(lease.getEndDate())) {
                throw new InvalidLeaseDatesException("A lease can only be activated within its agreed period");
            }

            UnitDetailsResponse unitDetails = propertyUnitServiceClient.getUnitDetails(lease.getUnitId());
            if (unitDetails == null) throw new DependencyUnavailableException("property-unit-service", null);
            if (unitDetails.isUnderMaintenance()) throw new UnitUnderMaintenanceException(lease.getUnitId());
            if (!unitDetails.isEligibleForNewOccupancy()) throw new UnitNotEligibleException(lease.getUnitId());

            for (Occupant occupant : lease.getOccupants()) {
                if (!residents.isValidResident(occupant.getResidentId()))
                    throw new InvalidTenantException(occupant.getResidentId());
            }

            validateUnitCapacityAndOverlap(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId());

            lease.setStatus(target);
            Lease saved = leaseRepository.save(lease);
            recordStatus(saved, previous, target, request.reason());
            return saved;
        }

        lease.setStatus(target);
        Lease saved = leaseRepository.save(lease);
        recordStatus(saved, previous, target, request.reason());

        // When a lease is terminated or expired, update related active occupancies to ENDED
        if (previous == LeaseStatus.ACTIVE && (target == LeaseStatus.TERMINATED || target == LeaseStatus.EXPIRED)) {
            for (Occupancy occupancy : occupancyRepository.findByLeaseIdAndStatus(leaseId, OccupancyStatus.ACTIVE)) {
                occupancy.setStatus(OccupancyStatus.ENDED);
                occupancy.setMoveOutDate(LocalDate.now());
                occupancyRepository.save(occupancy);
                OccupancyStatusHistory entry = new OccupancyStatusHistory();
                entry.setOccupancyId(occupancy.getId());
                entry.setFromStatus(OccupancyStatus.ACTIVE);
                entry.setToStatus(OccupancyStatus.ENDED);
                entry.setChangedBy(AuthContext.subjectOrSystem());
                entry.setReason("Lease " + target + ": " + request.reason());
                occupancyStatusHistory.save(entry);
            }
        }
        return saved;
    }

    private void recordStatus(Lease lease, LeaseStatus from, LeaseStatus to, String reason) {
        LeaseStatusHistory entry = new LeaseStatusHistory();
        entry.setLeaseId(lease.getId());
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(AuthContext.subjectOrSystem());
        entry.setReason(reason);
        statusHistory.save(entry);
    }

    /**
     * LEASE-012: Return effective current occupancy for a unit.
     */
    @Transactional(readOnly = true)
    public ActiveOccupancyResponse getActiveOccupancy(UUID unitId) {
        // Validate unit exists in property-unit-service
        propertyUnitServiceClient.getUnitDetails(unitId);

        LocalDate today = LocalDate.now();
        List<Lease> activeLeases = leaseRepository
                .findByUnitIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        unitId, LeaseStatus.ACTIVE, today, today);

        List<Occupancy> activeOccupancies = occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE)
                .stream().filter(occ -> !occ.getMoveInDate().isAfter(today)
                        && (occ.getMoveOutDate() == null || !occ.getMoveOutDate().isBefore(today)))
                .toList();
        if (activeLeases.isEmpty() || activeOccupancies.isEmpty()) {
            return ActiveOccupancyResponse.inactive(unitId);
        }

        Lease activeLease = activeLeases.stream()
                .filter(lease -> activeOccupancies.stream().anyMatch(occ -> lease.getId().equals(occ.getLeaseId())))
                .findFirst().orElse(null);
        if (activeLease == null) return ActiveOccupancyResponse.inactive(unitId);

        List<ActiveOccupancyResponse.OccupantSummary> occupants = activeOccupancies.stream()
                .filter(occ -> activeLease.getId().equals(occ.getLeaseId()))
                .map(occ -> new ActiveOccupancyResponse.OccupantSummary(occ.getResidentId(), occ.getStatus()))
                .toList();

        return new ActiveOccupancyResponse(
                unitId,
                true,
                activeLease.getId(),
                occupants,
                activeLease.getStartDate(),
                activeLease.getEndDate());
    }

    /**
     * Executes interval overlap query: (NewStart <= ExistingEnd) AND (NewEnd >= ExistingStart)
     * and evaluates multi-occupancy capacity against property-unit-service's UnitType capacity limit:
     * - If Capacity == 1: any overlapping active lease blocks with 409 Conflict.
     * - If Capacity > 1: allows concurrent leases if (overlapping + 1) <= Capacity;
     *   rejects with 409 Conflict if Capacity is breached.
     */
    public void validateUnitCapacityAndOverlap(UUID unitId, LocalDate startDate, LocalDate endDate, UUID excludeLeaseId) {
        UnitCapacityResponse capacityResponse = propertyUnitServiceClient.getUnitCapacity(unitId);
        if (capacityResponse == null || capacityResponse.capacityLimit() <= 0) {
            throw new CapacityLimitExceededException("Unit " + unitId + " has no leasable capacity");
        }
        int capacity = capacityResponse.capacityLimit();

        long overlappingActiveCount = leaseRepository.countOverlappingActiveLeases(unitId, startDate, endDate, excludeLeaseId);

        if (capacity == 1) {
            if (overlappingActiveCount > 0) {
                throw new LeaseConflictException(
                        "Unit " + unitId + " already has an active lease overlapping the requested dates");
            }
        } else {
            if (overlappingActiveCount + 1 > capacity) {
                throw new CapacityLimitExceededException(
                        "Unit " + unitId + " (capacity = " + capacity + ") cannot accept additional lease: already has "
                                + overlappingActiveCount + " active overlapping leases");
            }
        }
    }

    private void assertValidTransition(LeaseStatus current, LeaseStatus target) {
        boolean valid = switch (current) {
            case DRAFT -> target == LeaseStatus.PENDING || target == LeaseStatus.ACTIVE || target == LeaseStatus.CANCELLED;
            case PENDING -> target == LeaseStatus.ACTIVE || target == LeaseStatus.CANCELLED;
            case ACTIVE -> target == LeaseStatus.TERMINATED || target == LeaseStatus.EXPIRED;
            case TERMINATED, EXPIRED, CANCELLED -> false;
        };

        if (!valid) {
            throw new InvalidLeaseStatusTransitionException(
                    "Cannot transition lease from " + current + " to " + target);
        }
    }
}
