package com.ams.leaseoccupancy.controller;

import com.ams.leaseoccupancy.config.AuthContext;
import com.ams.leaseoccupancy.config.RequestContext;
import com.ams.leaseoccupancy.dto.ApiResponse;
import com.ams.leaseoccupancy.dto.BillingTargetResponse;
import com.ams.leaseoccupancy.dto.OccupancyValidationResponse;
import com.ams.leaseoccupancy.service.InternalOccupancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway-routed, service-to-service only endpoints. Each carries a Gateway-issued Service
 * JWT rather than a User JWT (AGENTS.md §8) — {@code sub} must be one of the specific
 * services allowed to call that endpoint (API-STANDARD-v1 §26-27).
 */
@RestController
@RequestMapping("/api/v1/internal/occupancies")
@Tag(name = "Internal - Occupancies", description = "Service-to-service only, Gateway-issued Service JWT required")
public class InternalOccupancyController {

    private static final String OPERATIONS_SERVICE = "operations-service";
    private static final String BILLING_SERVICE = "billing-payment-service";

    private final InternalOccupancyService internalOccupancyService;

    public InternalOccupancyController(InternalOccupancyService internalOccupancyService) {
        this.internalOccupancyService = internalOccupancyService;
    }

    @GetMapping("/validate")
    @Operation(summary = "Check whether a tenant actively resides in a unit",
            description = "Consumed by operations-service before allowing a facility booking or maintenance request.")
    public ApiResponse<OccupancyValidationResponse> validate(
            @RequestParam UUID tenantId, @RequestParam UUID unitId) {

        AuthContext.requireServiceCaller(OPERATIONS_SERVICE);
        boolean active = internalOccupancyService.isTenantActiveInUnit(tenantId, unitId);
        OccupancyValidationResponse data = new OccupancyValidationResponse(tenantId, unitId, active);
        return ApiResponse.success("Occupancy validation completed", data, RequestContext.getRequestId());
    }

    @GetMapping("/active-billing")
    @Operation(summary = "List active units and their billing targets",
            description = "Consumed by billing-payment-service to drive recurring monthly invoicing.")
    public ApiResponse<List<BillingTargetResponse>> activeBillingTargets() {
        AuthContext.requireServiceCaller(BILLING_SERVICE);
        List<BillingTargetResponse> targets = internalOccupancyService.getActiveBillingTargets().stream()
                .map(BillingTargetResponse::from)
                .toList();

        return ApiResponse.success("Active billing targets retrieved successfully", targets, RequestContext.getRequestId());
    }
}
