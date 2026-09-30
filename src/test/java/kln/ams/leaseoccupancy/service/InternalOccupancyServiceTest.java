package kln.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import kln.ams.leaseoccupancy.dto.UnitOccupancyResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupantsResponse;
import kln.ams.leaseoccupancy.dto.UserOccupancyResponse;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.repository.OccupancyRepository;
import java.time.LocalDate;
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
    private OccupancyRepository occupancyRepository;

    @InjectMocks
    private InternalOccupancyService internalOccupancyService;

    @Test
    void getUnitOccupancy_returnsActiveTrue_whenActiveOccupancyExists() {
        UUID unitId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        Occupancy occ = new Occupancy();
        occ.setId(UUID.randomUUID());
        occ.setUnitId(unitId);
        occ.setLeaseId(leaseId);
        occ.setResidentId(UUID.randomUUID());
        occ.setStatus(OccupancyStatus.ACTIVE);
        occ.setMoveInDate(LocalDate.of(2026, 10, 1));
        occ.setMoveOutDate(LocalDate.of(2027, 9, 30));

        when(occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE))
                .thenReturn(List.of(occ));

        UnitOccupancyResponse response = internalOccupancyService.getUnitOccupancy(unitId);
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.active()).isTrue();
        assertThat(response.leaseId()).isEqualTo(leaseId);
        assertThat(response.status()).isEqualTo(OccupancyStatus.ACTIVE);
    }

    @Test
    void getUnitOccupancy_returnsActiveFalse_whenNoActiveOccupancy() {
        UUID unitId = UUID.randomUUID();
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
        UUID residentId = UUID.randomUUID();
        Occupancy occ = new Occupancy();
        occ.setId(UUID.randomUUID());
        occ.setUnitId(unitId);
        occ.setResidentId(residentId);
        occ.setStatus(OccupancyStatus.ACTIVE);
        occ.setMoveInDate(LocalDate.of(2026, 10, 1));

        when(occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE))
                .thenReturn(List.of(occ));

        UnitOccupantsResponse response = internalOccupancyService.getUnitOccupants(unitId);
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.occupants()).hasSize(1);
        assertThat(response.occupants().get(0).residentId()).isEqualTo(residentId);
    }

    @Test
    void getUserOccupancy_returnsUserOccupancies() {
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        Occupancy occ = new Occupancy();
        occ.setId(UUID.randomUUID());
        occ.setUnitId(unitId);
        occ.setResidentId(userId);
        occ.setStatus(OccupancyStatus.ACTIVE);
        occ.setMoveInDate(LocalDate.of(2026, 10, 1));

        when(occupancyRepository.findByResidentIdOrderByMoveInDateDesc(userId))
                .thenReturn(List.of(occ));

        UserOccupancyResponse response = internalOccupancyService.getUserOccupancy(userId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.occupancies()).hasSize(1);
        assertThat(response.occupancies().get(0).unitId()).isEqualTo(unitId);
        assertThat(response.occupancies().get(0).relationship()).isEqualTo("TENANT_RESIDENT");
    }
}
