package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import com.ams.leaseoccupancy.repository.OccupancyRepository;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs Gateway-routed internal endpoints protected by Service JWTs.
 *
 * Residency validation reads physical occupancy records. Billing targets are drawn from
 * currently effective active leases.
 */
@Service
public class InternalOccupancyService {

    private final LeaseRepository leaseRepository;
    private final OccupancyRepository occupancyRepository;

    public InternalOccupancyService(LeaseRepository leaseRepository, OccupancyRepository occupancyRepository) {
        this.leaseRepository = leaseRepository;
        this.occupancyRepository = occupancyRepository;
    }

    @Transactional(readOnly = true)
    public boolean isTenantActiveInUnit(UUID tenantId, UUID unitId) {
        return occupancyRepository.existsCurrentlyEligibleResident(unitId, tenantId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Lease> getActiveBillingTargets() {
        LocalDate today = LocalDate.now();
        return leaseRepository.findByStatus(LeaseStatus.ACTIVE).stream()
                .filter(lease -> !today.isBefore(lease.getStartDate()) && !today.isAfter(lease.getEndDate()))
                .toList();
    }
}
