package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Raised when a requested lease status change is not a valid transition from its current state. */
public class InvalidLeaseStatusTransitionException extends BusinessException {

    public InvalidLeaseStatusTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_LEASE_STATUS", message);
    }
}
