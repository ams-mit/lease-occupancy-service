package com.ams.leaseoccupancy.client;

import java.util.UUID;

/** Subset of identity-access-service's user validation response that this service needs. */
public record IdentityUserValidation(UUID userId, boolean exists, boolean active) {

    public boolean isValidTenant() {
        return exists && active;
    }
}
