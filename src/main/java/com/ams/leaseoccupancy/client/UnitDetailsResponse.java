package com.ams.leaseoccupancy.client;

import java.util.UUID;

/**
 * Detailed unit record fetched from property-unit-service (Group 2 sibling).
 * Contains unit status, capacity limit, and the registered owner ID.
 */
public record UnitDetailsResponse(
        UUID unitId,
        String status,
        int capacityLimit,
        UUID ownerId) {

    public boolean isUnderMaintenance() {
        return "UNDER_MAINTENANCE".equalsIgnoreCase(status);
    }
}
