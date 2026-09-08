package com.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.IdentityUserValidation;
import com.ams.leaseoccupancy.dto.LeaseCreateRequest;
import com.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.exception.InvalidLeaseDatesException;
import com.ams.leaseoccupancy.exception.InvalidLeaseStatusTransitionException;
import com.ams.leaseoccupancy.exception.InvalidTenantException;
import com.ams.leaseoccupancy.exception.LeaseConflictException;
import com.ams.leaseoccupancy.exception.LeaseNotFoundException;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaseServiceTest {

    @Mock
    private LeaseRepository leaseRepository;

    @Mock
    private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private LeaseService leaseService;

    private UUID unitId;
    private UUID tenantId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        unitId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        startDate = LocalDate.now().plusDays(1);
        endDate = LocalDate.now().plusMonths(6);
    }

    @Test
    void createLease_savesAsPending_whenTenantValidAndNoConflict() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, endDate);
        when(identityServiceClient.validateUser(tenantId))
                .thenReturn(new IdentityUserValidation(tenantId, true, true));
        when(leaseRepository.existsOverlappingActiveLease(eq(unitId), eq(startDate), eq(endDate), isNull()))
                .thenReturn(false);
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.createLease(request);

        assertThat(result.getStatus()).isEqualTo(LeaseStatus.PENDING);
        assertThat(result.getUnitId()).isEqualTo(unitId);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void createLease_rejects_whenEndDateNotAfterStartDate() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, startDate);

        assertThatThrownBy(() -> leaseService.createLease(request))
                .isInstanceOf(InvalidLeaseDatesException.class);
    }

    @Test
    void createLease_rejects_whenTenantNotValid() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, endDate);
        when(identityServiceClient.validateUser(tenantId))
                .thenReturn(new IdentityUserValidation(tenantId, true, false));

        assertThatThrownBy(() -> leaseService.createLease(request))
                .isInstanceOf(InvalidTenantException.class);
    }

    @Test
    void createLease_rejects_whenUnitHasOverlappingActiveLease() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, endDate);
        when(identityServiceClient.validateUser(tenantId))
                .thenReturn(new IdentityUserValidation(tenantId, true, true));
        when(leaseRepository.existsOverlappingActiveLease(eq(unitId), eq(startDate), eq(endDate), isNull()))
                .thenReturn(true);

        assertThatThrownBy(() -> leaseService.createLease(request))
                .isInstanceOf(LeaseConflictException.class);
    }

    @Test
    void updateStatus_throwsNotFound_whenLeaseMissing() {
        UUID leaseId = UUID.randomUUID();
        when(leaseRepository.findById(leaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.updateStatus(leaseId, new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null)))
                .isInstanceOf(LeaseNotFoundException.class);
    }

    @Test
    void updateStatus_rejects_illegalTransitionFromTerminated() {
        Lease lease = existingLeaseWithStatus(LeaseStatus.TERMINATED);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));

        assertThatThrownBy(() -> leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null)))
                .isInstanceOf(InvalidLeaseStatusTransitionException.class);
    }

    @Test
    void updateStatus_activates_whenTransitionValidAndNoConflict() {
        Lease lease = existingLeaseWithStatus(LeaseStatus.PENDING);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.existsOverlappingActiveLease(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId()))
                .thenReturn(false);
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null));

        assertThat(result.getStatus()).isEqualTo(LeaseStatus.ACTIVE);
    }

    private Lease existingLeaseWithStatus(LeaseStatus status) {
        Lease lease = new Lease();
        lease.setId(UUID.randomUUID());
        lease.setUnitId(unitId);
        lease.setTenantId(tenantId);
        lease.setStartDate(startDate);
        lease.setEndDate(endDate);
        lease.setStatus(status);
        return lease;
    }
}
