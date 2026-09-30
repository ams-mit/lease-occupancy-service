package kln.ams.leaseoccupancy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kln.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import kln.ams.leaseoccupancy.config.JwtKeyConfig;
import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestIdFilter;
import kln.ams.leaseoccupancy.config.TestJwtTokens;
import kln.ams.leaseoccupancy.dto.UnitOccupancyResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupantsResponse;
import kln.ams.leaseoccupancy.dto.UserOccupancyResponse;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.service.InternalOccupancyService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(InternalOccupancyController.class)
@Import({JwtKeyConfig.class, JwtService.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
class InternalOccupancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalOccupancyService internalOccupancyService;

    private static MockHttpServletRequestBuilder asService(MockHttpServletRequestBuilder request, String serviceName) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.serviceToken(serviceName));
    }

    @Test
    void getUnitOccupancy_returnsAuthoritativeOccupancy_whenCalledByAllowedService() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID occId = UUID.randomUUID();
        UnitOccupancyResponse response = new UnitOccupancyResponse(
                unitId, true, leaseId, occId, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30), OccupancyStatus.ACTIVE);
        when(internalOccupancyService.getUnitOccupancy(unitId)).thenReturn(response);

        mockMvc.perform(asService(get("/api/v1/internal/units/{unitId}/occupancy", unitId), "operations-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unitId").value(unitId.toString()))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.leaseId").value(leaseId.toString()));
    }

    @Test
    void getUnitOccupancy_returns401_whenNoToken() throws Exception {
        UUID unitId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/internal/units/{unitId}/occupancy", unitId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getUnitOccupancy_returns403_whenCalledWithUserTokenInsteadOfServiceToken() throws Exception {
        UUID unitId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/internal/units/{unitId}/occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void getUnitOccupants_returnsCurrentOccupants_whenCalledByAllowedService() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();
        UnitOccupantsResponse response = new UnitOccupantsResponse(unitId, List.of(
                new UnitOccupantsResponse.OccupantItem(residentId, OccupancyStatus.ACTIVE, LocalDate.of(2026, 10, 1), null)));
        when(internalOccupancyService.getUnitOccupants(unitId)).thenReturn(response);

        mockMvc.perform(asService(get("/api/v1/internal/units/{unitId}/occupants", unitId), "billing-payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.occupants[0].residentId").value(residentId.toString()));
    }

    @Test
    void getUserOccupancy_returnsUserOccupancies_whenCalledByAllowedService() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UserOccupancyResponse response = new UserOccupancyResponse(userId, List.of(
                new UserOccupancyResponse.OccupancyItem(unitId, UUID.randomUUID(), UUID.randomUUID(), "TENANT_RESIDENT", OccupancyStatus.ACTIVE, LocalDate.of(2026, 10, 1), null)));
        when(internalOccupancyService.getUserOccupancy(userId)).thenReturn(response);

        mockMvc.perform(asService(get("/api/v1/internal/users/{userId}/occupancy", userId), "community-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.occupancies[0].unitId").value(unitId.toString()));
    }
}
