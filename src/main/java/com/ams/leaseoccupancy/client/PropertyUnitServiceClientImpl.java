package com.ams.leaseoccupancy.client;

import com.ams.leaseoccupancy.config.JwtService;
import com.ams.leaseoccupancy.exception.DependencyUnavailableException;
import com.ams.leaseoccupancy.exception.UnitNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls property-unit-service's internal endpoints through the Gateway,
 * carrying a Service JWT minted by this service (AGENTS.md §8).
 */
@Component
public class PropertyUnitServiceClientImpl implements PropertyUnitServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PropertyUnitServiceClientImpl.class);
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
        } catch (HttpClientErrorException.NotFound ex) {
            throw new UnitNotFoundException(unitId);
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    @Override
    public UnitDetailsResponse getUnitDetails(UUID unitId) {
        try {
            String serviceToken = jwtService.mintServiceToken();
            UnitDetailsEnvelope envelope = restClient.get()
                    .uri("/api/v1/internal/units/{unitId}", unitId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .retrieve()
                    .body(UnitDetailsEnvelope.class);

            if (envelope == null || envelope.data() == null) {
                throw new DependencyUnavailableException(SERVICE_NAME, null);
            }
            return envelope.data();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new UnitNotFoundException(unitId);
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    @Override
    public void updateUnitStatus(UUID unitId, String status) {
        try {
            String serviceToken = jwtService.mintServiceToken();
            restClient.patch()
                    .uri("/api/v1/internal/units/{unitId}/status", unitId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully updated unit {} status to {} in property-unit-service", unitId, status);
        } catch (RestClientException ex) {
            log.warn("Failed to transition unit {} status to {} in property-unit-service: {}", unitId, status, ex.getMessage());
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    private record CapacityEnvelope(boolean success, UnitCapacityResponse data) {
    }

    private record UnitDetailsEnvelope(boolean success, UnitDetailsResponse data) {
    }
}
