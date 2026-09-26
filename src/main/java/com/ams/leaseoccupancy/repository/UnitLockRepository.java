package com.ams.leaseoccupancy.repository;

import com.ams.leaseoccupancy.entity.UnitLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitLockRepository extends JpaRepository<UnitLock, UUID> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO unit_locks (unit_id, locked_at) VALUES (:unitId, CURRENT_TIMESTAMP(6))",
            nativeQuery = true)
    void ensureLockRow(@Param("unitId") UUID unitId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UnitLock u WHERE u.unitId = :unitId")
    Optional<UnitLock> findByUnitIdForUpdate(@Param("unitId") UUID unitId);
}
