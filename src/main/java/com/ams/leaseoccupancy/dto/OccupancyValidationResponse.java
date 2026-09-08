package com.ams.leaseoccupancy.dto;

import java.util.UUID;

/** Response for GET /api/v1/internal/occupancies/validate (consumed by operations-service). */
public record OccupancyValidationResponse(UUID tenantId, UUID unitId, boolean active) {
}
