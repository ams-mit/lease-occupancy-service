package kln.ams.leaseoccupancy.repository;

import kln.ams.leaseoccupancy.entity.OccupancyStatusHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OccupancyStatusHistoryRepository extends JpaRepository<OccupancyStatusHistory, UUID> {}
