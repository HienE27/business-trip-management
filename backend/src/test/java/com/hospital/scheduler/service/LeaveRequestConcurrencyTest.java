package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.LeaveRequestResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for BUG-m7: race condition on leave request approval.
 *
 * Two reviewers approving/rejecting the same request simultaneously previously
 * caused double state transitions. The {@code @Version} field on
 * {@link LeaveRequest} now causes the loser of the race to receive
 * {@link OptimisticLockingFailureException}, which {@code GlobalExceptionHandler}
 * maps to HTTP 409.
 *
 * These tests verify that an {@code OptimisticLockingFailureException} thrown by
 * the underlying repository is NOT swallowed — it propagates to the caller (so
 * the global handler can render the 409 properly).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LeaveRequestService - Optimistic locking (BUG-m7)")
class LeaveRequestConcurrencyTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ScheduleService scheduleService;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private CacheEvictor cacheEvictor;
    @Mock private com.hospital.scheduler.repository.SchedulePeriodRepository schedulePeriodRepository;
    @Mock private com.hospital.scheduler.repository.ScheduleRepository scheduleRepository;
    @Mock private NotificationService notificationService;
    @Mock private com.hospital.scheduler.service.EmailService emailService;
    @Mock private AuthContextService authContextService;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private LeaveRequest pendingRequest;
    private Staff reviewer;

    @BeforeEach
    void setUp() {
        reviewer = Staff.builder()
                .id(99).username("reviewer").fullName("Reviewer")
                .isActive(true).staffRoles(Set.of())
                .build();

        pendingRequest = LeaveRequest.builder()
                .id(1)
                .staff(Staff.builder().id(2).username("staff").isActive(true).staffRoles(Set.of()).build())
                .startDate(LocalDate.now().plusDays(7))
                .endDate(LocalDate.now().plusDays(9))
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("BUG-m7: Approve throws OptimisticLockingFailureException → propagates to caller (not swallowed)")
    void approve_concurrentLoss_propagatesOptimisticLock() {
        when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(pendingRequest));
        when(staffRepository.findById(99)).thenReturn(Optional.of(reviewer));

        // Simulate the loser's save() throwing because version no longer matches
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenThrow(new OptimisticLockingFailureException(
                        "Row was updated by another transaction: leave_request#1"));

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(1, 99, "OK"))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("leave_request#1");
    }

    @Test
    @DisplayName("BUG-m7: Reject throws OptimisticLockingFailureException → propagates to caller")
    void reject_concurrentLoss_propagatesOptimisticLock() {
        when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(pendingRequest));
        when(staffRepository.findById(99)).thenReturn(Optional.of(reviewer));

        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update on leave_request#1"));

        assertThatThrownBy(() -> leaveRequestService.rejectLeaveRequest(1, 99, "NOPE"))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("BUG-m7: Cancel throws OptimisticLockingFailureException → propagates to caller")
    void cancel_concurrentLoss_propagatesOptimisticLock() {
        when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(pendingRequest));

        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update on leave_request#1"));

        assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(1, pendingRequest.getStaff()))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Approve happy path → returns response (version field added, behavior preserved)")
    void approve_happyPath_stillWorks() {
        when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(pendingRequest));
        when(staffRepository.findById(99)).thenReturn(Optional.of(reviewer));
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenAnswer(inv -> {
                    LeaveRequest arg = inv.getArgument(0);
                    arg.setVersion(1L);
                    return arg;
                });

        LeaveRequestResponse resp = leaveRequestService.approveLeaveRequest(1, 99, "Looks good");

        org.assertj.core.api.Assertions.assertThat(resp.getStatus().name())
                .isEqualTo(LeaveRequest.LeaveStatus.APPROVED.name());
        org.assertj.core.api.Assertions.assertThat(resp.getReviewedAt()).isNotNull();
    }
}