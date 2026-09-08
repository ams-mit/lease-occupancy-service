package com.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class InvalidTenantException extends BusinessException {

    public InvalidTenantException(UUID tenantId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                "Tenant " + tenantId + " is not a valid, active user");
    }
}
