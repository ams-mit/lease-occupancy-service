package com.ams.leaseoccupancy.controller;

import com.ams.leaseoccupancy.config.AuthContext;
import com.ams.leaseoccupancy.config.RequestContext;
import com.ams.leaseoccupancy.dto.ApiResponse;
import com.ams.leaseoccupancy.dto.LeaseCreateRequest;
import com.ams.leaseoccupancy.dto.LeaseResponse;
import com.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import com.ams.leaseoccupancy.dto.PaginationMeta;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.service.LeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

/** Manager-facing lease endpoints — every operation requires the MANAGER role (AGENTS.md §3A). */
@RestController
@RequestMapping("/api/v1/leases")
@Tag(name = "Leases", description = "Contractual lease lifecycle")
public class LeaseController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROLE_MANAGER = "MANAGER";

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a lease", description =
            "Validates the tenant with identity-access-service and checks for a schedule conflict on the unit.")
    public ApiResponse<LeaseResponse> createLease(@Valid @RequestBody LeaseCreateRequest request) {
        AuthContext.requireRole(ROLE_MANAGER);
        Lease lease = leaseService.createLease(request);
        return ApiResponse.success("Lease created successfully", LeaseResponse.from(lease), RequestContext.getRequestId());
    }

    @GetMapping
    @Operation(summary = "List leases", description = "Filterable by status and/or a date the lease must be active on.")
    public ApiResponse<List<LeaseResponse>> listLeases(
            @RequestParam(required = false) LeaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeOn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuthContext.requireRole(ROLE_MANAGER);
        Page<Lease> result = leaseService.listLeases(status, activeOn, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
        List<LeaseResponse> data = result.map(LeaseResponse::from).getContent();

        return ApiResponse.successPage(
                "Leases retrieved successfully", data, PaginationMeta.from(result), RequestContext.getRequestId());
    }

    @PatchMapping("/{leaseId}/status")
    @Operation(summary = "Activate, terminate, or complete a lease")
    public ApiResponse<LeaseResponse> updateStatus(
            @PathVariable UUID leaseId, @Valid @RequestBody LeaseStatusUpdateRequest request) {

        AuthContext.requireRole(ROLE_MANAGER);
        Lease lease = leaseService.updateStatus(leaseId, request);
        return ApiResponse.success("Lease status updated successfully", LeaseResponse.from(lease), RequestContext.getRequestId());
    }
}
