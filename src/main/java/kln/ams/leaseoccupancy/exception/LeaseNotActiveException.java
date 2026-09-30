package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

public class LeaseNotActiveException extends BusinessException {

    public LeaseNotActiveException(String message) {
        super(HttpStatus.CONFLICT, "LEASE_NOT_ACTIVE", message);
    }
}
