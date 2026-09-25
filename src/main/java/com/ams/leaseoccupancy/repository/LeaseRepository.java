package com.ams.leaseoccupancy.repository;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaseRepository extends JpaRepository<Lease, UUID>, JpaSpecificationExecutor<Lease> {

    /**
     * Standard-unit 1-to-1 occupancy rule: true if the unit already has an ACTIVE
     * lease whose date range overlaps [startDate, endDate]. excludeLeaseId lets a
     * lease being re-activated ignore its own row.
     * Executes the interval overlap rule: (NewStart <= ExistingEnd) AND (NewEnd >= ExistingStart).
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

    /**
     * Executes the interval overlap query rule: (NewStart <= ExistingEnd) AND (NewEnd >= ExistingStart)
     * for active leases on the unit.
     */
    @Query("""
            SELECT COUNT(l) FROM Lease l
            WHERE l.unitId = :unitId
              AND l.status = com.ams.leaseoccupancy.entity.LeaseStatus.ACTIVE
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND (:excludeLeaseId IS NULL OR l.id <> :excludeLeaseId)
            """)
    long countOverlappingActiveLeases(
            @Param("unitId") UUID unitId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeLeaseId") UUID excludeLeaseId);

    /**
     * Executes the interval overlap query rule for specified lease statuses (e.g. ACTIVE, PENDING_ACTIVATION).
     */
    @Query("""
            SELECT COUNT(l) FROM Lease l
            WHERE l.unitId = :unitId
              AND l.status IN :statuses
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND (:excludeLeaseId IS NULL OR l.id <> :excludeLeaseId)
            """)
    long countOverlappingLeasesWithStatuses(
            @Param("unitId") UUID unitId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeLeaseId") UUID excludeLeaseId,
            @Param("statuses") Collection<LeaseStatus> statuses);

    /**
     * Finds overlapping active leases on the unit with a pessimistic write lock for concurrency control.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l FROM Lease l
            WHERE l.unitId = :unitId
              AND l.status = com.ams.leaseoccupancy.entity.LeaseStatus.ACTIVE
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND (:excludeLeaseId IS NULL OR l.id <> :excludeLeaseId)
            """)
    List<Lease> findOverlappingActiveLeasesForUpdate(
            @Param("unitId") UUID unitId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeLeaseId") UUID excludeLeaseId);

    /** Backs GET /api/v1/internal/occupancies/validate and GET /api/v1/leases/validate. */
    boolean existsByUnitIdAndTenantIdAndStatus(UUID unitId, UUID tenantId, LeaseStatus status);

    /** Backs GET /api/v1/internal/occupancies/active-billing — every currently billable unit/tenant pair. */
    List<Lease> findByStatus(LeaseStatus status);
}
