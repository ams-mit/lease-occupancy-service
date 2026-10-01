package kln.ams.leaseoccupancy.client;

import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
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
            // Read as plain maps: the RestClient's Jackson 3 converter cannot build Jackson 2 JsonNode trees.
            Map<String, Object> body = restClient.get().uri("/api/v1/internal/residents/{residentId}/validate", residentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.mintServiceToken())
                    .header("X-Request-ID", RequestContext.getRequestId())
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            Object data = body == null ? null : body.get("data");
            Map<?, ?> fields = data instanceof Map<?, ?> map ? map : Map.of();
            boolean positive = Boolean.TRUE.equals(data)
                    || Boolean.TRUE.equals(fields.get("valid"))
                    || Boolean.TRUE.equals(fields.get("exists"));
            boolean sameId = fields.get("residentId") == null
                    || residentId.toString().equals(String.valueOf(fields.get("residentId")));
            if (body == null || !Boolean.TRUE.equals(body.get("success")) || !positive || !sameId
                    || Boolean.FALSE.equals(fields.get("active"))) {
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
