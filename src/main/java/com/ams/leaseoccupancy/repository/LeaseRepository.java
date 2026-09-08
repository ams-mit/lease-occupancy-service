package com.ams.leaseoccupancy.repository;

import com.ams.leaseoccupancy.entity.Lease;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaseRepository extends JpaRepository<Lease, UUID>, JpaSpecificationExecutor<Lease> {

    /**
     * Standard-unit 1-to-1 occupancy rule: true if the unit already has an ACTIVE
     * lease whose date range overlaps [startDate, endDate]. excludeLeaseId lets a
     * lease being re-activated ignore its own row.
     */
    @Query("""
            SELECT COUNT(l) > 0 FROM Lease l
            WHERE l.unitId = :unitId
              AND l.status = com.ams.leaseoccupancy.entity.LeaseStatus.ACTIVE
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND (:excludeLeaseId IS NULL OR l.id <> :excludeLeaseId)
            """)
    boolean existsOverlappingActiveLease(
            @Param("unitId") UUID unitId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeLeaseId") UUID excludeLeaseId);
}
