package com.ams.leaseoccupancy.client;

import java.util.UUID;

/**
 * Physical capacity limit response for a target unit, sourced from property-unit-service.
 */
public record UnitCapacityResponse(UUID unitId, int capacityLimit) {
}
