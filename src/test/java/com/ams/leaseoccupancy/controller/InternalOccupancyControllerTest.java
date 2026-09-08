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
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.service.InternalOccupancyService;
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
    void validate_returnsActiveTrue_whenTenantActiveInUnit() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(internalOccupancyService.isTenantActiveInUnit(tenantId, unitId)).thenReturn(true);

        mockMvc.perform(asService(get("/api/v1/internal/occupancies/validate"), "operations-service")
                        .param("tenantId", tenantId.toString())
                        .param("unitId", unitId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.data.unitId").value(unitId.toString()));
    }

    @Test
    void validate_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/internal/occupancies/validate")
                        .param("tenantId", UUID.randomUUID().toString())
                        .param("unitId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void validate_returns403_whenCalledByDisallowedService() throws Exception {
        mockMvc.perform(asService(get("/api/v1/internal/occupancies/validate"), "community-service")
                        .param("tenantId", UUID.randomUUID().toString())
                        .param("unitId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void validate_returns403_whenCalledWithUserTokenInsteadOfServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/internal/occupancies/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.userToken("mgr-1", "MANAGER"))
                        .param("tenantId", UUID.randomUUID().toString())
                        .param("unitId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void validate_returns400_whenUnitIdMissing() throws Exception {
        mockMvc.perform(asService(get("/api/v1/internal/occupancies/validate"), "operations-service")
                        .param("tenantId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validate_returns400_whenTenantIdNotAValidUuid() throws Exception {
        mockMvc.perform(asService(get("/api/v1/internal/occupancies/validate"), "operations-service")
                        .param("tenantId", "not-a-uuid")
                        .param("unitId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void activeBillingTargets_returnsUnitTenantPairs() throws Exception {
        Lease lease = new Lease();
        lease.setUnitId(UUID.randomUUID());
        lease.setTenantId(UUID.randomUUID());
        lease.setStartDate(LocalDate.now());
        lease.setEndDate(LocalDate.now().plusMonths(6));
        lease.setStatus(LeaseStatus.ACTIVE);
        when(internalOccupancyService.getActiveBillingTargets()).thenReturn(List.of(lease));

        mockMvc.perform(asService(get("/api/v1/internal/occupancies/active-billing"), "billing-payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitId").value(lease.getUnitId().toString()))
                .andExpect(jsonPath("$.data[0].tenantId").value(lease.getTenantId().toString()));
    }

    @Test
    void activeBillingTargets_returns403_whenCalledByDisallowedService() throws Exception {
        mockMvc.perform(asService(get("/api/v1/internal/occupancies/active-billing"), "operations-service"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }
}
