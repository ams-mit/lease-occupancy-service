package com.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Raised when active occupancy records cannot be found for a given unit (HTTP 404).
 */
public class OccupancyNotFoundException extends BusinessException {

    public OccupancyNotFoundException(UUID unitId) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "No active occupancy found for unit: " + unitId);
    }
}
