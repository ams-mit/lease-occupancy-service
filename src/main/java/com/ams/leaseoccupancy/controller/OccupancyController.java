package com.ams.leaseoccupancy.controller;

import com.ams.leaseoccupancy.config.AuthContext;
import com.ams.leaseoccupancy.config.RequestContext;
import com.ams.leaseoccupancy.dto.ApiResponse;
import com.ams.leaseoccupancy.dto.OccupancyCreateRequest;
import com.ams.leaseoccupancy.dto.OccupancyResponse;
import com.ams.leaseoccupancy.exception.ForbiddenException;
import com.ams.leaseoccupancy.service.OccupancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/occupancies")
@Tag(name = "Occupancies", description = "Physical arrival, current residents, and occupancy history")
public class OccupancyController {
    private final OccupancyService service;

    public OccupancyController(OccupancyService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a resident's arrival under an active lease")
    public ApiResponse<OccupancyResponse> register(@Valid @RequestBody OccupancyCreateRequest request) {
        AuthContext.requireRole("MANAGER");
        return ApiResponse.success("Occupancy registered", OccupancyResponse.from(service.register(request)),
                RequestContext.getRequestId());
    }

    @GetMapping("/units/{unitId}")
    @Operation(summary = "List current physical occupants of a unit")
    public ApiResponse<List<OccupancyResponse>> currentForUnit(@PathVariable UUID unitId) {
        AuthContext.requireRole("MANAGER");
        return ApiResponse.success("Current occupants retrieved",
                service.currentForUnit(unitId).stream().map(OccupancyResponse::from).toList(),
                RequestContext.getRequestId());
    }

    @GetMapping("/residents/{residentId}")
    @Operation(summary = "List a resident's occupancy history")
    public ApiResponse<List<OccupancyResponse>> historyForResident(@PathVariable UUID residentId) {
        AuthContext.Principal caller = AuthContext.current();
        if (!caller.isUser() || (!caller.roles().contains("MANAGER")
                && !residentId.toString().equals(caller.sub()))) {
            throw new ForbiddenException("Only a manager or the resident can view occupancy history");
        }
        return ApiResponse.success("Occupancy history retrieved",
                service.historyForResident(residentId).stream().map(OccupancyResponse::from).toList(),
                RequestContext.getRequestId());
    }

    @PatchMapping("/{occupancyId}/status")
    @Operation(summary = "Deactivate an active physical occupancy")
    public ApiResponse<OccupancyResponse> deactivate(@PathVariable UUID occupancyId) {
        AuthContext.requireRole("MANAGER");
        return ApiResponse.success("Occupancy deactivated", OccupancyResponse.from(service.deactivate(occupancyId)),
                RequestContext.getRequestId());
    }
}
