package com.ams.leaseoccupancy.dto;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseResponse(
        UUID id,
        UUID unitId,
        UUID tenantId,
        LocalDate startDate,
        LocalDate endDate,
        LeaseStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static LeaseResponse from(Lease lease) {
        return new LeaseResponse(
                lease.getId(),
                lease.getUnitId(),
                lease.getTenantId(),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getStatus(),
                lease.getCreatedAt(),
                lease.getUpdatedAt());
    }
}
