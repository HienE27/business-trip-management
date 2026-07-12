package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.CSPScheduler;
import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScheduleDeleteService Tests - Xóa lịch trực khỏi kỳ DRAFT")
class ScheduleDeleteServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private ScheduleConflictRepository scheduleConflictRepository;
    @Mock private ScheduleExchangeRepository scheduleExchangeRepository;
    @Mock private ShiftRequirementRepository shiftRequirementRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private NotificationService notificationService;
    @Mock private AuthContextService authContextService;
    @Mock private CSPScheduler cspScheduler;
    @Mock private SchedulingResultLoader schedulingResultLoader;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ScheduleDeleteService deleteService;

    private Staff currentAdmin;
    private SchedulePeriod draftPeriod;
    private ShiftType shiftL01;
    private Schedule scheduleToDelete;

    @BeforeEach
    void setUp() {
        currentAdmin = Staff.builder()
                .id(99).username("admin").fullName("Admin User").isActive(true).build();

        draftPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 7/2026 - DRAFT")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        shiftL01 = ShiftType.builder()
                .id("L01").name("Lịch trực 24/24").isOvernight(true).build();

        scheduleToDelete = Schedule.builder()
                .id(10).period(draftPeriod).staff(currentAdmin).shiftType(shiftL01)
                .workDate(LocalDate.of(2026, 7, 5))
                .build();

        when(authContextService.getCurrentStaff()).thenReturn(currentAdmin);
    }

    /** Helper: stub the pre-delete cleanup paths (compensation, conflict, exchange queries)
     *  + native JdbcTemplate updates to non-zero so audit/delete logic can run. */
    private void stubCleanupPaths() {
        when(compensationDayRepository.findByScheduleId(10)).thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenReturn(0);
        when(scheduleExchangeRepository.findByRequesterScheduleIdOrTargetScheduleId(10, 10))
                .thenReturn(Collections.emptyList());
        when(scheduleConflictRepository.findByScheduleId(10)).thenReturn(Collections.emptyList());
        // Final native DELETE of the schedule returns 1 row
        when(jdbcTemplate.update(eq("DELETE FROM schedule WHERE id = ?"), eq(10))).thenReturn(1);
    }

    // ==================== deleteSchedule — re-solve coverage ====================

    @Test
    @DisplayName("Delete happy path: reSolve() valid → không throw, REMOVE delta có đúng staffId/date/shiftType")
    void draftPeriod_reSolveValid_succeedsAndLogs() {
        when(scheduleRepository.findById(10)).thenReturn(java.util.Optional.of(scheduleToDelete));
        stubCleanupPaths();

        SchedulingResult previous = SchedulingResult.builder()
                .assignments(java.util.Map.of("99_2026-07-05", "L01"))
                .valid(true).build();
        when(schedulingResultLoader.loadPreviousFromDb(eq(1), any(ScheduleRepository.class)))
                .thenReturn(previous);
        when(shiftRequirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(Collections.emptyList());
        when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(currentAdmin));
        when(cspScheduler.reSolve(any(), any(), any(), any(), any()))
                .thenReturn(SchedulingResult.builder().assignments(new java.util.HashMap<>()).valid(true).build());

        // No exception → delete succeeded
        assertThatCode(() -> deleteService.deleteSchedule(10)).doesNotThrowAnyException();

        // Verify schedule lookup happened once
        verify(scheduleRepository, times(1)).findById(10);

        // Verify the REMOVE delta: 1 entry, correct staff/date/shiftType, no other deltas
        ArgumentCaptor<ScheduleChange> changeCap = ArgumentCaptor.forClass(ScheduleChange.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Staff>> staffCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShiftRequirementInfo>> reqCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LeaveRequest>> leaveCap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<SchedulingResult> prevCap = ArgumentCaptor.forClass(SchedulingResult.class);

        verify(cspScheduler).reSolve(prevCap.capture(), changeCap.capture(), staffCap.capture(),
                reqCap.capture(), leaveCap.capture());

        assertThat(prevCap.getValue()).isSameAs(previous);
        assertThat(changeCap.getValue().getRemoved()).hasSize(1);
        assertThat(changeCap.getValue().getModified()).isNullOrEmpty();
        assertThat(changeCap.getValue().getAdded()).isNullOrEmpty();

        ScheduleChange.AssignmentDelta removed = changeCap.getValue().getRemoved().get(0);
        assertThat(removed.getStaffId()).isEqualTo(99); // currentAdmin's id
        assertThat(removed.getDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(removed.getShiftType()).isEqualTo("L01");

        assertThat(staffCap.getValue()).containsExactly(currentAdmin);
        assertThat(reqCap.getValue()).isEmpty();
        assertThat(leaveCap.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Delete infeasible: reSolve() trả invalid → throw BadRequestException, rollback @Transactional")
    void draftPeriod_reSolveInfeasible_throwsBadRequest() {
        when(scheduleRepository.findById(10)).thenReturn(java.util.Optional.of(scheduleToDelete));
        stubCleanupPaths();

        when(schedulingResultLoader.loadPreviousFromDb(eq(1), any(ScheduleRepository.class)))
                .thenReturn(SchedulingResult.builder()
                        .assignments(java.util.Map.of("99_2026-07-05", "L01"))
                        .valid(true).build());
        when(shiftRequirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(Collections.emptyList());
        when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(currentAdmin));
        SchedulingResult invalid = SchedulingResult.builder()
                .assignments(new java.util.HashMap<>())
                .valid(false)
                .errors(List.of("staff_99_quota_exceeded"))
                .build();
        when(cspScheduler.reSolve(any(), any(), any(), any(), any())).thenReturn(invalid);

        assertThatThrownBy(() -> deleteService.deleteSchedule(10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bất khả thi")
                .hasMessageContaining("staff_99_quota_exceeded");

        // reSolve was still invoked (the failure is what triggered the throw)
        verify(cspScheduler, times(1)).reSolve(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("reSolve() throws RuntimeException → propagate ra ngoài (không catch - khác với exchange)")
    void draftPeriod_reSolveThrowsRuntime_propagates() {
        when(scheduleRepository.findById(10)).thenReturn(java.util.Optional.of(scheduleToDelete));
        stubCleanupPaths();

        when(schedulingResultLoader.loadPreviousFromDb(eq(1), any(ScheduleRepository.class)))
                .thenReturn(SchedulingResult.builder()
                        .assignments(java.util.Map.of("99_2026-07-05", "L01"))
                        .valid(true).build());
        when(shiftRequirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(Collections.emptyList());
        when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(currentAdmin));
        when(cspScheduler.reSolve(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("CSP internal boom"));

        // NOTE: This documents the divergence from ScheduleExchangeService — exchange
        // has a try/catch that swallows CSP exceptions ("best-effort"); delete does not.
        // If the user wants delete to match exchange's best-effort behavior, the prod
        // code needs a try/catch added in reschedulePeriodIncrementalAfterDelete.
        assertThatThrownBy(() -> deleteService.deleteSchedule(10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CSP internal boom");
    }
}
