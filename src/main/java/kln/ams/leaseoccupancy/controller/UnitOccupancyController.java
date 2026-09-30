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
    @Operation(summary = "Query active unit occupancy (LEASE-012)",
            description = "Returns current active occupancy for a unit. Allowed: SYSTEM_ADMINISTRATOR, APARTMENT_MANAGER, authorized owner/resident, or authorized service.")
    public ApiResponse<ActiveOccupancyResponse> getActiveOccupancy(@PathVariable UUID unitId) {
        AuthContext.Principal caller = AuthContext.current();
        ActiveOccupancyResponse response = leaseService.getActiveOccupancy(unitId);

        if (caller.isService()) {
            AuthContext.requireServiceCaller("billing-payment-service");
        } else if (!AuthContext.isManagement(caller)) {
            String callerId = caller.sub();
            boolean isOccupant = response.occupants() != null && response.occupants().stream()
                    .anyMatch(o -> o.residentId() != null && o.residentId().toString().equals(callerId));

            if (!isOccupant) {
                UnitDetailsResponse unit = propertyUnitServiceClient.getUnitDetails(unitId);
                boolean isOwner = unit != null && unit.ownerId() != null && unit.ownerId().toString().equals(callerId);
                if (!isOwner) {
                    throw new ForbiddenException("Caller is not authorized to view active occupancy for unit " + unitId);
                }
            }
        }

        return ApiResponse.success("Active occupancy retrieved successfully", response, RequestContext.getRequestId());
    }
}

