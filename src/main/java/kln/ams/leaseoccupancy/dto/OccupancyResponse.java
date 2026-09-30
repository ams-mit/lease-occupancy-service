package kln.ams.leaseoccupancy.dto;

import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OccupancyResponse(UUID id, UUID unitId, UUID residentId, UUID leaseId,
                                OccupancyStatus status, LocalDate startDate, LocalDate endDate, String notes,
                                Instant createdAt, Instant updatedAt) {
    public static OccupancyResponse from(Occupancy occupancy) {
        return new OccupancyResponse(occupancy.getId(), occupancy.getUnitId(), occupancy.getResidentId(),
                occupancy.getLeaseId(), occupancy.getStatus(), occupancy.getMoveInDate(),
                occupancy.getMoveOutDate(), occupancy.getNotes(), occupancy.getCreatedAt(), occupancy.getUpdatedAt());
    }
}
