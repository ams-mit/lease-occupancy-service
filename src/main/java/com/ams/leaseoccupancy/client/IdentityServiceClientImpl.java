package com.ams.leaseoccupancy.client;

import com.ams.leaseoccupancy.config.JwtService;
import com.ams.leaseoccupancy.exception.DependencyUnavailableException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls identity-access-service's internal validation endpoint through the Gateway,
 * carrying a Service JWT we mint ourselves — per the JWT standard, we never call a
 * sibling service directly or unauthenticated (AGENTS.md §8).
 */
@Component
public class IdentityServiceClientImpl implements IdentityServiceClient {

    private static final String SERVICE_NAME = "identity-access-service";

    private final RestClient restClient;
    private final JwtService jwtService;

    public IdentityServiceClientImpl(@Qualifier("gatewayRestClient") RestClient restClient, JwtService jwtService) {
        this.restClient = restClient;
        this.jwtService = jwtService;
    }

    @Override
    public IdentityUserValidation validateUser(UUID userId) {
        try {
            String serviceToken = jwtService.mintServiceToken();
            ValidationEnvelope envelope = restClient.get()
                    .uri("/api/v1/internal/users/{userId}/validate", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .retrieve()
                    .body(ValidationEnvelope.class);

            if (envelope == null || envelope.data() == null) {
                throw new DependencyUnavailableException(SERVICE_NAME, null);
            }
            return envelope.data();
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    /** Mirrors the shared success envelope's shape for this one endpoint (API-STANDARD-v1 §27). */
    private record ValidationEnvelope(boolean success, IdentityUserValidation data) {
    }
}
