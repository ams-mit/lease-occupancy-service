package com.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.IdentityUserValidation;
import com.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import com.ams.leaseoccupancy.client.UnitDetailsResponse;
import com.ams.leaseoccupancy.dto.OccupancyCreateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.entity.Occupancy;
import com.ams.leaseoccupancy.entity.OccupancyStatus;
import com.ams.leaseoccupancy.exception.OccupancyRuleException;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import com.ams.leaseoccupancy.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyServiceTest {
    @Mock OccupancyRepository occupancies;
    @Mock LeaseRepository leases;
    @Mock IdentityServiceClient identity;
    @Mock PropertyUnitServiceClient property;
    @Mock UnitLockService locks;
    @InjectMocks OccupancyService service;

    private UUID unitId;
    private UUID residentId;
    private UUID leaseId;
    private Lease lease;

    @BeforeEach
    void setUp() {
        unitId = UUID.randomUUID();
        residentId = UUID.randomUUID();
        leaseId = UUID.randomUUID();
        lease = new Lease();
        lease.setId(leaseId);
        lease.setUnitId(unitId);
        lease.setTenantId(residentId);
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setStartDate(LocalDate.now().minusDays(10));
        lease.setEndDate(LocalDate.now().plusDays(10));
    }

    @Test
    void register_acceptsPermittedResidentWithActiveLease() {
        when(leases.findById(leaseId)).thenReturn(Optional.of(lease));
        when(identity.validateUser(residentId)).thenReturn(new IdentityUserValidation(residentId, true, true));
        when(property.getUnitDetails(unitId)).thenReturn(new UnitDetailsResponse(unitId, "OCCUPIED", 1, null));
        when(occupancies.save(any(Occupancy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Occupancy result = service.register(new OccupancyCreateRequest(unitId, residentId, leaseId, LocalDate.now()));

        assertThat(result.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(result.getResidentId()).isEqualTo(residentId);
    }

    @Test
    void register_rejectsResidentOutsideLease() {
        UUID otherResident = UUID.randomUUID();
        when(leases.findById(leaseId)).thenReturn(Optional.of(lease));

        assertThatThrownBy(() -> service.register(
                new OccupancyCreateRequest(unitId, otherResident, leaseId, LocalDate.now())))
                .isInstanceOf(OccupancyRuleException.class);
    }

    @Test
    void register_rejectsDuplicateActiveOccupancy() {
        when(leases.findById(leaseId)).thenReturn(Optional.of(lease));
        when(identity.validateUser(residentId)).thenReturn(new IdentityUserValidation(residentId, true, true));
        when(property.getUnitDetails(unitId)).thenReturn(new UnitDetailsResponse(unitId, "OCCUPIED", 1, null));
        when(occupancies.existsByUnitIdAndResidentIdAndStatus(unitId, residentId, OccupancyStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new OccupancyCreateRequest(unitId, residentId, leaseId, LocalDate.now())))
                .isInstanceOf(OccupancyRuleException.class);
    }

    @Test
    void deactivate_recordsMoveOutWithoutDeletingHistory() {
        Occupancy occupancy = new Occupancy();
        occupancy.setUnitId(unitId);
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        when(occupancies.findById(any(UUID.class))).thenReturn(Optional.of(occupancy));
        when(occupancies.save(occupancy)).thenReturn(occupancy);

        Occupancy result = service.deactivate(UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(OccupancyStatus.INACTIVE);
        assertThat(result.getMoveOutDate()).isEqualTo(LocalDate.now());
    }
}
