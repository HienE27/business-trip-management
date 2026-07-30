package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the typed entry points added in SERVICE_AUDIT.md P2.
 *
 * <p>Covers {@link ConflictCheckRequest} factories and the
 * {@code (ConflictCheckRequest)} overloads on {@link ConflictDetectionService}.
 * The legacy 4-7 parameter overloads are still covered by
 * {@code ConflictDetectionServiceTest}; this file focuses on the new typed API.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConflictDetectionService - Typed ConflictCheckRequest entry points (P2)")
class ConflictDetectionServiceTypedApiTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleConflictRepository scheduleConflictRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ShiftRequirementRepository shiftRequirementRepository;
    @Mock private SchedulePeriodRepository schedulePeriodRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private AuthContextService authContextService;
    @Mock private EmailService emailService;
    @Mock private ConflictBroadcastService conflictBroadcastService;

    private ConflictDetectionService service;

    @BeforeEach
    void setUp() {
        service = new ConflictDetectionService(
                leaveRequestRepository, compensationDayRepository, scheduleRepository,
                scheduleConflictRepository, staffRepository, shiftRequirementRepository,
                schedulePeriodRepository, shiftTypeRepository, authContextService, emailService,
                conflictBroadcastService);

        // Default: no leaves, no comp days, no schedules on the requested date
        lenient().when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(List.of());
        lenient().when(leaveRequestRepository.findByStaffIdAndDateRange(any(), any(), any())).thenReturn(List.of());
        lenient().when(compensationDayRepository.findInRange(any(), any())).thenReturn(List.of());
        lenient().when(compensationDayRepository.findByStaffIdAndCompensationDate(any(), any())).thenReturn(java.util.Optional.empty());
        lenient().when(scheduleRepository.findByWorkDateWithDetails(any())).thenReturn(List.of());
        lenient().when(scheduleRepository.findL01SchedulesInRange(any(), any())).thenReturn(List.of());
        lenient().when(scheduleRepository.findByStaffIdAndDateRange(any(), any(), any())).thenReturn(List.of());
        lenient().when(shiftTypeRepository.findById(any())).thenReturn(java.util.Optional.empty());
    }

    // ── ConflictCheckRequest factory semantics ──────────────────────────────────

    @Test
    @DisplayName("ConflictCheckRequest.of() — no flags, no period")
    void factoryOf_buildsBareRequest() {
        ConflictCheckRequest req = ConflictCheckRequest.of(7, LocalDate.of(2026, 7, 6), "L01");
        assertThat(req.staffId()).isEqualTo(7);
        assertThat(req.workDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(req.shiftTypeId()).isEqualTo("L01");
        assertThat(req.excludeScheduleId()).isNull();
        assertThat(req.periodId()).isNull();
        assertThat(req.skipCompensationDay()).isFalse();
        assertThat(req.skipShiftTypeConflict()).isFalse();
    }

    @Test
    @DisplayName("ConflictCheckRequest.forUpdate() — carries excludeScheduleId + period")
    void factoryForUpdate_carriesExcludeAndPeriod() {
        ConflictCheckRequest req = ConflictCheckRequest.forUpdate(
                7, LocalDate.of(2026, 7, 6), "L01", 99, 10);
        assertThat(req.excludeScheduleId()).isEqualTo(99);
        assertThat(req.periodId()).isEqualTo(10);
        assertThat(req.skipCompensationDay()).isFalse();
        assertThat(req.skipShiftTypeConflict()).isFalse();
    }

    @Test
    @DisplayName("ConflictCheckRequest.forPreview() — both skip flags true")
    void factoryForPreview_bothSkipsTrue() {
        ConflictCheckRequest req = ConflictCheckRequest.forPreview(
                7, LocalDate.of(2026, 7, 6), "L01", 10);
        assertThat(req.skipCompensationDay()).isTrue();
        assertThat(req.skipShiftTypeConflict()).isTrue();
    }

    @Test
    @DisplayName("ConflictCheckRequest.forAutoSchedule() — only comp day skip")
    void factoryForAutoSchedule_onlyCompSkip() {
        ConflictCheckRequest req = ConflictCheckRequest.forAutoSchedule(
                7, LocalDate.of(2026, 7, 6), "L01", 10);
        assertThat(req.skipCompensationDay()).isTrue();
        assertThat(req.skipShiftTypeConflict()).isFalse();
    }

    // ── Typed entry point behaviour ─────────────────────────────────────────────

    @Test
    @DisplayName("detectAllConflicts(req) — empty inputs → empty result")
    void detectAllConflicts_typedReturnsEmptyForCleanState() {
        ConflictCheckRequest req = ConflictCheckRequest.of(7, LocalDate.of(2026, 7, 6), "L01");

        List<String> result = service.detectAllConflicts(req);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validateAndThrow(req) — clean state → no exception")
    void validateAndThrow_typedCleanStateDoesNotThrow() {
        ConflictCheckRequest req = ConflictCheckRequest.of(7, LocalDate.of(2026, 7, 6), "L01");

        assertThatCode(() -> service.validateAndThrow(req)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAndThrow(req) — leaves approved → throws ConflictException")
    void validateAndThrow_typedLeaveConflictThrows() {
        LocalDate workDate = LocalDate.of(2026, 7, 6);
        LeaveRequest leave = new LeaveRequest();
        leave.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        leave.setStartDate(workDate.minusDays(1));
        leave.setEndDate(workDate.plusDays(1));
        Staff staff = new Staff();
        staff.setId(7);
        leave.setStaff(staff);
        // detectLeaveConflict uses findByStaffIdAndDateRange(staffId, workDate, workDate)
        when(leaveRequestRepository.findByStaffIdAndDateRange(eq(7), eq(workDate), eq(workDate)))
                .thenReturn(List.of(leave));

        ConflictCheckRequest req = ConflictCheckRequest.of(7, workDate, "L01");

        assertThatThrownBy(() -> service.validateAndThrow(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("nghỉ phép");
    }

    @Test
    @DisplayName("hasAnyConflict(req) — true when conflicts found")
    void hasAnyConflict_typedReturnsTrueWhenConflict() {
        LocalDate workDate = LocalDate.of(2026, 7, 6);
        CompensationDay cd = new CompensationDay();
        cd.setCompensationDate(workDate);
        Staff staff = new Staff();
        staff.setId(7);
        cd.setStaff(staff);
        // detectCompensationConflict uses findByStaffIdAndCompensationDate(staffId, workDate)
        when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(7), eq(workDate)))
                .thenReturn(java.util.Optional.of(cd));

        ConflictCheckRequest req = ConflictCheckRequest.of(7, workDate, "L01");

        assertThat(service.hasAnyConflict(req)).isTrue();
    }

    @Test
    @DisplayName("forPreview — comp day conflict is bypassed, returns empty")
    void forPreview_bypassesCompDayCheck() {
        LocalDate workDate = LocalDate.of(2026, 7, 6);
        // Even if a comp day exists, the forPreview factory sets skipCompensationDay=true
        // so the conflict is suppressed.
        CompensationDay cd = new CompensationDay();
        cd.setCompensationDate(workDate);
        Staff staff = new Staff();
        staff.setId(7);
        cd.setStaff(staff);
        when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(7), eq(workDate)))
                .thenReturn(java.util.Optional.of(cd));

        ConflictCheckRequest req = ConflictCheckRequest.forPreview(7, workDate, "L01", 10);

        assertThat(service.detectAllConflicts(req)).isEmpty();
    }
}