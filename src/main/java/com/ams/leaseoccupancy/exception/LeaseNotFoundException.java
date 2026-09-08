package com.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class LeaseNotFoundException extends BusinessException {

    public LeaseNotFoundException(UUID leaseId) {
        super(HttpStatus.NOT_FOUND, "LEASE_NOT_FOUND", "Lease not found: " + leaseId);
    }
}
