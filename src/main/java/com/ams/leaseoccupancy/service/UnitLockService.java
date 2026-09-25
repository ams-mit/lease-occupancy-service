package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.entity.UnitLock;
import com.ams.leaseoccupancy.repository.UnitLockRepository;
import java.time.Instant;
import java.util.Optional;
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
        Optional<UnitLock> lockOpt = unitLockRepository.findByUnitIdForUpdate(unitId);
        if (lockOpt.isEmpty()) {
            UnitLock lock = new UnitLock(unitId, Instant.now());
            unitLockRepository.saveAndFlush(lock);
            unitLockRepository.findByUnitIdForUpdate(unitId);
        } else {
            UnitLock lock = lockOpt.get();
            lock.setLockedAt(Instant.now());
            unitLockRepository.save(lock);
        }
    }
}
