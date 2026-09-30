package kln.ams.leaseoccupancy.service;

import kln.ams.leaseoccupancy.dto.UnitOccupancyResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupantsResponse;
import kln.ams.leaseoccupancy.dto.UserOccupancyResponse;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.repository.OccupancyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs Gateway-routed internal endpoints protected by Service JWTs (LEASE-INT-001, LEASE-INT-002, LEASE-INT-003).
 */
@Service
public class InternalOccupancyService {

    private final OccupancyRepository occupancyRepository;

    public InternalOccupancyService(OccupancyRepository occupancyRepository) {
        this.occupancyRepository = occupancyRepository;
    }

    /**
     * LEASE-INT-001: Return authoritative occupancy state for a unit.
     */
    @Transactional(readOnly = true)
    public UnitOccupancyResponse getUnitOccupancy(UUID unitId) {
        List<Occupancy> active = occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE);
        if (active.isEmpty()) {
            return new UnitOccupancyResponse(unitId, false, null, null, null, null, null);
        }
        Occupancy occ = active.get(0);
        return new UnitOccupancyResponse(
                unitId,
                true,
                occ.getLeaseId(),
                occ.getId(),
                occ.getMoveInDate(),
                occ.getMoveOutDate(),
                occ.getStatus());
    }

    /**
     * LEASE-INT-002: Return current occupants for a unit.
     */
    @Transactional(readOnly = true)
    public UnitOccupantsResponse getUnitOccupants(UUID unitId) {
        List<Occupancy> active = occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE);
        List<UnitOccupantsResponse.OccupantItem> items = active.stream()
                .map(occ -> new UnitOccupantsResponse.OccupantItem(
                        occ.getResidentId(),
                        occ.getStatus(),
                        occ.getMoveInDate(),
                        occ.getMoveOutDate()))
                .toList();
        return new UnitOccupantsResponse(unitId, items);
    }

    /**
     * LEASE-INT-003: Return occupancy relationship for a user.
     */
    @Transactional(readOnly = true)
    public UserOccupancyResponse getUserOccupancy(UUID userId) {
        List<Occupancy> occupancies = occupancyRepository.findByResidentIdOrderByMoveInDateDesc(userId);
        List<UserOccupancyResponse.OccupancyItem> items = occupancies.stream()
                .map(occ -> new UserOccupancyResponse.OccupancyItem(
                        occ.getUnitId(),
                        occ.getId(),
                        occ.getLeaseId(),
                        "TENANT_RESIDENT",
                        occ.getStatus(),
                        occ.getMoveInDate(),
                        occ.getMoveOutDate()))
                .toList();
        return new UserOccupancyResponse(userId, items);
    }
}
