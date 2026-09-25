package com.ams.leaseoccupancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Physical occupancy record: tracks physical arrival and residency of an individual in a unit.
 * unitId and residentId are synthetic foreign keys owned by property-unit-service and identity-access-service.
 */
@Entity
@Table(name = "occupancies")
@Getter
@Setter
@NoArgsConstructor
public class Occupancy {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "resident_id", nullable = false)
    private UUID residentId;

    @Column(name = "lease_id")
    private UUID leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccupancyStatus status = OccupancyStatus.ACTIVE;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate = LocalDate.now();

    @Column(name = "move_out_date")
    private LocalDate moveOutDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (moveInDate == null) {
            moveInDate = LocalDate.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
