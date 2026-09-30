package kln.ams.leaseoccupancy.dto;

import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record OccupancyStatusUpdateRequest(@NotNull OccupancyStatus status,
                                           @Size(max = 255) String reason,
                                           @NotNull LocalDate effectiveDate) {}
