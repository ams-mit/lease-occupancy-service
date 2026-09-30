package kln.ams.leaseoccupancy.controller;

import kln.ams.leaseoccupancy.config.AuthContext;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.dto.ApiResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupancyResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupantsResponse;
import kln.ams.leaseoccupancy.dto.UserOccupancyResponse;
import kln.ams.leaseoccupancy.service.InternalOccupancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway-routed, service-to-service internal provider endpoints.
 * Requires a Gateway-issued Service JWT (RS256, type=service).
 */
@RestController
@RequestMapping("/api/v1/internal")
@Tag(name = "Internal - Lease & Occupancy", description = "Service-to-service only, Gateway-issued Service JWT required")
public class InternalOccupancyController {

    private static final String RESIDENT_SERVICE = "resident-management-service";
    private static final String BILLING_SERVICE = "billing-payment-service";
    private static final String UTILITY_SERVICE = "utility-charge-service";
    private static final String OPERATIONS_SERVICE = "operations-service";
    private static final String COMMUNITY_SERVICE = "community-service";

    private final InternalOccupancyService internalOccupancyService;

    public InternalOccupancyController(InternalOccupancyService internalOccupancyService) {
        this.internalOccupancyService = internalOccupancyService;
    }

    /**
     * LEASE-INT-001: Return authoritative occupancy state for a unit.
     */
    @GetMapping("/units/{unitId}/occupancy")
    @Operation(summary = "Return authoritative occupancy state for a unit",
            description = "Allowed callers: resident-management-service, billing-payment-service, utility-charge-service, operations-service, community-service.")
    public ApiResponse<UnitOccupancyResponse> getUnitOccupancy(@PathVariable UUID unitId) {
        AuthContext.requireServiceCaller(RESIDENT_SERVICE, BILLING_SERVICE, UTILITY_SERVICE, OPERATIONS_SERVICE, COMMUNITY_SERVICE);
        UnitOccupancyResponse data = internalOccupancyService.getUnitOccupancy(unitId);
        return ApiResponse.success("Unit occupancy retrieved", data, RequestContext.getRequestId());
    }

    /**
     * LEASE-INT-002: Return current occupants for a unit.
     */
    @GetMapping("/units/{unitId}/occupants")
    @Operation(summary = "Return current occupants for a unit",
            description = "Allowed callers: resident-management-service, billing-payment-service, operations-service, community-service.")
    public ApiResponse<UnitOccupantsResponse> getUnitOccupants(@PathVariable UUID unitId) {
        AuthContext.requireServiceCaller(RESIDENT_SERVICE, BILLING_SERVICE, OPERATIONS_SERVICE, COMMUNITY_SERVICE);
        UnitOccupantsResponse data = internalOccupancyService.getUnitOccupants(unitId);
        return ApiResponse.success("Unit occupants retrieved", data, RequestContext.getRequestId());
    }

    /**
     * LEASE-INT-003: Return occupancy relationship for a user.
     */
    @GetMapping("/users/{userId}/occupancy")
    @Operation(summary = "Return occupancy relationship for a user",
            description = "Allowed callers: resident-management-service, billing-payment-service, operations-service, community-service.")
    public ApiResponse<UserOccupancyResponse> getUserOccupancy(@PathVariable UUID userId) {
        AuthContext.requireServiceCaller(RESIDENT_SERVICE, BILLING_SERVICE, OPERATIONS_SERVICE, COMMUNITY_SERVICE);
        UserOccupancyResponse data = internalOccupancyService.getUserOccupancy(userId);
        return ApiResponse.success("User occupancy retrieved", data, RequestContext.getRequestId());
    }
}
