package kln.ams.leaseoccupancy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import kln.ams.leaseoccupancy.config.JwtKeyConfig;
import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestIdFilter;
import kln.ams.leaseoccupancy.config.TestJwtTokens;
import kln.ams.leaseoccupancy.dto.ActiveOccupancyResponse;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.exception.OccupancyNotFoundException;
import kln.ams.leaseoccupancy.exception.UnitNotFoundException;
import kln.ams.leaseoccupancy.service.LeaseService;
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

@WebMvcTest(UnitOccupancyController.class)
@Import({JwtKeyConfig.class, JwtService.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
class UnitOccupancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaseService leaseService;

    @MockitoBean
    private PropertyUnitServiceClient propertyUnitServiceClient;

    @Test
    void getActiveOccupancy_returns200_whenValidJwtAndOccupancyExists() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID occupantId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusMonths(1);
        LocalDate end = LocalDate.now().plusMonths(11);

        ActiveOccupancyResponse response = new ActiveOccupancyResponse(
                unitId, true, leaseId, List.of(new ActiveOccupancyResponse.OccupantSummary(occupantId, OccupancyStatus.ACTIVE)), start, end);

        when(leaseService.getActiveOccupancy(unitId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unitId").value(unitId.toString()))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.leaseId").value(leaseId.toString()))
                .andExpect(jsonPath("$.data.occupants[0].residentId").value(occupantId.toString()))
                .andExpect(jsonPath("$.data.occupants[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.startDate").value(start.toString()))
                .andExpect(jsonPath("$.data.endDate").value(end.toString()));
    }

    @Test
    void getActiveOccupancy_returns404_whenUnitNotFound() throws Exception {
        UUID invalidUnitId = UUID.randomUUID();
        when(leaseService.getActiveOccupancy(invalidUnitId)).thenThrow(new UnitNotFoundException(invalidUnitId));

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", invalidUnitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNIT_NOT_FOUND"));
    }

    @Test
    void getActiveOccupancy_returns404_whenNoActiveOccupancy() throws Exception {
        UUID unitId = UUID.randomUUID();
        when(leaseService.getActiveOccupancy(unitId)).thenThrow(new OccupancyNotFoundException(unitId));

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OCCUPANCY_NOT_FOUND"));
    }

    @Test
    void getActiveOccupancy_returns401_whenNoJwt() throws Exception {
        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getActiveOccupancy_failsClosedForResidentUntilRelationshipContractAvailable() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID otherResident = UUID.randomUUID();
        ActiveOccupancyResponse response = new ActiveOccupancyResponse(
                unitId, true, UUID.randomUUID(), List.of(new ActiveOccupancyResponse.OccupantSummary(otherResident, OccupancyStatus.ACTIVE)), LocalDate.now(), LocalDate.now().plusMonths(6));
        when(leaseService.getActiveOccupancy(unitId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "TENANT_RESIDENT")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getActiveOccupancy_rejectsUnapprovedService() throws Exception {
        UUID unitId = UUID.randomUUID();
        ActiveOccupancyResponse response = new ActiveOccupancyResponse(
                unitId, true, UUID.randomUUID(), List.of(), LocalDate.now(), LocalDate.now().plusMonths(6));
        when(leaseService.getActiveOccupancy(unitId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtTokens.serviceToken("community-service")))
                .andExpect(status().isForbidden());
    }
}
