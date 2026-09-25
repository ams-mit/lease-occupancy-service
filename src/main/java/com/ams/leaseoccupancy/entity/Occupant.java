package com.ams.leaseoccupancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A permitted occupant/resident linked to a contractual {@link Lease}.
 * Maps the lease to resident profiles (synthetic foreign key owned by identity-access-service).
 */
@Entity
@Table(name = "lease_occupants")
@Getter
@Setter
@NoArgsConstructor
public class Occupant {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lease_id", nullable = false)
    private Lease lease;

    @Column(name = "resident_id", nullable = false)
    private UUID residentId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Occupant(UUID residentId, boolean primary) {
        this.residentId = residentId;
        this.primary = primary;
    }
}
