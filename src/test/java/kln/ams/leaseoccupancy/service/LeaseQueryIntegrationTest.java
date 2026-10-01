package kln.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.client.ResidentServiceClient;
import kln.ams.leaseoccupancy.client.UnitDetailsResponse;
import kln.ams.leaseoccupancy.dto.LeaseResponse;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.entity.Occupant;
import kln.ams.leaseoccupancy.repository.LeaseRepository;
import kln.ams.leaseoccupancy.repository.LeaseSpecifications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Runs lease queries against the real JPA stack, outside a test transaction, the way controllers
 * call them: occupants must be readable after the service's transaction has closed.
 */
@SpringBootTest
class LeaseQueryIntegrationTest {

    @Autowired LeaseService leaseService;
    @Autowired LeaseRepository leaseRepository;
    @MockitoBean PropertyUnitServiceClient propertyUnitServiceClient;
    @MockitoBean ResidentServiceClient residentServiceClient;

    private final UUID unitId = UUID.randomUUID();
    private final UUID primaryTenant = UUID.randomUUID();
    private final UUID coOccupant = UUID.randomUUID();

    @BeforeEach
    void saveLease() {
        Lease lease = new Lease();
        lease.setUnitId(unitId);
        lease.setTenantId(primaryTenant);
        lease.setStartDate(LocalDate.now());
        lease.setEndDate(LocalDate.now().plusMonths(6));
        lease.setStatus(LeaseStatus.DRAFT);
        // Stored co-occupant first to prove responses put the primary tenant first.
        lease.addOccupant(new Occupant(coOccupant, false));
        lease.addOccupant(new Occupant(primaryTenant, true));
        leaseRepository.save(lease);
    }

    @AfterEach
    void cleanUp() {
        leaseRepository.deleteAll();
    }

    @Test
    void listWithoutFilters_returnsLeasesWithReadableOccupants() {
        List<Lease> leases = leaseService.listLeases(null, null, null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(leases).hasSize(1);
        assertThat(LeaseResponse.from(leases.get(0)).occupants()).containsExactly(primaryTenant, coOccupant);
    }

    @Test
    void listSortedByCreatedAt_putsNewestLeaseFirst() {
        Lease newer = new Lease();
        newer.setUnitId(unitId);
        newer.setTenantId(UUID.randomUUID());
        newer.setStartDate(LocalDate.now().plusYears(1));
        newer.setEndDate(LocalDate.now().plusYears(2));
        newer.setStatus(LeaseStatus.DRAFT);
        newer.addOccupant(new Occupant(newer.getTenantId(), true));
        newer = leaseRepository.save(newer);

        List<Lease> leases = leaseService.listLeases(null, null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        assertThat(leases).extracting(Lease::getId).first().isEqualTo(newer.getId());
    }

    @Test
    void listByResident_matchesTenantOrOccupant() {
        assertThat(leaseService.listLeases(null, coOccupant, null, null, null, null, PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(leaseService.listLeases(null, UUID.randomUUID(), null, null, null, null, PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void historyForUnit_returnsLeasesWithReadableOccupants() {
        when(propertyUnitServiceClient.getUnitDetails(unitId))
                .thenReturn(new UnitDetailsResponse(unitId, "AVAILABLE", 1, null));

        List<Lease> leases = leaseService.historyForUnit(unitId);

        assertThat(leases).hasSize(1);
        assertThat(LeaseResponse.from(leases.get(0)).occupants()).hasSize(2);
    }

    @Test
    void absentFilters_combineWithoutRestricting() {
        Specification<Lease> spec = Specification.where(LeaseSpecifications.hasUnitId(null))
                .and(LeaseSpecifications.hasTenantOrOccupantId(null))
                .and(LeaseSpecifications.hasStatus(null))
                .and(LeaseSpecifications.unitIdIn(null));

        assertThat(leaseRepository.findAll(spec)).hasSize(1);
        assertThat(leaseRepository.findAll(LeaseSpecifications.unitIdIn(List.of()))).isEmpty();
    }
}
