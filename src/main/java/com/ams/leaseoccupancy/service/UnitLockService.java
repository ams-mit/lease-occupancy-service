package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.repository.UnitLockRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies pessimistic locking on a unit during lease activation or concurrent operations.
 */
@Service
public class UnitLockService {

    private final UnitLockRepository unitLockRepository;

    public UnitLockService(UnitLockRepository unitLockRepository) {
        this.unitLockRepository = unitLockRepository;
    }

    /**
     * Acquires a pessimistic write lock for the given unitId.
     * If the unit record does not exist in unit_locks yet, it is initialized and locked.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireUnitLock(UUID unitId) {
        unitLockRepository.ensureLockRow(unitId);
        unitLockRepository.findByUnitIdForUpdate(unitId)
                .orElseThrow(() -> new IllegalStateException("Unable to lock unit " + unitId));
    }
}
