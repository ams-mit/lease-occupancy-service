package kln.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class InvalidTenantException extends BusinessException {

    public InvalidTenantException(UUID tenantId) {
        super(HttpStatus.NOT_FOUND, "RESPONSIBLE_PARTY_NOT_FOUND",
                "Responsible party/resident " + tenantId + " is not a valid, active user");
    }
}
