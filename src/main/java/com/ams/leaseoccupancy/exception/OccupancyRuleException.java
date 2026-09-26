package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

public class OccupancyRuleException extends BusinessException {
    public OccupancyRuleException(String message) {
        super(HttpStatus.CONFLICT, "OCCUPANCY_CONFLICT", message);
    }
}
