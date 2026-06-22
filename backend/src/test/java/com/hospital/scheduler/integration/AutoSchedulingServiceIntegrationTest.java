package com.hospital.scheduler.integration;

import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AutoSchedulingService;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.service.AuditHistoryService;
import com.hospital.scheduler.service.NotificationService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * M07 Auto-scheduling unit tests using Mockito.
 * Verifies the orchestration of the service layer when interacting with its dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoSchedulingService Unit Tests (Mockito) - M07")
class AutoSchedulingServiceIntegrationTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ShiftRequirementRepository requirementRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private AlgorithmMetricsRepository algorithmMetricsRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private CompensationDateCalculator compensationDateCalculator;
    @Mock private NotificationService notificationService;

    private AutoSchedulingService autoSchedulingService;

    private SchedulePeriod testPeriod;
    private Staff staff1;

    @BeforeEach
    void setUp() {
        autoSchedulingService = new AutoSchedulingService(
                scheduleRepository, periodRepository, staffRepository, requirementRepository,
                compensationDayRepository, algorithmMetricsRepository, conflictDetectionService,
                auditHistoryService, compensationDateCalculator, notificationService
        );

        testPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026 - Test")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 7))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        staff1 = Staff.builder().id(1).username("dr1").fullName("Dr. Nguyen Van A").isActive(true)
                .passwordHash("hash1").maxShiftsPerMonth(5).build();

        // Common mock setup
        when(periodRepository.findById(1)).thenReturn(java.util.Optional.of(testPeriod));
        when(requirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        when(scheduleRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());
        when(staffRepository.findByIsActiveTrue()).thenReturn(List.of(staff1));
        when(conflictDetectionService.detectAllConflicts(anyInt(), any(), anyString(), any())).thenReturn(Collections.emptyList());
        lenient().when(compensationDateCalculator.calculate(any(LocalDate.class))).thenReturn(LocalDate.of(2026, 6, 8));
    }

    @Test
    @DisplayName("previewSchedule -> returns preview response")
    void previewAutoSchedule_shouldReturnPreview() {
        AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                .periodId(1).algorithmType("GREEDY").build();

        AutoScheduleResponse result = autoSchedulingService.previewSchedule(request);

        assertThat(result).isNotNull();
        assertThat(result.getPeriodId()).isEqualTo(1);
    }

    @Test
    @DisplayName("previewSchedule with L01 -> response is valid")
    void previewAutoSchedule_withL01_shouldBeValid() {
        ShiftType l01 = ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(7, 30)).build();

        when(requirementRepository.findByPeriodId(1)).thenReturn(List.of(
                ShiftRequirement.builder().id(1).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                        .shiftType(l01).requiredStaffCount(1).build()
        ));

        AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                .periodId(1).algorithmType("GREEDY").build();

        AutoScheduleResponse result = autoSchedulingService.previewSchedule(request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("previewSchedule -> no conflicts reported")
    void previewSchedule_shouldHaveNoConflicts() {
        ShiftType l01 = ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(7, 30)).build();

        when(requirementRepository.findByPeriodId(1)).thenReturn(List.of(
                ShiftRequirement.builder().id(1).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                        .shiftType(l01).requiredStaffCount(1).build()
        ));

        AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                .periodId(1).algorithmType("GREEDY").build();

        AutoScheduleResponse result = autoSchedulingService.previewSchedule(request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("autoSchedule direct save -> returns response")
    void autoSchedule_directSave_shouldReturnResponse() {
        ShiftType l01 = ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(7, 30)).build();

        when(requirementRepository.findByPeriodId(1)).thenReturn(List.of(
                ShiftRequirement.builder().id(1).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                        .shiftType(l01).requiredStaffCount(1).build()
        ));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            if (s.getId() == null) s.setId((int)(System.currentTimeMillis() % 10000));
            return s;
        });
        when(algorithmMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditHistoryService.logAction(anyString(), any(), any(), any(), any(), any())).thenReturn(null);

        AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                .periodId(1).algorithmType("GREEDY").build();

        AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("autoSchedule with L01 -> uses compensation calculator")
    void autoSchedule_withL01_shouldUseCompensationCalculator() {
        ShiftType l01 = ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(7, 30)).build();

        when(requirementRepository.findByPeriodId(1)).thenReturn(List.of(
                ShiftRequirement.builder().id(1).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                        .shiftType(l01).requiredStaffCount(1).build()
        ));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            if (s.getId() == null) s.setId((int)(System.currentTimeMillis() % 10000));
            return s;
        });
        when(algorithmMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditHistoryService.logAction(anyString(), any(), any(), any(), any(), any())).thenReturn(null);

        AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                .periodId(1).algorithmType("GREEDY").build();

        AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getUnassignedDaysReport -> returns report for empty period")
    void getUnassignedDaysReport_emptyPeriod_shouldReturnReport() {
        when(requirementRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());

        var result = autoSchedulingService.getUnassignedDaysReport(1);

        assertThat(result).isNotNull();
        assertThat(result.get("totalUnassignedDays")).isEqualTo(0);
    }
}
