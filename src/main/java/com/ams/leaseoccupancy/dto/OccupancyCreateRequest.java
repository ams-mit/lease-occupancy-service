package com.ams.leaseoccupancy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.util.UUID;

public record OccupancyCreateRequest(
        @NotNull UUID unitId,
        @NotNull UUID residentId,
        @NotNull UUID leaseId,
        @NotNull @PastOrPresent LocalDate moveInDate) {
}
