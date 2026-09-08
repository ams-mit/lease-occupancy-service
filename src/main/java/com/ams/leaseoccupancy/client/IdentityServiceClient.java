package com.ams.leaseoccupancy.client;

import java.util.UUID;

/** Outbound client for identity-access-service — the only source of truth for user validity. */
public interface IdentityServiceClient {

    IdentityUserValidation validateUser(UUID userId);
}
