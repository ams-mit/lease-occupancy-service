package com.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a downstream service (identity-access-service, property-unit-service, ...)
 * cannot be reached or times out. Per API-STANDARD-v1 §28, this must never be swallowed
 * into a misleading success response — it always surfaces as 503.
 */
public class DependencyUnavailableException extends BusinessException {

    private final String serviceName;

    public DependencyUnavailableException(String serviceName, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                serviceName + " is temporarily unavailable");
        this.serviceName = serviceName;
        initCause(cause);
    }

    public String getServiceName() {
        return serviceName;
    }
}
