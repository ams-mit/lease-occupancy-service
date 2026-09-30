package kln.ams.leaseoccupancy.dto;

import java.time.LocalDate;
import java.util.UUID;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;

/**
 * Response payload for LEASE-INT-001: GET /api/v1/internal/units/{unitId}/occupancy
 */
public record UnitOccupancyResponse(
        UUID unitId,
        boolean active,
        UUID leaseId,
        UUID occupancyId,
        LocalDate startDate,
        LocalDate endDate,
        OccupancyStatus status) {
}
