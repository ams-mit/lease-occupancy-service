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
            JsonNode data = body == null ? null : body.path("data");
            boolean positive = data != null && (data.isBoolean() && data.asBoolean()
                    || data.path("valid").isBoolean() && data.path("valid").asBoolean()
                    || data.path("exists").isBoolean() && data.path("exists").asBoolean());
            boolean sameId = data != null && (!data.hasNonNull("residentId")
                    || residentId.toString().equals(data.path("residentId").asText()));
            if (body == null || !body.path("success").asBoolean(false) || !positive || !sameId
                    || data.path("active").isBoolean() && !data.path("active").asBoolean()) {
                throw new DependencyUnavailableException("resident-management-service", null);
            }
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResidentNotFoundException(residentId);
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException("resident-management-service", ex);
        }
    }

    public boolean isValidResident(UUID residentId) {
        requireResident(residentId);
        return true;
    }
}
