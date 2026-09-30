package kln.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import kln.ams.leaseoccupancy.dto.UnitOccupancyResponse;
import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.UnitCapacityResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupantsResponse;
import kln.ams.leaseoccupancy.dto.UserOccupancyResponse;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.repository.LeaseRepository;
import kln.ams.leaseoccupancy.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalOccupancyServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private LeaseRepository leaseRepository;

    @Mock
    private PropertyUnitServiceClient propertyUnitServiceClient;

    @InjectMocks
    private InternalOccupancyService internalOccupancyService;

    @Test
    void getUnitOccupancy_returnsActiveTrue_whenActiveOccupancyExists() {
        UUID unitId = UUID.randomUUID();
        when(propertyUnitServiceClient.getUnitCapacity(unitId)).thenReturn(new UnitCapacityResponse(unitId, 1));
        UUID leaseId = UUID.randomUUID();
        Occupancy occ = new Occupancy();
        occ.setId(UUID.randomUUID());
        occ.setUnitId(unitId);
        occ.setLeaseId(leaseId);
        occ.setResidentId(UUID.randomUUID());
        occ.setStatus(OccupancyStatus.ACTIVE);
        occ.setMoveInDate(LocalDate.now());
        occ.setMoveOutDate(LocalDate.of(2027, 9, 30));

        when(occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE))
                .thenReturn(List.of(occ));
        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(activeLease(leaseId)));

        UnitOccupancyResponse response = internalOccupancyService.getUnitOccupancy(unitId);
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.active()).isTrue();
        assertThat(response.leaseId()).isEqualTo(leaseId);
        assertThat(response.status()).isEqualTo(OccupancyStatus.ACTIVE);
    }

    @Test
    void getUnitOccupancy_returnsActiveFalse_whenNoActiveOccupancy() {
        UUID unitId = UUID.randomUUID();
        when(propertyUnitServiceClient.getUnitCapacity(unitId)).thenReturn(new UnitCapacityResponse(unitId, 1));
        when(occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE))
                .thenReturn(List.of());

        UnitOccupancyResponse response = internalOccupancyService.getUnitOccupancy(unitId);
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.active()).isFalse();
        assertThat(response.status()).isNull();
    }

    @Test
    void getUnitOccupants_returnsOccupantsList() {
        UUID unitId = UUID.randomUUID();
        when(propertyUnitServiceClient.getUnitCapacity(unitId)).thenReturn(new UnitCapacityResponse(unitId, 1));
        UUID residentId = UUID.randomUUID();
        Occupancy occ = new Occupancy();
        occ.setId(UUID.randomUUID());
        occ.setUnitId(unitId);
        occ.setResidentId(residentId);
        UUID leaseId = UUID.randomUUID();
        occ.setLeaseId(leaseId);
        occ.setStatus(OccupancyStatus.ACTIVE);
        occ.setMoveInDate(LocalDate.now());

        when(occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE))
                .thenReturn(List.of(occ));
        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(activeLease(leaseId)));

        UnitOccupantsResponse response = internalOccupancyService.getUnitOccupants(unitId);
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.occupants()).hasSize(1);
        assertThat(response.occupants().get(0).residentId()).isEqualTo(residentId);
    }

    @Test
    void getUserOccupancy_failsClosedWithoutUserResidentMapping() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> internalOccupancyService.getUserOccupancy(userId))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    private Lease activeLease(UUID id) {
        Lease lease = new Lease();
        lease.setId(id);
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setStartDate(LocalDate.now().minusDays(1));
        lease.setEndDate(LocalDate.now().plusDays(30));
        return lease;
    }
}
