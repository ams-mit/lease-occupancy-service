package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Authenticated but not authorized — wrong role or disallowed calling service (PROJECT-A-GLOBAL-API-STANDARD §17). */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
