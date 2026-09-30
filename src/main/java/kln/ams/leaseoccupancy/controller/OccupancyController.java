package kln.ams.leaseoccupancy.controller;

import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.UnitDetailsResponse;
import kln.ams.leaseoccupancy.config.AuthContext;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.dto.ApiResponse;
import kln.ams.leaseoccupancy.dto.OccupancyCreateRequest;
import kln.ams.leaseoccupancy.dto.OccupancyResponse;
import kln.ams.leaseoccupancy.dto.OccupancyStatusUpdateRequest;
import kln.ams.leaseoccupancy.dto.PaginationMeta;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.exception.ForbiddenException;
import kln.ams.leaseoccupancy.service.OccupancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Physical Occupancy Lifecycle (LEASE-008 through LEASE-011).
 */
@RestController
@RequestMapping("/api/v1/occupancies")
@Tag(name = "Occupancies", description = "Physical arrival, current residents, and occupancy history")
public class OccupancyController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OccupancyService occupancyService;
    private final PropertyUnitServiceClient propertyUnitServiceClient;

    public OccupancyController(OccupancyService occupancyService, PropertyUnitServiceClient propertyUnitServiceClient) {
        this.occupancyService = occupancyService;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
    }

    /**
     * LEASE-008: Register Occupancy / Move-In.
     * Roles: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register physical move-in/occupancy")
    public ApiResponse<OccupancyResponse> register(@Valid @RequestBody OccupancyCreateRequest request) {
        AuthContext.requireManagement();
        Occupancy occupancy = occupancyService.register(request);
        return ApiResponse.success("Occupancy registered successfully", OccupancyResponse.from(occupancy), RequestContext.getRequestId());
    }

    /**
     * LEASE-009: List Occupants by Unit.
     * Roles: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR, authorized owner/resident.
     */
    @GetMapping("/units/{unitId}")
    @Operation(summary = "List occupants by unit", description = "Supports status filtering, history inclusion, and pagination.")
    public ApiResponse<List<OccupancyResponse>> listOccupantsByUnit(
            @PathVariable UUID unitId,
            @RequestParam(required = false) OccupancyStatus status,
            @RequestParam(defaultValue = "false") boolean includeHistory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuthContext.Principal caller = AuthContext.current();
        if (!caller.isUser()) {
            throw new ForbiddenException("User authentication required");
        }

        if (!AuthContext.isManagement(caller)) {
            UnitDetailsResponse unit = propertyUnitServiceClient.getUnitDetails(unitId);
            boolean isOwner = unit != null && unit.ownerId() != null && unit.ownerId().toString().equals(caller.sub());
            boolean isResident = caller.roles().contains("TENANT_RESIDENT");
            if (!isOwner && !isResident) {
                throw new ForbiddenException("Caller is not authorized to view occupants of unit " + unitId);
            }
        }

        Page<Occupancy> result = occupancyService.listOccupantsForUnit(
                unitId, status, includeHistory, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));

        List<OccupancyResponse> data = result.map(OccupancyResponse::from).getContent();
        return ApiResponse.successPage("Occupants retrieved successfully", data, PaginationMeta.from(result), RequestContext.getRequestId());
    }

    /**
     * LEASE-010: Resident Occupancy History.
     * Roles: Management or authorized self.
     */
    @GetMapping("/residents/{residentId}")
    @Operation(summary = "Resident occupancy history")
    public ApiResponse<List<OccupancyResponse>> historyForResident(@PathVariable UUID residentId) {
        AuthContext.Principal caller = AuthContext.current();
        if (!caller.isUser()) {
            throw new ForbiddenException("User authentication required");
        }

        if (!AuthContext.isManagement(caller) && !residentId.toString().equals(caller.sub())) {
            throw new ForbiddenException("Only management or the resident can view this occupancy history");
        }

        List<OccupancyResponse> data = occupancyService.historyForResident(residentId).stream()
                .map(OccupancyResponse::from)
                .toList();

        return ApiResponse.success("Occupancy history retrieved successfully", data, RequestContext.getRequestId());
    }

    /**
     * LEASE-011: Change Occupancy Status / Record Move-Out.
     * Roles: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR.
     */
    @PatchMapping("/{occupancyId}/status")
    @Operation(summary = "Record move-out / deactivate occupancy")
    public ApiResponse<OccupancyResponse> updateStatus(
            @PathVariable UUID occupancyId, @Valid @RequestBody OccupancyStatusUpdateRequest request) {

        AuthContext.requireManagement();
        Occupancy occupancy = occupancyService.updateStatus(occupancyId, request);
        return ApiResponse.success("Occupancy status updated successfully", OccupancyResponse.from(occupancy), RequestContext.getRequestId());
    }
}
