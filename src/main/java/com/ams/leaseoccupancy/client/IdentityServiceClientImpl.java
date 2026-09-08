package com.ams.leaseoccupancy.client;

import com.ams.leaseoccupancy.exception.DependencyUnavailableException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class IdentityServiceClientImpl implements IdentityServiceClient {

    private static final String SERVICE_NAME = "identity-access-service";

    private final RestClient restClient;

    public IdentityServiceClientImpl(@Qualifier("identityServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public IdentityUserValidation validateUser(UUID userId) {
        try {
            ValidationEnvelope envelope = restClient.get()
                    .uri("/api/v1/internal/users/{userId}/validate", userId)
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
