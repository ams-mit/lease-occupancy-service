package kln.ams.leaseoccupancy.dto;

import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LeaseResponse(
        UUID id,
        UUID unitId,
        LocalDate startDate,
        LocalDate endDate,
        LeaseStatus status,
        String notes,
        List<UUID> occupants,
        Instant createdAt,
        Instant updatedAt) {

    public static LeaseResponse from(Lease lease) {
        return new LeaseResponse(
                lease.getId(),
                lease.getUnitId(),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getStatus(),
                lease.getCustomNotes(),
                lease.getOccupants().stream().map(o -> o.getResidentId()).toList(),
                lease.getCreatedAt(),
                lease.getUpdatedAt());
    }
}
