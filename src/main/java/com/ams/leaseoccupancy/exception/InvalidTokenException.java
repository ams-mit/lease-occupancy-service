package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Missing/malformed/expired/invalid-signature JWT — always a 401 (API-STANDARD-v1 §17). */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
