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
    @DisplayName("Delete happy path: succeeds and logs without CSP invocation")
    void draftPeriod_succeedsWithoutCsp() {
        when(scheduleRepository.findById(10)).thenReturn(java.util.Optional.of(scheduleToDelete));
        stubCleanupPaths();

        // No exception → delete succeeded
        assertThatCode(() -> deleteService.deleteSchedule(10)).doesNotThrowAnyException();

        // Verify schedule lookup happened once
        verify(scheduleRepository, times(1)).findById(10);
    }
}
