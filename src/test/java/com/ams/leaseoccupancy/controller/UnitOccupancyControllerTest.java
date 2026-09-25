package com.ams.leaseoccupancy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import com.ams.leaseoccupancy.config.JwtKeyConfig;
import com.ams.leaseoccupancy.config.JwtService;
import com.ams.leaseoccupancy.config.RequestIdFilter;
import com.ams.leaseoccupancy.config.TestJwtTokens;
import com.ams.leaseoccupancy.dto.ActiveOccupancyResponse;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.exception.OccupancyNotFoundException;
import com.ams.leaseoccupancy.exception.UnitNotFoundException;
import com.ams.leaseoccupancy.service.LeaseService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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

    @Test
    void getActiveOccupancy_returns200_whenValidJwtAndOccupancyExists() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID occupantId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusMonths(1);
        LocalDate end = LocalDate.now().plusMonths(11);

        ActiveOccupancyResponse response = new ActiveOccupancyResponse(
                unitId, leaseId, occupantId, tenantId, ownerId, start, end, LeaseStatus.ACTIVE, List.of(occupantId));

        when(leaseService.getActiveOccupancy(unitId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.serviceToken("billing-payment-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unitId").value(unitId.toString()))
                .andExpect(jsonPath("$.data.leaseId").value(leaseId.toString()))
                .andExpect(jsonPath("$.data.occupantId").value(occupantId.toString()))
                .andExpect(jsonPath("$.data.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getActiveOccupancy_returns404_whenUnitNotFound() throws Exception {
        UUID invalidUnitId = UUID.randomUUID();
        when(leaseService.getActiveOccupancy(invalidUnitId)).thenThrow(new UnitNotFoundException(invalidUnitId));

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", invalidUnitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.serviceToken("billing-payment-service")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getActiveOccupancy_returns404_whenNoActiveOccupancy() throws Exception {
        UUID unitId = UUID.randomUUID();
        when(leaseService.getActiveOccupancy(unitId)).thenThrow(new OccupancyNotFoundException(unitId));

        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", unitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.serviceToken("billing-payment-service")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getActiveOccupancy_returns401_whenNoJwt() throws Exception {
        mockMvc.perform(get("/api/v1/units/{unitId}/active-occupancy", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
