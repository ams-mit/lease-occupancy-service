package kln.ams.leaseoccupancy.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UnitNotEligibleException extends BusinessException {
    public UnitNotEligibleException(UUID unitId) {
        super(HttpStatus.CONFLICT, "OCCUPANCY_CONFLICT", "Unit " + unitId + " is not eligible for a new lease or occupancy");
    }
}
