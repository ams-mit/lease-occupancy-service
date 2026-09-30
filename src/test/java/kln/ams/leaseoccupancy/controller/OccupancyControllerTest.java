package kln.ams.leaseoccupancy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import kln.ams.leaseoccupancy.config.JwtKeyConfig;
import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestIdFilter;
import kln.ams.leaseoccupancy.config.TestJwtTokens;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.service.OccupancyService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
    @MockitoBean PropertyUnitServiceClient propertyUnitServiceClient;

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
                {"unitId":"%s","residentId":"%s","leaseId":"%s","startDate":"%s"}
                """.formatted(unitId, residentId, leaseId, LocalDate.now());
        mvc.perform(post("/api/v1/occupancies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.residentId").value(residentId.toString()));
    }

    @Test
    void residentCannotRegisterPhysicalOccupancy() throws Exception {
        String body = """
                {"unitId":"%s","residentId":"%s","leaseId":"%s","startDate":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
        mvc.perform(post("/api/v1/occupancies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "TENANT_RESIDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void residentHistoryFailsClosedWithoutRelationshipProviderContract() throws Exception {
        mvc.perform(get("/api/v1/occupancies/residents/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "TENANT_RESIDENT")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void residentSelfHistoryNeedsRelationshipProviderContract() throws Exception {
        UUID residentId = UUID.randomUUID();
        mvc.perform(get("/api/v1/occupancies/residents/{id}", residentId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtTokens.userToken(residentId.toString(), "TENANT_RESIDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void managerCanListUnitOccupantsAndRecordMoveOut() throws Exception {
        UUID unitId = UUID.randomUUID();
        Occupancy occupancy = new Occupancy();
        occupancy.setId(UUID.randomUUID());
        occupancy.setUnitId(unitId);
        occupancy.setResidentId(UUID.randomUUID());
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        when(service.listOccupantsForUnit(org.mockito.ArgumentMatchers.eq(unitId), any(),
                org.mockito.ArgumentMatchers.eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(occupancy), PageRequest.of(0, 20), 1));
        when(service.updateStatus(org.mockito.ArgumentMatchers.eq(occupancy.getId()), any())).thenAnswer(inv -> {
            occupancy.setStatus(OccupancyStatus.ENDED);
            return occupancy;
        });
        String token = "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER");
        mvc.perform(get("/api/v1/occupancies/units/{id}", unitId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalElements").value(1));
        mvc.perform(patch("/api/v1/occupancies/{id}/status", occupancy.getId())
                        .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENDED\",\"effectiveDate\":\"2026-10-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));
    }
}
