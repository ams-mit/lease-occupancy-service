package kln.ams.leaseoccupancy.controller;

import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.UnitDetailsResponse;
import kln.ams.leaseoccupancy.config.AuthContext;
import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.dto.ApiResponse;
import kln.ams.leaseoccupancy.dto.LeaseCreateRequest;
import kln.ams.leaseoccupancy.dto.LeaseHistoryResponse;
import kln.ams.leaseoccupancy.dto.LeaseResponse;
import kln.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import kln.ams.leaseoccupancy.dto.PaginationMeta;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.entity.LeaseStatusHistory;
import kln.ams.leaseoccupancy.exception.ForbiddenException;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import kln.ams.leaseoccupancy.exception.InvalidRequestException;
import kln.ams.leaseoccupancy.service.LeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Controller for Lease Lifecycle (LEASE-001 through LEASE-006).
 */
@RestController
@RequestMapping("/api/v1/leases")
@Tag(name = "Leases", description = "Contractual lease lifecycle")
public class LeaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LeaseService leaseService;
    private final PropertyUnitServiceClient propertyUnitServiceClient;

    public LeaseController(LeaseService leaseService, PropertyUnitServiceClient propertyUnitServiceClient) {
        this.leaseService = leaseService;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
    }

    /**
     * LEASE-001: Create Lease.
     * Required roles: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "LEASE-001", summary = "Create a lease", description = "Management only. Validates unit and residents through provider APIs. Dependency failure: 503 DEPENDENCY_UNAVAILABLE.")
    public ApiResponse<LeaseResponse> createLease(@Valid @RequestBody LeaseCreateRequest request) {
        AuthContext.requireManagement();
        Lease lease = leaseService.createLease(request);
        return ApiResponse.success("Lease created successfully", LeaseResponse.from(lease), RequestContext.getRequestId());
    }

    /**
     * LEASE-002: Search/List Leases.
     * Roles: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR, OWNER, TENANT_RESIDENT.
     */
    @GetMapping
    @Operation(operationId = "LEASE-002", summary = "Search/list leases", description = "Management or authorized owner/resident. Filters: unitId, residentId, ownerId, status, startDate, endDate; page defaults to 0, size to 20.")
    public ApiResponse<List<LeaseResponse>> listLeases(
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) UUID residentId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) LeaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
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
        Page<Lease> result = leaseService.listLeases(
                unitId,
                residentId,
                ownerId,
                status,
                startDate,
                endDate,
                // Newest first, so paging is stable and a just-created lease is on page one.
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<LeaseResponse> data = result.map(LeaseResponse::from).getContent();
        return ApiResponse.successPage("Leases retrieved successfully", data, PaginationMeta.from(result), RequestContext.getRequestId());
    }

    /**
     * LEASE-003: Get Lease by ID.
     * Allowed: Management, authorized owner of unit, or authorized tenant/occupant of lease.
     */
    @GetMapping("/{leaseId}")
    @Operation(operationId = "LEASE-003", summary = "Get a lease by ID")
    public ApiResponse<LeaseResponse> getLease(@PathVariable UUID leaseId) {
        AuthContext.Principal caller = AuthContext.current();
        Lease lease = leaseService.getLease(leaseId);
        assertCanViewLease(caller, lease);
        return ApiResponse.success("Lease retrieved", LeaseResponse.from(lease), RequestContext.getRequestId());
    }

    /**
     * LEASE-004: Lease History.
     * Allowed: Same scope as lease detail.
     */
    @GetMapping("/{leaseId}/history")
    @Operation(operationId = "LEASE-004", summary = "Get lease status history")
    public ApiResponse<LeaseHistoryResponse> statusHistory(@PathVariable UUID leaseId) {
        AuthContext.Principal caller = AuthContext.current();
        Lease lease = leaseService.getLease(leaseId);
        assertCanViewLease(caller, lease);

        List<LeaseStatusHistory> history = leaseService.statusHistory(leaseId);
        return ApiResponse.success("Lease status history retrieved", LeaseHistoryResponse.from(leaseId, history), RequestContext.getRequestId());
    }

    /**
     * LEASE-005: Lease History by Unit.
     * Allowed: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR, or authorized owner of unit.
     */
    @GetMapping("/units/{unitId}")
    @Operation(operationId = "LEASE-005", summary = "Get lease history for a unit")
    public ApiResponse<List<LeaseResponse>> historyForUnit(@PathVariable UUID unitId) {
        AuthContext.Principal caller = AuthContext.current();
        if (!AuthContext.isManagement(caller)) {
            if (caller.isUser() && caller.roles().contains("OWNER")) {
                throw new DependencyUnavailableException("resident-management-service", null);
            }
            throw new ForbiddenException("Owner scope requires the Resident Management relationship contract");
        }

        List<LeaseResponse> data = leaseService.historyForUnit(unitId).stream()
                .map(LeaseResponse::from)
                .toList();

        return ApiResponse.success("Lease history retrieved", data, RequestContext.getRequestId());
    }

    /**
     * LEASE-006: Change Lease Status.
     * Allowed roles: APARTMENT_MANAGER, SYSTEM_ADMINISTRATOR.
     */
    @PatchMapping("/{leaseId}/status")
    @Operation(operationId = "LEASE-006", summary = "Change lease status", description = "Management only. Enforces state machine and activation prerequisites.")
    public ApiResponse<LeaseResponse> updateStatus(
            @PathVariable UUID leaseId, @Valid @RequestBody LeaseStatusUpdateRequest request) {

        AuthContext.requireManagement();
        Lease lease = leaseService.updateStatus(leaseId, request);
        return ApiResponse.success("Lease status updated successfully", LeaseResponse.from(lease), RequestContext.getRequestId());
    }

    private void assertCanViewLease(AuthContext.Principal caller, Lease lease) {
        if (!caller.isUser()) {
            throw new ForbiddenException("User authentication required");
        }
        if (AuthContext.isManagement(caller)) {
            return;
        }

        if (caller.roles().contains("OWNER") || caller.roles().contains("TENANT_RESIDENT")) {
            throw new DependencyUnavailableException("resident-management-service", null);
        }
        throw new ForbiddenException("Caller role is not allowed to view this lease");
    }
}
