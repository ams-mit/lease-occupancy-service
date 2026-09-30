package kln.ams.leaseoccupancy.client;

import java.util.UUID;
import java.util.Set;

/**
 * Outbound client for property-unit-service — source of truth for unit inventory,
 * physical status, capacity limits, and ownership records.
 */
public interface PropertyUnitServiceClient {

    UnitCapacityResponse getUnitCapacity(UUID unitId);

    UnitDetailsResponse getUnitDetails(UUID unitId);

    Set<UUID> getOwnerIds(UUID unitId);

}
