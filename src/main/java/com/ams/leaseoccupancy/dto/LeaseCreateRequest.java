package com.ams.leaseoccupancy.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseCreateRequest(

        @NotNull(message = "unitId is required")
        UUID unitId,

        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotNull(message = "startDate is required")
        @FutureOrPresent(message = "startDate cannot be in the past")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        @Future(message = "endDate must be in the future")
        LocalDate endDate) {
}
