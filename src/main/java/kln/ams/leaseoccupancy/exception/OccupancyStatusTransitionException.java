package kln.ams.leaseoccupancy.exception;

import org.springframework.http.HttpStatus;

public class OccupancyStatusTransitionException extends BusinessException {

    public OccupancyStatusTransitionException(String message) {
        super(HttpStatus.CONFLICT, "OCCUPANCY_STATUS_TRANSITION_NOT_ALLOWED", message);
    }
}
