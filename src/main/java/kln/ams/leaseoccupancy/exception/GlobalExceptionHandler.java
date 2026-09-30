package kln.ams.leaseoccupancy.exception;

import kln.ams.leaseoccupancy.config.RequestContext;
import kln.ams.leaseoccupancy.dto.ApiError;
import kln.ams.leaseoccupancy.dto.ApiResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Centralizes error handling so every controller returns the same envelope (PROJECT-A-GLOBAL-API-STANDARD
 * §15, §16) instead of leaking framework default error bodies or stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ApiError error = ex instanceof DependencyUnavailableException dependency
                ? new ApiError(ex.getErrorCode(), Map.of("service", dependency.getServiceName()))
                : ApiError.of(ex.getErrorCode());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getMessage(), error, RequestContext.getRequestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", ApiError.validation(fieldErrors), RequestContext.getRequestId()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestParameter(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid or missing request parameter", ApiError.of("VALIDATION_ERROR"),
                        RequestContext.getRequestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Unable to process request", ApiError.of("INTERNAL_SERVER_ERROR"),
                        RequestContext.getRequestId()));
    }
}
