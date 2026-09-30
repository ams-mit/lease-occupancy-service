package kln.ams.leaseoccupancy.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.entity.LeaseStatusHistory;

/**
 * Canonical response payload for LEASE-004: GET /api/v1/leases/{leaseId}/history
 */
public record LeaseHistoryResponse(
        UUID leaseId,
        List<HistoryItem> history) {

    public record HistoryItem(
            LeaseStatus status,
            Instant changedAt,
            String changedBy,
            String reason) {
    }

    public static LeaseHistoryResponse from(UUID leaseId, List<LeaseStatusHistory> entries) {
        List<HistoryItem> items = entries.stream()
                .map(e -> new HistoryItem(e.getToStatus(), e.getChangedAt(), e.getChangedBy(), e.getReason()))
                .toList();
        return new LeaseHistoryResponse(leaseId, items);
    }
}
