package kln.ams.leaseoccupancy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.exception.UnitNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PropertyUnitServiceClientTest {
    @Mock JwtService jwtService;
    private MockRestServiceServer server;
    private PropertyUnitServiceClient client;

    @BeforeEach void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PropertyUnitServiceClientImpl(builder.build(), jwtService);
    }

    @Test void capacityComesFromCanonicalValidation() {
        UUID id = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
        expectValidation(id, 3);
        assertThat(client.getUnitCapacity(id)).isEqualTo(new UnitCapacityResponse(id, 3));
        server.verify();
    }

    @Test void unitDetailsJoinValidationAndOwnership() {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
        expectValidation(id, 2);
        server.expect(requestTo("http://gateway.test/api/v1/internal/units/" + id + "/ownership"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"unitId\":\"" + id
                        + "\",\"owners\":[{\"ownerId\":\"" + owner + "\"}]}}", MediaType.APPLICATION_JSON));
        UnitDetailsResponse details = client.getUnitDetails(id);
        assertThat(details).isEqualTo(new UnitDetailsResponse(id, "AVAILABLE", 2, owner));
        server.verify();
    }

    @Test void sharedOwnershipHasNoSoleOwner() {
        UUID id = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
        expectValidation(id, 2);
        server.expect(requestTo("http://gateway.test/api/v1/internal/units/" + id + "/ownership"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"unitId\":\"" + id
                        + "\",\"owners\":[{\"ownerId\":\"" + UUID.randomUUID()
                        + "\"},{\"ownerId\":\"" + UUID.randomUUID() + "\"}]}}", MediaType.APPLICATION_JSON));
        assertThat(client.getUnitDetails(id).ownerId()).isNull();
        server.verify();
    }

    @Test void ownerIdsIncludeEverySharedOwner() {
        UUID id = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
        server.expect(requestTo("http://gateway.test/api/v1/internal/units/" + id + "/ownership"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"unitId\":\"" + id
                        + "\",\"owners\":[{\"ownerId\":\"" + first + "\"},{\"ownerId\":\"" + second + "\"}]}}", MediaType.APPLICATION_JSON));
        assertThat(client.getOwnerIds(id)).containsExactlyInAnyOrder(first, second);
        server.verify();
    }

    @Test void missingUnitIsNotFound() {
        UUID id = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
        server.expect(requestTo("http://gateway.test/api/v1/internal/units/" + id + "/validate"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.getUnitDetails(id)).isInstanceOf(UnitNotFoundException.class);
    }

    @Test void failedProviderIsUnavailable() {
        UUID id = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
        server.expect(requestTo("http://gateway.test/api/v1/internal/units/" + id + "/validate"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> client.getUnitCapacity(id)).isInstanceOf(DependencyUnavailableException.class);
    }

    private void expectValidation(UUID id, int capacity) {
        server.expect(requestTo("http://gateway.test/api/v1/internal/units/" + id + "/validate"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"unitId\":\"" + id
                        + "\",\"exists\":true,\"status\":\"AVAILABLE\",\"availability\":true,\"capacity\":" + capacity + "}}",
                        MediaType.APPLICATION_JSON));
    }
}
