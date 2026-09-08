package com.ams.leaseoccupancy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import com.ams.leaseoccupancy.config.JwtKeyConfig;
import com.ams.leaseoccupancy.config.JwtService;
import com.ams.leaseoccupancy.config.RequestIdFilter;
import com.ams.leaseoccupancy.config.TestJwtTokens;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.exception.LeaseConflictException;
import com.ams.leaseoccupancy.exception.LeaseNotFoundException;
import com.ams.leaseoccupancy.service.LeaseService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(LeaseController.class)
@Import({JwtKeyConfig.class, JwtService.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
class LeaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaseService leaseService;

    private static MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken("mgr-1", "MANAGER"));
    }

    @Test
    void createLease_returns201WithEnvelope() throws Exception {
        Lease saved = sampleLease(LeaseStatus.PENDING);
        when(leaseService.createLease(any())).thenReturn(saved);

        String body = """
                {"unitId":"%s","tenantId":"%s","startDate":"%s","endDate":"%s"}
                """.formatted(saved.getUnitId(), saved.getTenantId(), saved.getStartDate(), saved.getEndDate());

        mockMvc.perform(asManager(post("/api/v1/leases")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void createLease_returns401_whenNoToken() throws Exception {
        String body = """
                {"unitId":"%s","tenantId":"%s","startDate":"2027-01-01","endDate":"2027-06-01"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/leases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void createLease_returns403_whenTokenLacksManagerRole() throws Exception {
        String body = """
                {"unitId":"%s","tenantId":"%s","startDate":"2027-01-01","endDate":"2027-06-01"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken("res-1", "RESIDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void createLease_returns401_whenTokenExpired() throws Exception {
        String body = """
                {"unitId":"%s","tenantId":"%s","startDate":"2027-01-01","endDate":"2027-06-01"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.expiredUserToken("mgr-1", "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void createLease_returns400_whenUnitIdMissing() throws Exception {
        String body = """
                {"tenantId":"%s","startDate":"2027-01-01","endDate":"2027-06-01"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(asManager(post("/api/v1/leases")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createLease_returns409_whenServiceReportsConflict() throws Exception {
        when(leaseService.createLease(any())).thenThrow(new LeaseConflictException("overlap"));

        String body = """
                {"unitId":"%s","tenantId":"%s","startDate":"2027-01-01","endDate":"2027-06-01"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(asManager(post("/api/v1/leases")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OCCUPANCY_CONFLICT"));
    }

    @Test
    void listLeases_returnsPaginatedEnvelope() throws Exception {
        Lease lease = sampleLease(LeaseStatus.ACTIVE);
        when(leaseService.listLeases(eq(LeaseStatus.ACTIVE), eq(null), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(lease), PageRequest.of(0, 20), 1));

        mockMvc.perform(asManager(get("/api/v1/leases")).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void updateStatus_returns404_whenLeaseMissing() throws Exception {
        UUID leaseId = UUID.randomUUID();
        when(leaseService.updateStatus(eq(leaseId), any())).thenThrow(new LeaseNotFoundException(leaseId));

        mockMvc.perform(asManager(patch("/api/v1/leases/{id}/status", leaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LEASE_NOT_FOUND"));
    }

    private Lease sampleLease(LeaseStatus status) {
        Lease lease = new Lease();
        lease.setId(UUID.randomUUID());
        lease.setUnitId(UUID.randomUUID());
        lease.setTenantId(UUID.randomUUID());
        lease.setStartDate(LocalDate.now().plusDays(1));
        lease.setEndDate(LocalDate.now().plusMonths(6));
        lease.setStatus(status);
        lease.setCreatedAt(java.time.Instant.now());
        lease.setUpdatedAt(java.time.Instant.now());
        return lease;
    }
}
