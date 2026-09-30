package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

/** Missing/malformed/expired/invalid-signature JWT — always a 401 (PROJECT-A-GLOBAL-API-STANDARD §17). */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
