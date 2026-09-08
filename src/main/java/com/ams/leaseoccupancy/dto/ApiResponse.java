package com.ams.leaseoccupancy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Standard success/error response envelope shared by every AMS microservice (API-STANDARD-v1 §11, §15). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        PaginationMeta pagination,
        ApiError error,
        Instant timestamp,
        String requestId) {

    public static <T> ApiResponse<T> success(String message, T data, String requestId) {
        return new ApiResponse<>(true, message, data, null, null, Instant.now(), requestId);
    }

    public static <T> ApiResponse<T> successPage(String message, T data, PaginationMeta pagination, String requestId) {
        return new ApiResponse<>(true, message, data, pagination, null, Instant.now(), requestId);
    }

    public static ApiResponse<Void> error(String message, ApiError error, String requestId) {
        return new ApiResponse<>(false, message, null, null, error, Instant.now(), requestId);
    }
}
