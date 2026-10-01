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
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.exception.InvalidRequestException;
import kln.ams.leaseoccupancy.service.OccupancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    @Operation(operationId = "LEASE-008", summary = "Register physical move-in/occupancy")
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
    @Operation(operationId = "LEASE-009", summary = "List occupants by unit", description = "Supports status filtering, history inclusion, and pagination.")
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
            if (caller.roles().contains("OWNER") || caller.roles().contains("TENANT_RESIDENT")) {
                throw new DependencyUnavailableException("resident-management-service", null);
            }
            throw new ForbiddenException("Resident or owner scope requires the Resident Management relationship contract");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("page must be nonnegative and size must be between 1 and 100");
        }

        Page<Occupancy> result = occupancyService.listOccupantsForUnit(
                unitId, status, includeHistory, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "moveInDate")));

        List<OccupancyResponse> data = result.map(OccupancyResponse::from).getContent();
        return ApiResponse.successPage("Occupants retrieved successfully", data, PaginationMeta.from(result), RequestContext.getRequestId());
    }

    /**
     * LEASE-010: Resident Occupancy History.
     * Roles: Management or authorized self.
     */
    @GetMapping("/residents/{residentId}")
    @Operation(operationId = "LEASE-010", summary = "Resident occupancy history")
    public ApiResponse<List<OccupancyResponse>> historyForResident(@PathVariable UUID residentId) {
        AuthContext.Principal caller = AuthContext.current();
        if (!caller.isUser()) {
            throw new ForbiddenException("User authentication required");
        }

        if (!AuthContext.isManagement(caller)) {
            if (caller.roles().contains("TENANT_RESIDENT")) {
                throw new DependencyUnavailableException("resident-management-service", null);
            }
            throw new ForbiddenException("Resident self scope requires the Resident Management relationship contract");
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
    @Operation(operationId = "LEASE-011", summary = "Record move-out / deactivate occupancy")
    public ApiResponse<OccupancyResponse> updateStatus(
            @PathVariable UUID occupancyId, @Valid @RequestBody OccupancyStatusUpdateRequest request) {

        AuthContext.requireManagement();
        Occupancy occupancy = occupancyService.updateStatus(occupancyId, request);
        return ApiResponse.success("Occupancy status updated successfully", OccupancyResponse.from(occupancy), RequestContext.getRequestId());
    }
}
