package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Base type for domain errors that map directly to a stable, machine-readable error code. */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    protected BusinessException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
