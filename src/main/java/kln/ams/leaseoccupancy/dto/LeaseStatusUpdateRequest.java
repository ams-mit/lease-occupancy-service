package kln.ams.leaseoccupancy.dto;

import kln.ams.leaseoccupancy.entity.LeaseStatus;
import jakarta.validation.constraints.NotNull;

public record LeaseStatusUpdateRequest(

        @NotNull(message = "status is required")
        LeaseStatus status,

        String reason) {
}
