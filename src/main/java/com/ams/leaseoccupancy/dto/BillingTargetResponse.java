package com.ams.leaseoccupancy.dto;

import com.ams.leaseoccupancy.entity.Lease;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of GET /api/v1/internal/occupancies/active-billing (consumed by billing-payment-service).
 * Supplies active unit and tenant pairs along with lease term details for monthly recurring invoicing.
 */
public record BillingTargetResponse(
        UUID unitId,
        UUID tenantId,
        UUID leaseId,
        LocalDate startDate,
        LocalDate endDate) {

    public BillingTargetResponse(UUID unitId, UUID tenantId) {
        this(unitId, tenantId, null, null, null);
    }

    public static BillingTargetResponse from(Lease lease) {
        return new BillingTargetResponse(
                lease.getUnitId(),
                lease.getTenantId(),
                lease.getId(),
                lease.getStartDate(),
                lease.getEndDate());
    }
}
