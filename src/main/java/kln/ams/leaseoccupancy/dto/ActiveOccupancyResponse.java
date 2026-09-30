package kln.ams.leaseoccupancy.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;

/**
 * Canonical response payload for LEASE-012: GET /api/v1/units/{unitId}/active-occupancy
 */
public record ActiveOccupancyResponse(
        UUID unitId,
        boolean active,
        UUID leaseId,
        List<OccupantSummary> occupants,
        LocalDate startDate,
        LocalDate endDate) {

    public record OccupantSummary(
            UUID residentId,
            OccupancyStatus status) {
    }

    public static ActiveOccupancyResponse inactive(UUID unitId) {
        return new ActiveOccupancyResponse(unitId, false, null, List.of(), null, null);
    }
}
