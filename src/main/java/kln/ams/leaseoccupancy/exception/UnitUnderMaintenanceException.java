package kln.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Raised when attempting to activate a lease on a unit that is currently under maintenance.
 */
public class UnitUnderMaintenanceException extends BusinessException {

    public UnitUnderMaintenanceException(UUID unitId) {
        super(HttpStatus.CONFLICT, "OCCUPANCY_CONFLICT",
                "Unit " + unitId + " is under maintenance and cannot accept lease activation");
    }
}
