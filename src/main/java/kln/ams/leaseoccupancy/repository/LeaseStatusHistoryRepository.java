package kln.ams.leaseoccupancy.repository;

import kln.ams.leaseoccupancy.entity.LeaseStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseStatusHistoryRepository extends JpaRepository<LeaseStatusHistory, UUID> {
    List<LeaseStatusHistory> findByLeaseIdOrderByChangedAtAsc(UUID leaseId);
}
