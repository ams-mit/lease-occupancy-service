package com.ams.leaseoccupancy.controller;

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
 * Private-network-only endpoints — no JWT check (API-STANDARD-v1 §26-27). Must never be
 * routed to the frontend through the API Gateway.
 */
@RestController
@RequestMapping("/api/v1/internal/occupancies")
@Tag(name = "Internal - Occupancies", description = "Service-to-service only, no JWT required")
public class InternalOccupancyController {

    private final InternalOccupancyService internalOccupancyService;

    public InternalOccupancyController(InternalOccupancyService internalOccupancyService) {
        this.internalOccupancyService = internalOccupancyService;
    }

    @GetMapping("/validate")
    @Operation(summary = "Check whether a tenant actively resides in a unit",
            description = "Consumed by operations-service before allowing a facility booking or maintenance request.")
    public ApiResponse<OccupancyValidationResponse> validate(
            @RequestParam UUID tenantId, @RequestParam UUID unitId) {

        boolean active = internalOccupancyService.isTenantActiveInUnit(tenantId, unitId);
        OccupancyValidationResponse data = new OccupancyValidationResponse(tenantId, unitId, active);
        return ApiResponse.success("Occupancy validation completed", data, RequestContext.getRequestId());
    }

    @GetMapping("/active-billing")
    @Operation(summary = "List active units and their billing targets",
            description = "Consumed by billing-payment-service to drive recurring monthly invoicing.")
    public ApiResponse<List<BillingTargetResponse>> activeBillingTargets() {
        List<BillingTargetResponse> targets = internalOccupancyService.getActiveBillingTargets().stream()
                .map(BillingTargetResponse::from)
                .toList();

        return ApiResponse.success("Active billing targets retrieved successfully", targets, RequestContext.getRequestId());
    }
}
