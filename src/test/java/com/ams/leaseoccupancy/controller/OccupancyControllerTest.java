package com.ams.leaseoccupancy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import com.ams.leaseoccupancy.config.JwtKeyConfig;
import com.ams.leaseoccupancy.config.JwtService;
import com.ams.leaseoccupancy.config.RequestIdFilter;
import com.ams.leaseoccupancy.config.TestJwtTokens;
import com.ams.leaseoccupancy.entity.Occupancy;
import com.ams.leaseoccupancy.service.OccupancyService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OccupancyController.class)
@Import({JwtKeyConfig.class, JwtService.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
class OccupancyControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OccupancyService service;

    @Test
    void managerCanRegisterPhysicalOccupancy() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        Occupancy saved = new Occupancy();
        saved.setId(UUID.randomUUID());
        saved.setUnitId(unitId);
        saved.setResidentId(residentId);
        saved.setLeaseId(leaseId);
        when(service.register(any())).thenReturn(saved);

        String body = """
                {"unitId":"%s","residentId":"%s","leaseId":"%s","moveInDate":"%s"}
                """.formatted(unitId, residentId, leaseId, LocalDate.now());
        mvc.perform(post("/api/v1/occupancies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken("mgr", "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.residentId").value(residentId.toString()));
    }

    @Test
    void residentCannotRegisterPhysicalOccupancy() throws Exception {
        String body = """
                {"unitId":"%s","residentId":"%s","leaseId":"%s","moveInDate":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
        mvc.perform(post("/api/v1/occupancies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken("resident", "RESIDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void unrelatedResidentCannotReadHistory() throws Exception {
        mvc.perform(get("/api/v1/occupancies/residents/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "RESIDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void residentCanReadOwnHistory() throws Exception {
        UUID residentId = UUID.randomUUID();
        when(service.historyForResident(residentId)).thenReturn(List.of());
        mvc.perform(get("/api/v1/occupancies/residents/{id}", residentId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtTokens.userToken(residentId.toString(), "RESIDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
