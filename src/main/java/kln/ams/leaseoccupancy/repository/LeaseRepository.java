package kln.ams.leaseoccupancy.repository;

import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaseRepository extends JpaRepository<Lease, UUID>, JpaSpecificationExecutor<Lease> {

    @Query("SELECT l FROM Lease l LEFT JOIN FETCH l.occupants WHERE l.id = :leaseId")
    Optional<Lease> findWithOccupants(@Param("leaseId") UUID leaseId);

    /**
     * Executes the interval overlap query rule: (NewStart <= ExistingEnd) AND (NewEnd >= ExistingStart)
     * for active leases on the unit.
     */
    @Query("""
            SELECT COUNT(l) FROM Lease l
            WHERE l.unitId = :unitId
              AND l.status = kln.ams.leaseoccupancy.entity.LeaseStatus.ACTIVE
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND (:excludeLeaseId IS NULL OR l.id <> :excludeLeaseId)
            """)
    long countOverlappingActiveLeases(
            @Param("unitId") UUID unitId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeLeaseId") UUID excludeLeaseId);

    List<Lease> findByUnitIdOrderByStartDateDesc(UUID unitId);

    List<Lease> findByUnitIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID unitId, LeaseStatus status, LocalDate onOrBefore, LocalDate onOrAfter);

}
