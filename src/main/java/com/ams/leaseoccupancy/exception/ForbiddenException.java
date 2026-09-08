package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Authenticated but not authorized — wrong role or disallowed calling service (API-STANDARD-v1 §17). */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", message);
    }
}
