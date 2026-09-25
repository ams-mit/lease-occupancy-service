package com.ams.leaseoccupancy.dto;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.entity.Occupant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/v1/units/{unitId}/active-occupancy.
 * Delivers active occupant ID, owner ID, lease dates/terms, and tenant details.
 */
public record ActiveOccupancyResponse(
        UUID unitId,
        UUID leaseId,
        UUID occupantId,
        UUID tenantId,
        UUID ownerId,
        LocalDate startDate,
        LocalDate endDate,
        LeaseStatus status,
        List<UUID> occupantIds) {

    public static ActiveOccupancyResponse from(Lease lease, UUID ownerId) {
        UUID primaryOccupantId = lease.getOccupants().stream()
                .filter(Occupant::isPrimary)
                .map(Occupant::getResidentId)
                .findFirst()
                .orElse(lease.getTenantId());

        List<UUID> allOccupantIds = lease.getOccupants().stream()
                .map(Occupant::getResidentId)
                .toList();

        return new ActiveOccupancyResponse(
                lease.getUnitId(),
                lease.getId(),
                primaryOccupantId,
                lease.getTenantId(),
                ownerId,
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getStatus(),
                allOccupantIds.isEmpty() ? List.of(primaryOccupantId) : allOccupantIds
        );
    }
}
