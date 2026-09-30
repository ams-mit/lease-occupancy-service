package kln.ams.leaseoccupancy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.UnitDetailsResponse;
import kln.ams.leaseoccupancy.config.AuthContext;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.dto.ActiveOccupancyResponse;
import kln.ams.leaseoccupancy.dto.ApiResponse;
import kln.ams.leaseoccupancy.exception.ForbiddenException;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.service.LeaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit occupancy query endpoints (LEASE-012).
 */
@RestController
@RequestMapping("/api/v1/units")
@Tag(name = "Unit Occupancy", description = "Query active unit occupancy and lease terms")
public class UnitOccupancyController {

    private final LeaseService leaseService;
    private final PropertyUnitServiceClient propertyUnitServiceClient;

    public UnitOccupancyController(LeaseService leaseService, PropertyUnitServiceClient propertyUnitServiceClient) {
        this.leaseService = leaseService;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
    }

    @GetMapping("/{unitId}/active-occupancy")
    @Operation(operationId = "LEASE-012", summary = "Query active unit occupancy",
            description = "Returns current active occupancy for a unit. Allowed: SYSTEM_ADMINISTRATOR, APARTMENT_MANAGER, authorized owner/resident.")
    public ApiResponse<ActiveOccupancyResponse> getActiveOccupancy(@PathVariable UUID unitId) {
        AuthContext.Principal caller = AuthContext.current();
        if (!AuthContext.isManagement(caller)) {
            if (caller.isUser() && (caller.roles().contains("OWNER") || caller.roles().contains("TENANT_RESIDENT"))) {
                throw new DependencyUnavailableException("resident-management-service", null);
            }
            throw new ForbiddenException("Resident or owner scope requires the Resident Management relationship contract");
        }

        ActiveOccupancyResponse response = leaseService.getActiveOccupancy(unitId);

        return ApiResponse.success("Active occupancy retrieved successfully", response, RequestContext.getRequestId());
    }
}
