package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Invalid request parameters outside a DTO body. */
public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
