package kln.ams.leaseoccupancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "occupancy_status_history")
@Getter @Setter
public class OccupancyStatusHistory {
    @Id @UuidGenerator private UUID id;
    @Column(name = "occupancy_id", nullable = false) private UUID occupancyId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") private OccupancyStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false) private OccupancyStatus toStatus;
    @Column(name = "changed_by", nullable = false) private String changedBy;
    @Column(name = "reason") private String reason;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;
    @PrePersist void onCreate() { changedAt = Instant.now(); }
}
