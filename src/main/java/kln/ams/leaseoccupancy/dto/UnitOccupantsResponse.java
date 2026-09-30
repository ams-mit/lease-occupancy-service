package kln.ams.leaseoccupancy.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;

/**
 * Response payload for LEASE-INT-002: GET /api/v1/internal/units/{unitId}/occupants
 */
public record UnitOccupantsResponse(
        UUID unitId,
        List<OccupantItem> occupants) {

    public record OccupantItem(
            UUID residentId,
            OccupancyStatus status,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
