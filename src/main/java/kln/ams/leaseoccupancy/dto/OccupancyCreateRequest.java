package kln.ams.leaseoccupancy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record OccupancyCreateRequest(
        @NotNull UUID unitId,
        @NotNull UUID residentId,
        @NotNull UUID leaseId,
        @NotNull LocalDate startDate,
        @Size(max = 255) String notes) {
}
