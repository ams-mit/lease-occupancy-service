package kln.ams.leaseoccupancy.client;

import kln.ams.leaseoccupancy.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ResidentNotFoundException extends BusinessException {
    public ResidentNotFoundException(UUID residentId) {
        super(HttpStatus.NOT_FOUND, "RESIDENT_NOT_FOUND", "Resident profile not found: " + residentId);
    }
}
