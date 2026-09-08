package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the internal, no-JWT endpoints consumed by sibling services (API-STANDARD-v1 §26-27).
 *
 * <p>Currently backed by the Lease table directly: a tenant is treated as "residing" in a
 * unit while their lease is ACTIVE. Once the physical Occupant entity (AGENTS.md §3, group B
 * endpoints) exists, {@link #isTenantActiveInUnit} should switch to querying occupants
 * instead, since a lease's tenant of record and its physical occupants can differ.
 */
@Service
public class InternalOccupancyService {

    private final LeaseRepository leaseRepository;

    public InternalOccupancyService(LeaseRepository leaseRepository) {
        this.leaseRepository = leaseRepository;
    }

    @Transactional(readOnly = true)
    public boolean isTenantActiveInUnit(UUID tenantId, UUID unitId) {
        return leaseRepository.existsByUnitIdAndTenantIdAndStatus(unitId, tenantId, LeaseStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Lease> getActiveBillingTargets() {
        return leaseRepository.findByStatus(LeaseStatus.ACTIVE);
    }
}
