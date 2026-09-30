package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when activating a lease would exceed the target unit's physical UnitType capacity limit (HTTP 422).
 */
public class CapacityLimitExceededException extends BusinessException {

    public CapacityLimitExceededException(String message) {
        super(HttpStatus.CONFLICT, "OCCUPANCY_CONFLICT", message);
    }
}
