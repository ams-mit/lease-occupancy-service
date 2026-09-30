package kln.ams.leaseoccupancy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.config.JwtAuthenticationFilter;
import kln.ams.leaseoccupancy.config.JwtKeyConfig;
import kln.ams.leaseoccupancy.config.JwtService;
import kln.ams.leaseoccupancy.config.RequestIdFilter;
import kln.ams.leaseoccupancy.config.TestJwtTokens;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.exception.LeaseConflictException;
import kln.ams.leaseoccupancy.exception.LeaseNotFoundException;
import kln.ams.leaseoccupancy.service.LeaseService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

    @MockitoBean
    private PropertyUnitServiceClient propertyUnitServiceClient;

    private static MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER"));
    }

    @Test
    void createLease_returns201WithEnvelope() throws Exception {
        Lease saved = sampleLease(LeaseStatus.DRAFT);
        when(leaseService.createLease(any())).thenReturn(saved);

        String body = """
                {"unitId":"%s","startDate":"%s","endDate":"%s","occupants":[{"residentId":"%s"}]}
                """.formatted(saved.getUnitId(), saved.getStartDate(), saved.getEndDate(), saved.getTenantId());

        mockMvc.perform(asManager(post("/api/v1/leases")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void createLease_returns401_whenNoToken() throws Exception {
        String body = """
                {"unitId":"%s","startDate":"2027-01-01","endDate":"2027-06-01","occupants":[{"residentId":"%s"}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/leases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void createLease_returns403_whenTokenLacksManagerRole() throws Exception {
        String body = """
                {"unitId":"%s","startDate":"2027-01-01","endDate":"2027-06-01","occupants":[{"residentId":"%s"}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken(UUID.randomUUID().toString(), "TENANT_RESIDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void createLease_returns401_whenTokenExpired() throws Exception {
        String body = """
                {"unitId":"%s","startDate":"2027-01-01","endDate":"2027-06-01","occupants":[{"residentId":"%s"}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.expiredUserToken(UUID.randomUUID().toString(), "APARTMENT_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void createLease_returns400_whenUnitIdMissing() throws Exception {
        String body = """
                {"startDate":"2027-01-01","endDate":"2027-06-01","occupants":[{"residentId":"%s"}]}
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
                {"unitId":"%s","startDate":"2027-01-01","endDate":"2027-06-01","occupants":[{"residentId":"%s"}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(asManager(post("/api/v1/leases")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEASE_DATE_CONFLICT"));
    }

    @Test
    void listLeases_returnsPaginatedEnvelope() throws Exception {
        Lease lease = sampleLease(LeaseStatus.ACTIVE);
        when(leaseService.listLeases(any(), any(), any(), eq(LeaseStatus.ACTIVE), any(), any(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(lease), PageRequest.of(0, 20), 1));

        mockMvc.perform(asManager(get("/api/v1/leases")).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void leaseReadsAndHistoryUseCanonicalRoutes() throws Exception {
        Lease lease = sampleLease(LeaseStatus.ACTIVE);
        when(leaseService.getLease(lease.getId())).thenReturn(lease);
        when(leaseService.statusHistory(lease.getId())).thenReturn(List.of());
        when(leaseService.historyForUnit(lease.getUnitId())).thenReturn(List.of(lease));

        mockMvc.perform(asManager(get("/api/v1/leases/{id}", lease.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(lease.getId().toString()));
        mockMvc.perform(asManager(get("/api/v1/leases/{id}/history", lease.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.leaseId").value(lease.getId().toString()));
        mockMvc.perform(asManager(get("/api/v1/leases/units/{id}", lease.getUnitId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(lease.getId().toString()));
    }

    @Test
    void ownerFilterAndInvalidPagination() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(leaseService.listLeases(any(), any(), eq(ownerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(asManager(get("/api/v1/leases")).param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
        mockMvc.perform(asManager(get("/api/v1/leases")).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateStatusReturnsUpdatedLease() throws Exception {
        Lease lease = sampleLease(LeaseStatus.ACTIVE);
        when(leaseService.updateStatus(eq(lease.getId()), any())).thenReturn(lease);
        mockMvc.perform(asManager(patch("/api/v1/leases/{id}/status", lease.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
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
