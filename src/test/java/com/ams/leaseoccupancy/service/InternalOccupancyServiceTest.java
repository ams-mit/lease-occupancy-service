package com.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalOccupancyServiceTest {

    @Mock
    private LeaseRepository leaseRepository;

    @InjectMocks
    private InternalOccupancyService internalOccupancyService;

    @Test
    void isTenantActiveInUnit_delegatesToRepository() {
        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(leaseRepository.existsByUnitIdAndTenantIdAndStatus(unitId, tenantId, LeaseStatus.ACTIVE))
                .thenReturn(true);

        assertThat(internalOccupancyService.isTenantActiveInUnit(tenantId, unitId)).isTrue();
    }

    @Test
    void getActiveBillingTargets_returnsOnlyActiveLeases() {
        Lease active = new Lease();
        active.setStatus(LeaseStatus.ACTIVE);
        when(leaseRepository.findByStatus(LeaseStatus.ACTIVE)).thenReturn(List.of(active));

        assertThat(internalOccupancyService.getActiveBillingTargets()).containsExactly(active);
    }
}
