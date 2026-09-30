package kln.ams.leaseoccupancy.client;

import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.exception.UnitNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls the property provider's documented internal routes through the Gateway. */
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
        UnitValidation unit = validate(unitId);
        return new UnitCapacityResponse(unit.unitId(), unit.capacity());
    }

    @Override
    public UnitDetailsResponse getUnitDetails(UUID unitId) {
        UnitValidation unit = validate(unitId);
        try {
            OwnershipEnvelope ownership = restClient.get()
                    .uri("/api/v1/internal/units/{unitId}/ownership", unitId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.mintServiceToken())
                    .header("X-Request-ID", RequestContext.getRequestId())
                    .retrieve().body(OwnershipEnvelope.class);
            if (ownership == null || !ownership.success() || ownership.data() == null
                    || !unitId.equals(ownership.data().unitId()) || ownership.data().owners() == null) {
                throw new DependencyUnavailableException(SERVICE_NAME, null);
            }
            UUID ownerId = ownership.data().owners().size() == 1
                    ? ownership.data().owners().get(0).ownerId() : null;
            return new UnitDetailsResponse(unit.unitId(), unit.status(), unit.capacity(), ownerId);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new UnitNotFoundException(unitId);
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    private UnitValidation validate(UUID unitId) {
        try {
            ValidationEnvelope envelope = restClient.get()
                    .uri("/api/v1/internal/units/{unitId}/validate", unitId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.mintServiceToken())
                    .header("X-Request-ID", RequestContext.getRequestId())
                    .retrieve().body(ValidationEnvelope.class);
            if (envelope == null || !envelope.success() || envelope.data() == null
                    || !unitId.equals(envelope.data().unitId()) || !envelope.data().exists()
                    || envelope.data().status() == null || envelope.data().capacity() == null
                    || envelope.data().capacity() <= 0) {
                throw new DependencyUnavailableException(SERVICE_NAME, null);
            }
            return envelope.data();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new UnitNotFoundException(unitId);
        } catch (RestClientException ex) {
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }

    private record ValidationEnvelope(boolean success, UnitValidation data) {}
    private record UnitValidation(UUID unitId, boolean exists, String status, Integer capacity) {}
    private record OwnershipEnvelope(boolean success, UnitOwnership data) {}
    private record UnitOwnership(UUID unitId, List<Owner> owners) {}
    private record Owner(UUID ownerId) {}
}
