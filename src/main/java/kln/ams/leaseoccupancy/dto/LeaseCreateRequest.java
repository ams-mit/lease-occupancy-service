package kln.ams.leaseoccupancy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LeaseCreateRequest(
        @NotNull UUID unitId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty List<@Valid OccupantInput> occupants,
        @Size(max = 255) String notes) {
    public record OccupantInput(@NotNull UUID residentId) {}
}
