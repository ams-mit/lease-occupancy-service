package com.ams.leaseoccupancy.dto;

import org.springframework.data.domain.Page;

/** Pagination block nested in {@link ApiResponse} for list endpoints (API-STANDARD-v1 §12, §13). */
public record PaginationMeta(int page, int size, long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {

    public static PaginationMeta from(Page<?> page) {
        return new PaginationMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }
}
