package com.ams.leaseoccupancy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ams.leaseoccupancy.config.JwtService;
import com.ams.leaseoccupancy.exception.DependencyUnavailableException;
import com.ams.leaseoccupancy.exception.UnitNotFoundException;
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

    @Mock
    private JwtService jwtService;

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private PropertyUnitServiceClient propertyUnitServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        propertyUnitServiceClient = new PropertyUnitServiceClientImpl(restClient, jwtService);
    }

    @Test
    void getUnitCapacity_returnsCapacity_whenResponseIs200() {
        UUID unitId = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("mock-service-token");

        String json = """
                {"success":true,"data":{"unitId":"%s","capacityLimit":3}}
                """.formatted(unitId);

        mockServer.expect(requestTo("http://gateway.test/api/v1/internal/units/" + unitId + "/capacity"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-service-token"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        UnitCapacityResponse response = propertyUnitServiceClient.getUnitCapacity(unitId);

        assertThat(response).isNotNull();
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.capacityLimit()).isEqualTo(3);
        mockServer.verify();
    }

    @Test
    void getUnitCapacity_throwsDependencyUnavailable_whenServerErrors() {
        UUID unitId = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("mock-service-token");

        mockServer.expect(requestTo("http://gateway.test/api/v1/internal/units/" + unitId + "/capacity"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> propertyUnitServiceClient.getUnitCapacity(unitId))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void getUnitDetails_returnsDetails_whenResponseIs200() {
        UUID unitId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("mock-service-token");

        String json = """
                {"success":true,"data":{"unitId":"%s","status":"AVAILABLE","capacityLimit":2,"ownerId":"%s"}}
                """.formatted(unitId, ownerId);

        mockServer.expect(requestTo("http://gateway.test/api/v1/internal/units/" + unitId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-service-token"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        UnitDetailsResponse response = propertyUnitServiceClient.getUnitDetails(unitId);

        assertThat(response).isNotNull();
        assertThat(response.unitId()).isEqualTo(unitId);
        assertThat(response.status()).isEqualTo("AVAILABLE");
        assertThat(response.capacityLimit()).isEqualTo(2);
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.isUnderMaintenance()).isFalse();
        mockServer.verify();
    }

    @Test
    void getUnitDetails_throwsUnitNotFound_when404() {
        UUID unitId = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("mock-service-token");

        mockServer.expect(requestTo("http://gateway.test/api/v1/internal/units/" + unitId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> propertyUnitServiceClient.getUnitDetails(unitId))
                .isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void updateUnitStatus_sendsPatchRequest() {
        UUID unitId = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("mock-service-token");

        mockServer.expect(requestTo("http://gateway.test/api/v1/internal/units/" + unitId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-service-token"))
                .andExpect(content().json("{\"status\":\"OCCUPIED\"}"))
                .andRespond(withSuccess());

        propertyUnitServiceClient.updateUnitStatus(unitId, "OCCUPIED");

        mockServer.verify();
    }

    @Test
    void updateUnitStatus_failsLeaseOperationWhenPropertyIsUnavailable() {
        UUID unitId = UUID.randomUUID();
        Mockito.when(jwtService.mintServiceToken()).thenReturn("mock-service-token");
        mockServer.expect(requestTo("http://gateway.test/api/v1/internal/units/" + unitId + "/status"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> propertyUnitServiceClient.updateUnitStatus(unitId, "OCCUPIED"))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
