package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.IdentityUserValidation;
import com.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import com.ams.leaseoccupancy.client.UnitCapacityResponse;
import com.ams.leaseoccupancy.client.UnitDetailsResponse;
import com.ams.leaseoccupancy.config.AuthContext;
import com.ams.leaseoccupancy.dto.ActiveOccupancyResponse;
import com.ams.leaseoccupancy.dto.LeaseCreateRequest;
import com.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.entity.LeaseStatusHistory;
import com.ams.leaseoccupancy.entity.OccupancyStatus;
import com.ams.leaseoccupancy.entity.Occupancy;
import com.ams.leaseoccupancy.exception.CapacityLimitExceededException;
import com.ams.leaseoccupancy.exception.InvalidLeaseDatesException;
import com.ams.leaseoccupancy.exception.InvalidLeaseStatusTransitionException;
import com.ams.leaseoccupancy.exception.InvalidTenantException;
import com.ams.leaseoccupancy.exception.LeaseConflictException;
import com.ams.leaseoccupancy.exception.LeaseNotFoundException;
import com.ams.leaseoccupancy.exception.OccupancyNotFoundException;
import com.ams.leaseoccupancy.exception.UnitUnderMaintenanceException;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import com.ams.leaseoccupancy.repository.LeaseSpecifications;
import com.ams.leaseoccupancy.repository.LeaseStatusHistoryRepository;
import com.ams.leaseoccupancy.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces conflict-validation rules, interval overlap query engine,
 * multi-occupancy capacity checks, unit status transitions, and concurrency control.
 */
@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final IdentityServiceClient identityServiceClient;
    private final PropertyUnitServiceClient propertyUnitServiceClient;
    private final UnitLockService unitLockService;
    private final OccupancyRepository occupancyRepository;
    private final LeaseStatusHistoryRepository statusHistory;

    public LeaseService(
            LeaseRepository leaseRepository,
            IdentityServiceClient identityServiceClient,
            PropertyUnitServiceClient propertyUnitServiceClient,
            UnitLockService unitLockService,
            OccupancyRepository occupancyRepository,
            LeaseStatusHistoryRepository statusHistory) {
        this.leaseRepository = leaseRepository;
        this.identityServiceClient = identityServiceClient;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
        this.unitLockService = unitLockService;
        this.occupancyRepository = occupancyRepository;
        this.statusHistory = statusHistory;
    }

    @Transactional
    public Lease createLease(LeaseCreateRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new InvalidLeaseDatesException("endDate must be after startDate");
        }

        IdentityUserValidation tenant = identityServiceClient.validateUser(request.tenantId());
        if (!tenant.isValidTenant()) {
            throw new InvalidTenantException(request.tenantId());
        }

        validateUnitCapacityAndOverlap(request.unitId(), request.startDate(), request.endDate(), null);

        Lease lease = new Lease();
        lease.setUnitId(request.unitId());
        lease.setTenantId(request.tenantId());
        lease.setStartDate(request.startDate());
        lease.setEndDate(request.endDate());
        lease.setStatus(LeaseStatus.DRAFT);
        lease.setCustomNotes(request.customNotes());
        Lease saved = leaseRepository.save(lease);
        recordStatus(saved, null, LeaseStatus.DRAFT, "Lease created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Lease> listLeases(LeaseStatus status, LocalDate activeOn, Pageable pageable) {
        Specification<Lease> spec = Specification
                .where(LeaseSpecifications.hasStatus(status))
                .and(LeaseSpecifications.activeOn(activeOn));
        return leaseRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Lease getLease(UUID leaseId) {
        return leaseRepository.findWithOccupants(leaseId)
                .orElseThrow(() -> new LeaseNotFoundException(leaseId));
    }

    @Transactional(readOnly = true)
    public List<LeaseStatusHistory> statusHistory(UUID leaseId) {
        return statusHistory.findByLeaseIdOrderByChangedAtAsc(leaseId);
    }

    @Transactional(readOnly = true)
    public List<Lease> historyForUnit(UUID unitId) {
        propertyUnitServiceClient.getUnitDetails(unitId);
        return leaseRepository.findByUnitIdOrderByStartDateDesc(unitId);
    }

    public UUID ownerOfUnit(UUID unitId) {
        return propertyUnitServiceClient.getUnitDetails(unitId).ownerId();
    }

    @Transactional
    public Lease updateStatus(UUID leaseId, LeaseStatusUpdateRequest request) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new LeaseNotFoundException(leaseId));

        LeaseStatus target = request.status();
        LeaseStatus previous = lease.getStatus();
        assertValidTransition(previous, target);

        // Activation prerequisites with concurrency control:
        // 1. Acquire pessimistic lock on the unit record to serialize concurrent activations
        // 2. Verify target unit is not under maintenance
        // 3. Execute interval overlap rule & multi-occupancy capacity check against property-unit-service
        // 4. Auto-trigger unit status transition to OCCUPIED in property-unit-service
        if (target == LeaseStatus.ACTIVE) {
            unitLockService.acquireUnitLock(lease.getUnitId());

            LocalDate today = LocalDate.now();
            if (today.isBefore(lease.getStartDate()) || today.isAfter(lease.getEndDate())) {
                throw new InvalidLeaseDatesException("A lease can only be activated within its agreed period");
            }

            IdentityUserValidation tenant = identityServiceClient.validateUser(lease.getTenantId());
            if (!tenant.isValidTenant()) {
                throw new InvalidTenantException(lease.getTenantId());
            }

            UnitDetailsResponse unitDetails = propertyUnitServiceClient.getUnitDetails(lease.getUnitId());
            if (unitDetails != null && unitDetails.isUnderMaintenance()) {
                throw new UnitUnderMaintenanceException(lease.getUnitId());
            }

            validateUnitCapacityAndOverlap(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId());

            lease.setStatus(target);
            Lease saved = leaseRepository.save(lease);
            recordStatus(saved, previous, target, request.reason());

            // Auto-trigger REST hook / sync with property-unit-service (P1G2-07)
            propertyUnitServiceClient.updateUnitStatus(lease.getUnitId(), "OCCUPIED");
            return saved;
        }

        lease.setStatus(target);
        Lease saved = leaseRepository.save(lease);
        recordStatus(saved, previous, target, request.reason());
        if (previous == LeaseStatus.ACTIVE
                && (target == LeaseStatus.TERMINATED || target == LeaseStatus.EXPIRED)) {
            for (Occupancy occupancy : occupancyRepository.findByLeaseIdAndStatus(
                    leaseId, OccupancyStatus.ACTIVE)) {
                occupancy.setStatus(OccupancyStatus.INACTIVE);
                occupancy.setMoveOutDate(LocalDate.now());
                occupancyRepository.save(occupancy);
            }
            LocalDate today = LocalDate.now();
            boolean anotherActiveLease = leaseRepository
                    .findByUnitIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                            lease.getUnitId(), LeaseStatus.ACTIVE, today, today)
                    .stream().anyMatch(other -> !other.getId().equals(leaseId));
            if (!anotherActiveLease) {
                UnitDetailsResponse unit = propertyUnitServiceClient.getUnitDetails(lease.getUnitId());
                if ("OCCUPIED".equals(unit.status())) {
                    propertyUnitServiceClient.updateUnitStatus(lease.getUnitId(), "AVAILABLE");
                }
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
     * Checks whether a tenant has a physical active occupancy on a unit.
     * Validates target unit existence with property-unit-service.
     */
    @Transactional(readOnly = true)
    public boolean isTenantActiveInUnit(UUID tenantId, UUID unitId) {
        propertyUnitServiceClient.getUnitDetails(unitId);
        return occupancyRepository.existsCurrentlyEligibleResident(unitId, tenantId, LocalDate.now());
    }

    /**
     * Retrieves the current active occupancy record for a unit (consumed by billing-service).
     * Validates unit existence and returns active occupant ID, owner ID, and lease terms.
     */
    @Transactional(readOnly = true)
    public ActiveOccupancyResponse getActiveOccupancy(UUID unitId) {
        UnitDetailsResponse unitDetails = propertyUnitServiceClient.getUnitDetails(unitId);
        UUID ownerId = unitDetails != null ? unitDetails.ownerId() : null;

        LocalDate today = LocalDate.now();
        List<Lease> activeLeases = leaseRepository
                .findByUnitIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        unitId, LeaseStatus.ACTIVE, today, today);
        if (activeLeases.isEmpty()) {
            throw new OccupancyNotFoundException(unitId);
        }

        Lease activeLease = activeLeases.get(0);
        return ActiveOccupancyResponse.from(activeLease, ownerId);
    }

    /**
     * Executes the interval overlap query: (NewStart <= ExistingEnd) AND (NewEnd >= ExistingStart)
     * and evaluates multi-occupancy capacity against property-unit-service's UnitType capacity limit:
     * - If Capacity == 1: any overlapping active lease blocks with 409 Conflict.
     * - If Capacity > 1: allows concurrent leases if (overlapping + 1) <= Capacity;
     *   rejects with 422 Unprocessable Entity if Capacity is breached.
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
            case DRAFT -> target == LeaseStatus.PENDING_ACTIVATION || target == LeaseStatus.ACTIVE || target == LeaseStatus.TERMINATED;
            case PENDING_ACTIVATION -> target == LeaseStatus.ACTIVE || target == LeaseStatus.TERMINATED;
            case ACTIVE -> target == LeaseStatus.TERMINATED || target == LeaseStatus.EXPIRED;
            case TERMINATED, EXPIRED -> false;
        };

        if (!valid) {
            throw new InvalidLeaseStatusTransitionException(
                    "Cannot transition lease from " + current + " to " + target);
        }
    }
}
