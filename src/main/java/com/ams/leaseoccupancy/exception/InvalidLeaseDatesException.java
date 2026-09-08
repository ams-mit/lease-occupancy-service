package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

public class InvalidLeaseDatesException extends BusinessException {

    public InvalidLeaseDatesException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
