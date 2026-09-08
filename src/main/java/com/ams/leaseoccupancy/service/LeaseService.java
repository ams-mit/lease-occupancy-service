package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.IdentityUserValidation;
import com.ams.leaseoccupancy.dto.LeaseCreateRequest;
import com.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
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
 * Enforces the lease business rules documented in AGENTS.md §3: date-overlap checks,
 * activation prerequisites, and valid lease-status transitions.
 */
@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final IdentityServiceClient identityServiceClient;

    public LeaseService(LeaseRepository leaseRepository, IdentityServiceClient identityServiceClient) {
        this.leaseRepository = leaseRepository;
        this.identityServiceClient = identityServiceClient;
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

        assertNoOverlappingActiveLease(request.unitId(), request.startDate(), request.endDate(), null);

        Lease lease = new Lease();
        lease.setUnitId(request.unitId());
        lease.setTenantId(request.tenantId());
        lease.setStartDate(request.startDate());
        lease.setEndDate(request.endDate());
        lease.setStatus(LeaseStatus.PENDING);
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

        // Activation prerequisite: re-check for conflicts, since time has passed since creation
        // and another lease may have become active on this unit in the meantime.
        if (target == LeaseStatus.ACTIVE) {
            assertNoOverlappingActiveLease(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId());
        }

        lease.setStatus(target);
        return leaseRepository.save(lease);
    }

    private void assertNoOverlappingActiveLease(UUID unitId, LocalDate startDate, LocalDate endDate, UUID excludeLeaseId) {
        if (leaseRepository.existsOverlappingActiveLease(unitId, startDate, endDate, excludeLeaseId)) {
            throw new LeaseConflictException(
                    "Unit " + unitId + " already has an active lease overlapping the requested dates");
        }
    }

    private void assertValidTransition(LeaseStatus current, LeaseStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == LeaseStatus.ACTIVE || target == LeaseStatus.TERMINATED;
            case ACTIVE -> target == LeaseStatus.TERMINATED || target == LeaseStatus.COMPLETED;
            case TERMINATED, COMPLETED -> false;
        };

        if (!valid) {
            throw new InvalidLeaseStatusTransitionException(
                    "Cannot transition lease from " + current + " to " + target);
        }
    }
}
