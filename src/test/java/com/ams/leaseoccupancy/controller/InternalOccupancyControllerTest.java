package com.ams.leaseoccupancy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.service.InternalOccupancyService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOccupancyController.class)
class InternalOccupancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalOccupancyService internalOccupancyService;

    @Test
    void validate_returnsActiveTrue_whenTenantActiveInUnit() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(internalOccupancyService.isTenantActiveInUnit(tenantId, unitId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/internal/occupancies/validate")
                        .param("tenantId", tenantId.toString())
                        .param("unitId", unitId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.data.unitId").value(unitId.toString()));
    }

    @Test
    void validate_returns400_whenUnitIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/internal/occupancies/validate")
                        .param("tenantId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validate_returns400_whenTenantIdNotAValidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/internal/occupancies/validate")
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

        mockMvc.perform(get("/api/v1/internal/occupancies/active-billing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitId").value(lease.getUnitId().toString()))
                .andExpect(jsonPath("$.data[0].tenantId").value(lease.getTenantId().toString()));
    }
}
