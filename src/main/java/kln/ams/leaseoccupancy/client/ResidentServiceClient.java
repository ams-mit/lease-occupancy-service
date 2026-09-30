package kln.ams.leaseoccupancy.client;

import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Validates resident profile references through RES-INT-001 via the Gateway. */
@Component
public class ResidentServiceClient {
    private final RestClient restClient;
    private final JwtService jwtService;

    public ResidentServiceClient(@Qualifier("gatewayRestClient") RestClient restClient, JwtService jwtService) {
        this.restClient = restClient;
        this.jwtService = jwtService;
    }

    public void requireResident(UUID residentId) {
        try {
            JsonNode body = restClient.get().uri("/api/v1/internal/residents/{residentId}/validate", residentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.mintServiceToken())
                    .header("X-Request-ID", RequestContext.getRequestId())
                    .retrieve().body(JsonNode.class);
            if (body == null || !body.path("success").asBoolean(false) || body.path("data").isMissingNode()
                    || body.path("data").isNull() || body.path("data").isBoolean() && !body.path("data").asBoolean()
                    || body.path("data").path("valid").isBoolean() && !body.path("data").path("valid").asBoolean()
                    || body.path("data").path("exists").isBoolean() && !body.path("data").path("exists").asBoolean()) {
                throw new DependencyUnavailableException("resident-management-service", null);
            }
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResidentNotFoundException(residentId);
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException("resident-management-service", ex);
        }
    }
}
