package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Raised when a lease's dates overlap an existing active lease on the same unit. */
public class LeaseConflictException extends BusinessException {

    public LeaseConflictException(String message) {
        super(HttpStatus.CONFLICT, "LEASE_DATE_CONFLICT", message);
    }
}
