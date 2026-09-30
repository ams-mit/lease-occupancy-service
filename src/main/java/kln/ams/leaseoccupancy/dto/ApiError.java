package kln.ams.leaseoccupancy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Error body nested in {@link ApiResponse} (PROJECT-A-GLOBAL-API-STANDARD §15, §16). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, Object details) {

    public static ApiError of(String code) {
        return new ApiError(code, null);
    }

    public static ApiError validation(List<FieldError> fieldErrors) {
        return new ApiError("VALIDATION_ERROR", fieldErrors);
    }

    public record FieldError(String field, String message) {
    }
}
