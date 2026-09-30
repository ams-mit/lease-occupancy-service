package kln.ams.leaseoccupancy.repository;

import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OccupancyRepository extends JpaRepository<Occupancy, UUID> {

    List<Occupancy> findByUnitIdAndStatus(UUID unitId, OccupancyStatus status);

    Page<Occupancy> findByUnitIdAndStatus(UUID unitId, OccupancyStatus status, Pageable pageable);

    Page<Occupancy> findByUnitId(UUID unitId, Pageable pageable);

    List<Occupancy> findByResidentIdOrderByMoveInDateDesc(UUID residentId);

    Page<Occupancy> findByResidentIdOrderByMoveInDateDesc(UUID residentId, Pageable pageable);

    boolean existsByUnitIdAndResidentIdAndStatus(UUID unitId, UUID residentId, OccupancyStatus status);

    @Query("""
            SELECT COUNT(o) > 0 FROM Occupancy o, Lease l
            WHERE o.leaseId = l.id AND o.unitId = :unitId AND o.residentId = :residentId
              AND o.status = kln.ams.leaseoccupancy.entity.OccupancyStatus.ACTIVE
              AND l.status = kln.ams.leaseoccupancy.entity.LeaseStatus.ACTIVE
              AND l.startDate <= :today AND l.endDate >= :today
            """)
    boolean existsCurrentlyEligibleResident(
            @Param("unitId") UUID unitId,
            @Param("residentId") UUID residentId,
            @Param("today") LocalDate today);

    long countByUnitIdAndStatus(UUID unitId, OccupancyStatus status);

    List<Occupancy> findByLeaseIdAndStatus(UUID leaseId, OccupancyStatus status);
}
