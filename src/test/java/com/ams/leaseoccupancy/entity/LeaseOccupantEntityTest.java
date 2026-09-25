package com.ams.leaseoccupancy.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseOccupantEntityTest {

    @Test
    void lease_mapsOccupantsCorrectly() {
        Lease lease = new Lease();
        lease.setUnitId(UUID.randomUUID());
        lease.setTenantId(UUID.randomUUID());
        lease.setStartDate(LocalDate.now());
        lease.setEndDate(LocalDate.now().plusMonths(12));
        lease.setStatus(LeaseStatus.DRAFT);
        lease.setCustomNotes("Pet deposit included");

        UUID residentId = UUID.randomUUID();
        Occupant occupant = new Occupant(residentId, true);

        lease.addOccupant(occupant);

        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.DRAFT);
        assertThat(lease.getCustomNotes()).isEqualTo("Pet deposit included");
        assertThat(lease.getOccupants()).hasSize(1);
        assertThat(lease.getOccupants().get(0).getResidentId()).isEqualTo(residentId);
        assertThat(lease.getOccupants().get(0).isPrimary()).isTrue();
        assertThat(occupant.getLease()).isEqualTo(lease);

        lease.removeOccupant(occupant);
        assertThat(lease.getOccupants()).isEmpty();
        assertThat(occupant.getLease()).isNull();
    }

    @Test
    void occupancy_initializesWithDefaultActiveStatus() {
        Occupancy occupancy = new Occupancy();
        UUID unitId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();
        occupancy.setUnitId(unitId);
        occupancy.setResidentId(residentId);
        occupancy.onCreate();

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getMoveInDate()).isEqualTo(LocalDate.now());
        assertThat(occupancy.getCreatedAt()).isNotNull();
        assertThat(occupancy.getUpdatedAt()).isNotNull();
    }

    @Test
    void leaseStatus_containsAllRequiredStatuses() {
        assertThat(LeaseStatus.values()).containsExactly(
                LeaseStatus.DRAFT,
                LeaseStatus.PENDING_ACTIVATION,
                LeaseStatus.ACTIVE,
                LeaseStatus.TERMINATED,
                LeaseStatus.EXPIRED
        );
    }
}
