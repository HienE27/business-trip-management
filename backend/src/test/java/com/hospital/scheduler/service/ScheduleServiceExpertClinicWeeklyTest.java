package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ExpertClinicWeeklyResponse;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScheduleService - Expert Clinic Weekly View Tests")
class ScheduleServiceExpertClinicWeeklyTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;
    @Mock private CompensationDateCalculator compensationDateCalculator;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ScheduleService scheduleService;

    private SchedulePeriod draftPeriod;
    private ShiftType l04ShiftType;
    private Staff testStaff;

    @BeforeEach
    void setUp() {
        testStaff = Staff.builder()
                .id(1).username("doctor1").fullName("Dr. Nguyen Van A").isActive(true)
                .build();
        testStaff.setStaffRoles(new HashSet<>());

        draftPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        l04ShiftType = ShiftType.builder()
                .id("L04").name("Phòng khám chuyên gia").isOvernight(false).fatigueScore(2)
                .startTime(java.time.LocalTime.of(8, 0))
                .endTime(java.time.LocalTime.of(16, 0))
                .build();
    }

    @Nested
    @DisplayName("getExpertClinicWeeklyView - Lịch chuyên gia theo tuần")
    class GetExpertClinicWeeklyView {

        @Test
        @DisplayName("Có lịch trong tuần -> trả về 7 ngày với lịch")
        void withSchedulesInWeek_shouldReturn7Days() {
            LocalDate weekStart = LocalDate.of(2026, 6, 1); // Monday

            Schedule sched1 = Schedule.builder()
                    .id(100).period(draftPeriod).workDate(LocalDate.of(2026, 6, 1))
                    .staff(testStaff).shiftType(l04ShiftType).hasConflict(false)
                    .build();
            Schedule sched2 = Schedule.builder()
                    .id(101).period(draftPeriod).workDate(LocalDate.of(2026, 6, 3))
                    .staff(testStaff).shiftType(l04ShiftType).hasConflict(false)
                    .build();

            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(scheduleRepository.findExpertClinicByPeriodAndSpecialty(1, null))
                    .thenReturn(List.of(sched1, sched2));
            when(compensationDayRepository.findByPeriodIdWithStaff(1)).thenReturn(Collections.emptyList());
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt(), anyBoolean(), anyBoolean()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByScheduleId(anyInt())).thenReturn(Collections.emptyList());

            ExpertClinicWeeklyResponse result = scheduleService.getExpertClinicWeeklyView(1, weekStart, null);

            assertThat(result.getWeekSchedule()).hasSize(7);
            assertThat(result.getWeekStart()).isEqualTo(weekStart);
            assertThat(result.getWeekEnd()).isEqualTo(weekStart.plusDays(6));

            // Day 1 (Monday 6/1) should have 1 schedule
            ExpertClinicWeeklyResponse.DaySchedule monday = result.getWeekSchedule().stream()
                    .filter(d -> d.getDate().equals(LocalDate.of(2026, 6, 1)))
                    .findFirst().orElseThrow();
            assertThat(monday.getSchedules()).hasSize(1);
            assertThat(monday.getDayOfWeek()).isEqualTo("Thứ 2");

            // Day 3 (Wednesday 6/3) should have 1 schedule
            ExpertClinicWeeklyResponse.DaySchedule wednesday = result.getWeekSchedule().stream()
                    .filter(d -> d.getDate().equals(LocalDate.of(2026, 6, 3)))
                    .findFirst().orElseThrow();
            assertThat(wednesday.getSchedules()).hasSize(1);

            // Days without schedules should have empty list
            ExpertClinicWeeklyResponse.DaySchedule tuesday = result.getWeekSchedule().stream()
                    .filter(d -> d.getDate().equals(LocalDate.of(2026, 6, 2)))
                    .findFirst().orElseThrow();
            assertThat(tuesday.getSchedules()).isEmpty();
        }

        @Test
        @DisplayName("Không có lịch trong tuần -> trả về 7 ngày rỗng")
        void noSchedulesInWeek_shouldReturn7EmptyDays() {
            LocalDate weekStart = LocalDate.of(2026, 6, 8); // Monday

            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(scheduleRepository.findExpertClinicByPeriodAndSpecialty(1, null))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByPeriodIdWithStaff(1)).thenReturn(Collections.emptyList());

            ExpertClinicWeeklyResponse result = scheduleService.getExpertClinicWeeklyView(1, weekStart, null);

            assertThat(result.getWeekSchedule()).hasSize(7);
            assertThat(result.getWeekSchedule()).allMatch(d -> d.getSchedules().isEmpty());
        }

        @Test
        @DisplayName("Kỳ lịch không tồn tại -> throw ResourceNotFoundException")
        void periodNotFound_shouldThrow() {
            when(periodRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.getExpertClinicWeeklyView(999, LocalDate.of(2026, 6, 1), null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy kỳ lịch");
        }

        @Test
        @DisplayName("weekStart = null -> dùng startDate của period")
        void weekStartNull_shouldUsePeriodStartDate() {
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(scheduleRepository.findExpertClinicByPeriodAndSpecialty(1, null))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByPeriodIdWithStaff(1)).thenReturn(Collections.emptyList());

            ExpertClinicWeeklyResponse result = scheduleService.getExpertClinicWeeklyView(1, null, null);

            assertThat(result.getWeekStart()).isEqualTo(draftPeriod.getStartDate());
            assertThat(result.getWeekEnd()).isEqualTo(draftPeriod.getStartDate().plusDays(6));
        }

        @Test
        @DisplayName("Filter theo specialtyId -> chỉ trả về lịch của specialty đó")
        void filterBySpecialtyId_shouldReturnOnlyMatchingSpecialty() {
            LocalDate weekStart = LocalDate.of(2026, 6, 1);

            Specialty cardio = Specialty.builder().id(1).name("Cardiology").build();
            Specialty neuro = Specialty.builder().id(2).name("Neurology").build();

            Staff cardioStaff = Staff.builder()
                    .id(1).username("doc1").fullName("Dr. Cardio").isActive(true)
                    .specialty(cardio).build();
            cardioStaff.setStaffRoles(new HashSet<>());

            Staff neuroStaff = Staff.builder()
                    .id(2).username("doc2").fullName("Dr. Neuro").isActive(true)
                    .specialty(neuro).build();
            neuroStaff.setStaffRoles(new HashSet<>());

            Schedule cardioSched = Schedule.builder()
                    .id(100).period(draftPeriod).workDate(LocalDate.of(2026, 6, 1))
                    .staff(cardioStaff).shiftType(l04ShiftType).hasConflict(false)
                    .build();
            Schedule neuroSched = Schedule.builder()
                    .id(101).period(draftPeriod).workDate(LocalDate.of(2026, 6, 1))
                    .staff(neuroStaff).shiftType(l04ShiftType).hasConflict(false)
                    .build();

            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            // Filtered by specialtyId=1 (Cardiology)
            when(scheduleRepository.findExpertClinicByPeriodAndSpecialty(1, 1))
                    .thenReturn(List.of(cardioSched));
            when(compensationDayRepository.findByPeriodIdWithStaff(1)).thenReturn(Collections.emptyList());
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt(), anyBoolean(), anyBoolean()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByScheduleId(anyInt())).thenReturn(Collections.emptyList());

            ExpertClinicWeeklyResponse result = scheduleService.getExpertClinicWeeklyView(1, weekStart, 1);

            assertThat(result.getWeekSchedule()).hasSize(7);
            ExpertClinicWeeklyResponse.DaySchedule monday = result.getWeekSchedule().get(0);
            assertThat(monday.getSchedules()).hasSize(1);
            assertThat(monday.getSchedules().get(0).getStaff().getFullName()).isEqualTo("Dr. Cardio");
        }
    }
}
