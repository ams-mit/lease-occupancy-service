package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.IdentityUserValidation;
import com.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import com.ams.leaseoccupancy.client.UnitCapacityResponse;
import com.ams.leaseoccupancy.dto.LeaseCreateRequest;
import com.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.exception.CapacityLimitExceededException;
import com.ams.leaseoccupancy.exception.InvalidLeaseDatesException;
import com.ams.leaseoccupancy.exception.InvalidLeaseStatusTransitionException;
import com.ams.leaseoccupancy.exception.InvalidTenantException;
import com.ams.leaseoccupancy.exception.LeaseConflictException;
import com.ams.leaseoccupancy.exception.LeaseNotFoundException;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import com.ams.leaseoccupancy.repository.LeaseSpecifications;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces conflict-validation rules, interval overlap query engine,
 * multi-occupancy capacity checks, and concurrency control during activation.
 */
@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final IdentityServiceClient identityServiceClient;
    private final PropertyUnitServiceClient propertyUnitServiceClient;
    private final UnitLockService unitLockService;

    public LeaseService(
            LeaseRepository leaseRepository,
            IdentityServiceClient identityServiceClient,
            PropertyUnitServiceClient propertyUnitServiceClient,
            UnitLockService unitLockService) {
        this.leaseRepository = leaseRepository;
        this.identityServiceClient = identityServiceClient;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
        this.unitLockService = unitLockService;
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
        return leaseRepository.save(lease);
    }

    @Transactional(readOnly = true)
    public Page<Lease> listLeases(LeaseStatus status, LocalDate activeOn, Pageable pageable) {
        Specification<Lease> spec = Specification
                .where(LeaseSpecifications.hasStatus(status))
                .and(LeaseSpecifications.activeOn(activeOn));
        return leaseRepository.findAll(spec, pageable);
    }

    @Transactional
    public Lease updateStatus(UUID leaseId, LeaseStatusUpdateRequest request) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new LeaseNotFoundException(leaseId));

        LeaseStatus target = request.status();
        assertValidTransition(lease.getStatus(), target);

        // Activation prerequisites with concurrency control:
        // 1. Acquire pessimistic lock on the unit record to serialize concurrent activations
        // 2. Execute interval overlap rule & multi-occupancy capacity check against property-unit-service
        if (target == LeaseStatus.ACTIVE) {
            unitLockService.acquireUnitLock(lease.getUnitId());
            validateUnitCapacityAndOverlap(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId());
        }

        lease.setStatus(target);
        return leaseRepository.save(lease);
    }

    /**
     * Checks whether a tenant has an active lease on a unit (for operations/facility validation).
     */
    @Transactional(readOnly = true)
    public boolean isTenantActiveInUnit(UUID tenantId, UUID unitId) {
        return leaseRepository.existsByUnitIdAndTenantIdAndStatus(unitId, tenantId, LeaseStatus.ACTIVE);
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
        int capacity = (capacityResponse != null && capacityResponse.capacityLimit() > 0)
                ? capacityResponse.capacityLimit()
                : 1;

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
