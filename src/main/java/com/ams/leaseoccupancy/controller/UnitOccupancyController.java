package com.ams.leaseoccupancy.controller;

import com.ams.leaseoccupancy.config.AuthContext;
import com.ams.leaseoccupancy.config.RequestContext;
import com.ams.leaseoccupancy.dto.ActiveOccupancyResponse;
import com.ams.leaseoccupancy.dto.ApiResponse;
import com.ams.leaseoccupancy.service.LeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit occupancy query endpoints consumed by billing and other domain services (P1G2-09).
 */
@RestController
@RequestMapping("/api/v1/units")
@Tag(name = "Unit Occupancy", description = "Query active unit occupancy and lease terms for billing")
public class UnitOccupancyController {

    private final LeaseService leaseService;

    public UnitOccupancyController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @GetMapping("/{unitId}/active-occupancy")
    @Operation(summary = "Query active unit occupancy",
            description = "Returns current occupant ID, owner ID, and active lease terms for a unit. Consumed by billing-service.")
    public ApiResponse<ActiveOccupancyResponse> getActiveOccupancy(@PathVariable UUID unitId) {
        AuthContext.Principal caller = AuthContext.current();
        if (caller.isService()) {
            AuthContext.requireServiceCaller("billing-payment-service");
        } else {
            AuthContext.requireRole("MANAGER");
        }

        ActiveOccupancyResponse response = leaseService.getActiveOccupancy(unitId);
        return ApiResponse.success("Active occupancy retrieved successfully", response, RequestContext.getRequestId());
    }
}
