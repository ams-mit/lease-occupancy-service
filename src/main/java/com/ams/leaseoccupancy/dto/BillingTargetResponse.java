package com.ams.leaseoccupancy.dto;

import com.ams.leaseoccupancy.entity.Lease;
import java.util.UUID;

/** One entry of GET /api/v1/internal/occupancies/active-billing (consumed by billing-payment-service). */
public record BillingTargetResponse(UUID unitId, UUID tenantId) {

    public static BillingTargetResponse from(Lease lease) {
        return new BillingTargetResponse(lease.getUnitId(), lease.getTenantId());
    }
}
