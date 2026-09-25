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
 * Calls property-unit-service's internal capacity endpoint through the Gateway,
 * carrying a Service JWT minted by this service (AGENTS.md §8).
 */
@Component
public class PropertyUnitServiceClientImpl implements PropertyUnitServiceClient {

    private static final String SERVICE_NAME = "property-unit-service";

    private final RestClient restClient;
    private final JwtService jwtService;

    public PropertyUnitServiceClientImpl(@Qualifier("gatewayRestClient") RestClient restClient, JwtService jwtService) {
        this.restClient = restClient;
        this.jwtService = jwtService;
    }

    @Override
    public UnitCapacityResponse getUnitCapacity(UUID unitId) {
        try {
            String serviceToken = jwtService.mintServiceToken();
            CapacityEnvelope envelope = restClient.get()
                    .uri("/api/v1/internal/units/{unitId}/capacity", unitId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .retrieve()
                    .body(CapacityEnvelope.class);

            if (envelope == null || envelope.data() == null) {
                throw new DependencyUnavailableException(SERVICE_NAME, null);
            }
            return envelope.data();
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    private record CapacityEnvelope(boolean success, UnitCapacityResponse data) {
    }
}
