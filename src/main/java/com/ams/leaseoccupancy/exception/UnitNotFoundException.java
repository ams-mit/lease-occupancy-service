package com.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Raised when a requested unit cannot be found in property-unit-service (HTTP 404).
 */
public class UnitNotFoundException extends BusinessException {

    public UnitNotFoundException(UUID unitId) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Unit not found: " + unitId);
    }
}
