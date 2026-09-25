package com.ams.leaseoccupancy.dto;

import java.util.UUID;

/** Response for GET /api/v1/internal/occupancies/validate and GET /api/v1/leases/validate. */
public record OccupancyValidationResponse(
        UUID tenantId,
        UUID unitId,
        boolean active,
        String status) {

    public OccupancyValidationResponse(UUID tenantId, UUID unitId, boolean active) {
        this(tenantId, unitId, active, active ? "ACTIVE" : "INACTIVE");
    }
}
