package com.ams.leaseoccupancy.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A lease contract between a tenant and a unit. unitId and tenantId are synthetic
 * foreign keys owned by property-unit-service and identity-access-service — this
 * entity never has a JPA relationship to another service's tables.
 * Permitted occupants are modeled via {@link Occupant}.
 */
@Entity
@Table(name = "leases")
@Getter
@Setter
@NoArgsConstructor
public class Lease {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeaseStatus status = LeaseStatus.DRAFT;

    @Column(name = "custom_notes")
    private String customNotes;

    @OneToMany(mappedBy = "lease", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Occupant> occupants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addOccupant(Occupant occupant) {
        occupants.add(occupant);
        occupant.setLease(this);
    }

    public void removeOccupant(Occupant occupant) {
        occupants.remove(occupant);
        occupant.setLease(null);
    }
}
