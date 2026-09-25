package com.ams.leaseoccupancy.client;

import java.util.UUID;

/**
 * Outbound client for property-unit-service — source of truth for unit inventory and capacity limits.
 */
public interface PropertyUnitServiceClient {

    UnitCapacityResponse getUnitCapacity(UUID unitId);
}
