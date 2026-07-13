package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Core ScheduleService behaviour that the split test files (BusinessRules, BulkL01,
 * ExpertClinic, Delete) don't directly cover. Focus: orchestration of create/update
 * guards and downstream side effects (notification, audit, L01 compensation day).
 *
 * <p>Mockito-only — no Spring context. Build the SUT manually so the test stays
 * fast and isolated from the JPA layer. The real {@code ScheduleRepository} and
 * collaborators are mocked; the only thing exercised is the service's own logic.
 */
@DisplayName("ScheduleService - core create/update orchestration")
class ScheduleServiceCoreTest {

    private static final Integer PERIOD_ID = 1;
    private static final Integer STAFF_ID = 42;
    private static final String L01 = "L01";        // shift that auto-creates comp day
    private static final String L04 = "L04";        // expert-clinic, no comp day
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 6, 1); // Monday

    private ScheduleRepository scheduleRepository;
    private SchedulePeriodRepository periodRepository;
    private StaffRepository staffRepository;
    private ShiftTypeRepository shiftTypeRepository;
    private CompensationDayRepository compensationDayRepository;
    private ScheduleConflictRepository scheduleConflictRepository;
    private HolidayRepository holidayRepository;
    private ConflictDetectionService conflictDetectionService;
    private AuditHistoryService auditHistoryService;
    private AuthContextService authContextService;
    private CompensationDateCalculator compensationDateCalculator;
    private NotificationService notificationService;
    private ConflictBroadcastService conflictBroadcastService;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(ScheduleRepository.class);
        periodRepository = mock(SchedulePeriodRepository.class);
        staffRepository = mock(StaffRepository.class);
        shiftTypeRepository = mock(ShiftTypeRepository.class);
        compensationDayRepository = mock(CompensationDayRepository.class);
        scheduleConflictRepository = mock(ScheduleConflictRepository.class);
        holidayRepository = mock(HolidayRepository.class);
        conflictDetectionService = mock(ConflictDetectionService.class);
        auditHistoryService = mock(AuditHistoryService.class);
        authContextService = mock(AuthContextService.class);
        compensationDateCalculator = mock(CompensationDateCalculator.class);
        notificationService = mock(NotificationService.class);
        conflictBroadcastService = mock(ConflictBroadcastService.class);

        service = new ScheduleService(
                mock(JdbcTemplate.class),
                scheduleRepository,
                periodRepository,
                staffRepository,
                shiftTypeRepository,
                compensationDayRepository,
                scheduleConflictRepository,
                holidayRepository,
                conflictDetectionService,
                auditHistoryService,
                authContextService,
                compensationDateCalculator,
                notificationService,
                conflictBroadcastService
        );
        // EntityManager field is @PersistenceContext; constructor doesn't take it.
        // Inject via reflection to avoid pulling Spring into the test.
        try {
            var f = ScheduleService.class.getDeclaredField("entityManager");
            f.setAccessible(true);
            f.set(service, mock(EntityManager.class));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot inject EntityManager", e);
        }
    }

    // ---------- createSchedule ----------

    @Test
    @DisplayName("createSchedule - happy path L01: persists, creates comp day, notifies with comp date, audits")
    void createSchedule_l01_persistsAndNotifies() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        ShiftType shift = newShift(L01);

        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(shiftTypeRepository.findById(L01)).thenReturn(Optional.of(shift));
        when(holidayRepository.existsByHolidayDateAndIsActiveTrue(WORK_DATE)).thenReturn(false);
        when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                PERIOD_ID, STAFF_ID, L01, WORK_DATE)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(100);
            return s;
        });
        // First findByScheduleId (inside createCompensationDay) returns empty so save() is called;
        // the second findByScheduleId (in createSchedule response build) returns the saved comp day.
        LocalDate compDate = LocalDate.of(2026, 6, 2);
        CompensationDay persistedComp = CompensationDay.builder()
                .id(500)
                .schedule(Schedule.builder().id(100).build())
                .compensationDate(compDate).build();
        when(compensationDayRepository.findByScheduleId(100))
                .thenReturn(List.of())
                .thenReturn(List.of(persistedComp));
        when(compensationDayRepository.save(any(CompensationDay.class))).thenReturn(persistedComp);
        when(authContextService.getCurrentStaff()).thenReturn(activeStaff(999));

        ScheduleResponse resp = service.createSchedule(req(L01));

        assertThat(resp.getId()).isEqualTo(100);
        assertThat(resp.getCompensationDate()).isEqualTo(compDate);

        verify(conflictDetectionService).validateAndThrowWithEmail(
                eq(STAFF_ID), eq(WORK_DATE), eq(L01), isNull(), eq(PERIOD_ID));
        verify(compensationDayRepository).save(any(CompensationDay.class));
        verify(auditHistoryService).logAction(
                eq("schedule"), eq(100), eq(AuditHistory.ActionType.INSERT),
                isNull(), any(Schedule.class), eq(999));
        verify(notificationService).createNotification(eq(STAFF_ID), argThat(dto ->
                dto.getMessage().contains(compDate.toString())));
    }

    @Test
    @DisplayName("createSchedule - happy path non-L01: no comp day, simpler notification")
    void createSchedule_nonL01_noCompDay() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        ShiftType shift = newShift(L04);

        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(shiftTypeRepository.findById(L04)).thenReturn(Optional.of(shift));
        when(holidayRepository.existsByHolidayDateAndIsActiveTrue(WORK_DATE)).thenReturn(false);
        when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                PERIOD_ID, STAFF_ID, L04, WORK_DATE)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(101);
            return s;
        });
        when(authContextService.getCurrentStaff()).thenReturn(activeStaff(999));

        ScheduleResponse resp = service.createSchedule(req(L04));

        assertThat(resp.getId()).isEqualTo(101);
        assertThat(resp.getCompensationDate()).isNull();
        verify(compensationDayRepository, never()).save(any());
        verify(notificationService).createNotification(eq(STAFF_ID), argThat(dto ->
                !dto.getMessage().contains("Ngày nghỉ bù")));
    }

    @Test
    @DisplayName("createSchedule - rejects when period is PUBLISHED (only DRAFT allows edits)")
    void createSchedule_publishedPeriod_rejected() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.PUBLISHED);
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.createSchedule(req(L01)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("createSchedule - rejects when workDate is outside period range")
    void createSchedule_dateOutsidePeriod_rejected() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));

        ScheduleRequest r = req(L01);
        r.setWorkDate(period.getEndDate().plusDays(1));

        assertThatThrownBy(() -> service.createSchedule(r))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kỳ lịch");
    }

    @Test
    @DisplayName("createSchedule - rejects on active holiday")
    void createSchedule_holiday_rejected() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(holidayRepository.existsByHolidayDateAndIsActiveTrue(WORK_DATE)).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedule(req(L01)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nghỉ lễ");
    }

    @Test
    @DisplayName("createSchedule - rejects when staff is inactive")
    void createSchedule_inactiveStaff_rejected() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        staff.setIsActive(false);
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.createSchedule(req(L01)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ngừng hoạt động");
    }

    @Test
    @DisplayName("createSchedule - rejects duplicate (same period/staff/shift/date)")
    void createSchedule_duplicate_rejected() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        ShiftType shift = newShift(L01);
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(shiftTypeRepository.findById(L01)).thenReturn(Optional.of(shift));
        when(holidayRepository.existsByHolidayDateAndIsActiveTrue(WORK_DATE)).thenReturn(false);
        when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                PERIOD_ID, STAFF_ID, L01, WORK_DATE))
                .thenReturn(Optional.of(Schedule.builder().id(999).build()));

        assertThatThrownBy(() -> service.createSchedule(req(L01)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("phân công ca này");
    }

    @Test
    @DisplayName("createSchedule - propagates conflict from detector (e.g. overlapping shift)")
    void createSchedule_conflictDetectorThrows_propagates() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        ShiftType shift = newShift(L01);
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(shiftTypeRepository.findById(L01)).thenReturn(Optional.of(shift));
        when(holidayRepository.existsByHolidayDateAndIsActiveTrue(WORK_DATE)).thenReturn(false);
        when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        doThrow(new ConflictException("Ca trùng lặp"))
                .when(conflictDetectionService).validateAndThrowWithEmail(
                        any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.createSchedule(req(L01)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("trùng");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchedule - 404 when period missing")
    void createSchedule_periodMissing_404() {
        when(periodRepository.findById(PERIOD_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createSchedule(req(L01)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- updateSchedule ----------

    @Test
    @DisplayName("updateSchedule - happy path: changes workDate + shift, audit logged, notification sent")
    void updateSchedule_happyPath() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        ShiftType shiftOld = newShift(L04);
        ShiftType shiftNew = newShift(L01);
        Schedule existing = Schedule.builder()
                .id(500).period(period).staff(staff).shiftType(shiftOld)
                .workDate(WORK_DATE).hasConflict(false).build();

        when(scheduleRepository.findById(500)).thenReturn(Optional.of(existing));
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(shiftTypeRepository.findById(L01)).thenReturn(Optional.of(shiftNew));
        when(holidayRepository.existsByHolidayDateAndIsActiveTrue(WORK_DATE)).thenReturn(false);
        when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                PERIOD_ID, STAFF_ID, L01, WORK_DATE)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authContextService.getCurrentStaff()).thenReturn(activeStaff(999));

        ScheduleRequest r = req(L01);
        ScheduleResponse resp = service.updateSchedule(500, r);

        assertThat(resp.getShiftType().getId()).isEqualTo(L01);
        verify(conflictDetectionService).validateAndThrowWithEmail(
                eq(STAFF_ID), eq(WORK_DATE), eq(L01), eq(500), eq(PERIOD_ID));
        verify(auditHistoryService).logAction(
                eq("schedule"), eq(500), eq(AuditHistory.ActionType.UPDATE),
                any(), any(Schedule.class), eq(999));
        verify(notificationService).createNotification(eq(STAFF_ID), any());
    }

    @Test
    @DisplayName("updateSchedule - rejects when period is PUBLISHED")
    void updateSchedule_publishedPeriod_rejected() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.PUBLISHED);
        Schedule existing = Schedule.builder()
                .id(500).period(period).staff(activeStaff(STAFF_ID))
                .shiftType(newShift(L04)).workDate(WORK_DATE).build();
        when(scheduleRepository.findById(500)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateSchedule(500, req(L04)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("updateSchedule - 404 when schedule id missing")
    void updateSchedule_missing_404() {
        when(scheduleRepository.findById(500)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSchedule(500, req(L04)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- getSchedulesByPeriod ----------

    @Test
    @DisplayName("getSchedulesByPeriod - empty when no schedules")
    void getByPeriod_empty() {
        when(scheduleRepository.findByPeriodId(PERIOD_ID)).thenReturn(List.of());
        assertThat(service.getSchedulesByPeriod(PERIOD_ID)).isEmpty();
        verifyNoInteractions(compensationDayRepository);
    }

    @Test
    @DisplayName("getSchedulesByPeriod - batch-loads compensation dates once")
    void getByPeriod_batchLoadsCompDates() {
        SchedulePeriod period = newPeriod(PERIOD_ID, SchedulePeriod.PeriodStatus.DRAFT);
        Staff staff = activeStaff(STAFF_ID);
        ShiftType shift = newShift(L01);
        Schedule s1 = Schedule.builder().id(1).period(period).staff(staff).shiftType(shift)
                .workDate(WORK_DATE).build();
        Schedule s2 = Schedule.builder().id(2).period(period).staff(staff).shiftType(shift)
                .workDate(WORK_DATE.plusDays(1)).build();
        when(scheduleRepository.findByPeriodId(PERIOD_ID)).thenReturn(List.of(s1, s2));
        when(compensationDayRepository.findByScheduleIds(List.of(1, 2))).thenReturn(List.of(
                CompensationDay.builder().schedule(s1)
                        .compensationDate(LocalDate.of(2026, 6, 2)).build()
        ));

        List<ScheduleResponse> out = service.getSchedulesByPeriod(PERIOD_ID);

        assertThat(out).hasSize(2);
        // One batched call — proves we don't do N+1
        verify(compensationDayRepository, times(1)).findByScheduleIds(List.of(1, 2));
    }

    // ---------- helpers ----------

    private static ScheduleRequest req(String shiftTypeId) {
        return ScheduleRequest.builder()
                .periodId(PERIOD_ID)
                .staffId(STAFF_ID)
                .shiftTypeId(shiftTypeId)
                .workDate(WORK_DATE)
                .build();
    }

    private static SchedulePeriod newPeriod(Integer id, SchedulePeriod.PeriodStatus status) {
        return SchedulePeriod.builder()
                .id(id)
                .periodName("2026-W22")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(status)
                .build();
    }

    private static Staff activeStaff(Integer id) {
        return Staff.builder()
                .id(id)
                .username("user" + id)
                .fullName("Staff " + id)
                .isActive(true)
                .staffRoles(new HashSet<>())
                .build();
    }

    private static ShiftType newShift(String id) {
        return ShiftType.builder()
                .id(id)
                .name("Ca " + id)
                .description("desc")
                .startTime(java.time.LocalTime.of(8, 0))
                .endTime(java.time.LocalTime.of(17, 0))
                .isOvernight(false)
                .fatigueScore(1)
                .build();
    }
}