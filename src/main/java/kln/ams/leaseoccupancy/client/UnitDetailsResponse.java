package kln.ams.leaseoccupancy.client;

import java.util.UUID;

/**
 * Detailed unit record fetched from property-unit-service (Group 2 sibling).
 * Contains unit status, capacity limit, and the registered owner ID.
 */
public record UnitDetailsResponse(
        UUID unitId,
        String status,
        int capacityLimit,
        UUID ownerId,
        boolean availability) {

    public UnitDetailsResponse(UUID unitId, String status, int capacityLimit, UUID ownerId) {
        this(unitId, status, capacityLimit, ownerId, "AVAILABLE".equals(status));
    }

    public boolean isEligibleForNewOccupancy() {
        return "AVAILABLE".equals(status) && availability;
    }

    public boolean isEligibleForMoveInUnderActiveLease() {
        return "AVAILABLE".equals(status) || "OCCUPIED".equals(status);
    }

    public boolean isUnderMaintenance() {
        return "UNDER_MAINTENANCE".equalsIgnoreCase(status);
    }
}
