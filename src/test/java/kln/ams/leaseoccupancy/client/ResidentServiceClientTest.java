package kln.ams.leaseoccupancy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ResidentServiceClientTest {
    @Mock JwtService jwtService;
    private MockRestServiceServer server;
    private ResidentServiceClient client;

    @BeforeEach void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ResidentServiceClient(builder.build(), jwtService);
        Mockito.when(jwtService.mintServiceToken()).thenReturn("service-token");
    }

    private void respond(UUID id, String json) {
        server.expect(requestTo("http://gateway.test/api/v1/internal/residents/" + id + "/validate"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test void validResidentIsAccepted() {
        UUID id = UUID.randomUUID();
        respond(id, "{\"success\":true,\"data\":{\"residentId\":\"" + id + "\",\"exists\":true,\"active\":true}}");
        assertThat(client.isValidResident(id)).isTrue();
        server.verify();
    }

    @Test void booleanDataIsAccepted() {
        UUID id = UUID.randomUUID();
        respond(id, "{\"success\":true,\"data\":true}");
        assertThat(client.isValidResident(id)).isTrue();
    }

    @Test void inactiveOrMismatchedResidentIsRejected() {
        UUID id = UUID.randomUUID();
        respond(id, "{\"success\":true,\"data\":{\"residentId\":\"" + id + "\",\"exists\":true,\"active\":false}}");
        assertThatThrownBy(() -> client.requireResident(id)).isInstanceOf(DependencyUnavailableException.class);

        server.reset();
        respond(id, "{\"success\":true,\"data\":{\"residentId\":\"" + UUID.randomUUID() + "\",\"exists\":true}}");
        assertThatThrownBy(() -> client.requireResident(id)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test void missingResidentIsNotFound() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://gateway.test/api/v1/internal/residents/" + id + "/validate"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.requireResident(id)).isInstanceOf(ResidentNotFoundException.class);
    }
}
