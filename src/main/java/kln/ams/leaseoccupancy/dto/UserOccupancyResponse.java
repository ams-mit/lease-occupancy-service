package kln.ams.leaseoccupancy.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;

/**
 * Response payload for LEASE-INT-003: GET /api/v1/internal/users/{userId}/occupancy
 */
public record UserOccupancyResponse(
        UUID userId,
        List<OccupancyItem> occupancies) {

    public record OccupancyItem(
            UUID unitId,
            UUID occupancyId,
            UUID leaseId,
            String relationship,
            OccupancyStatus status,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
