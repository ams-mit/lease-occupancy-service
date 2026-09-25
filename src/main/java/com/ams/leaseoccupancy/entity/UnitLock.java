package com.ams.leaseoccupancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a unit-level lock record for serializing concurrent lease activations
 * or occupancy mutations on the same unit.
 */
@Entity
@Table(name = "unit_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitLock {

    @Id
    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt = Instant.now();
}
