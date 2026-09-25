package com.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Raised when attempting to activate a lease on a unit that is currently under maintenance.
 */
public class UnitUnderMaintenanceException extends BusinessException {

    public UnitUnderMaintenanceException(UUID unitId) {
        super(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION",
                "Unit " + unitId + " is under maintenance and cannot accept lease activation");
    }
}
