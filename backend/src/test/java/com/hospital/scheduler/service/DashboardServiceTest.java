package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleExchangeRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashboardService Tests - Tổng hợp dashboard")
class DashboardServiceTest {

    @Mock private StaffRepository staffRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private ScheduleExchangeRepository exchangeRepository;

    @InjectMocks private DashboardService dashboardService;

    private SchedulePeriod period;
    private Staff staff;
    private Schedule schedule;
    private ShiftType shiftL01;

    @BeforeEach
    void setUp() {
        period = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .build();

        shiftL01 = ShiftType.builder()
                .id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(7, 30))
                .build();

        staff = Staff.builder()
                .id(1).username("staff1").fullName("Nguyen Van A").isActive(true)
                .specialty(Specialty.builder().id(1).name("Nội khoa").build())
                .build();
        staff.setStaffRoles(new HashSet<>());

        schedule = Schedule.builder()
                .id(100).period(period).workDate(LocalDate.of(2026, 6, 15))
                .staff(staff).shiftType(shiftL01).hasConflict(false)
                .build();
    }

    @Test
    @DisplayName("getDashboardSummary -> trả về DashboardSummary với counts")
    void summary() {
        when(staffRepository.countByIsActiveTrue()).thenReturn(10L);
        when(scheduleRepository.count()).thenReturn(20L);
        when(periodRepository.count()).thenReturn(1L);
        when(leaveRequestRepository.countByStatus(any())).thenReturn(0L);
        when(exchangeRepository.countByStatus(any())).thenReturn(0L);
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(schedule));

        // Build 10 active staff so findByIsActiveTrue().size() == 10.
        java.util.List<Staff> activeStaff = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            activeStaff.add(Staff.builder()
                    .id(i).username("s" + i).fullName("User " + i).isActive(true)
                    .specialty(Specialty.builder().id(1).name("Nội khoa").build())
                    .build());
        }
        when(staffRepository.findByIsActiveTrue()).thenReturn(activeStaff);
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(schedule));

        var result = dashboardService.getDashboardSummary(1);

        assertThat(result.getSummary().getTotalStaff()).isEqualTo(10);
        assertThat(result.getSummary().getActiveStaff()).isEqualTo(10);
        assertThat(result.getSummary().getTotalSchedules()).isEqualTo(1);
        assertThat(result.getShiftStatistics().getL01Count()).isEqualTo(1);
    }

    @Test
    @DisplayName("getShiftStatistics không filter period")
    void shiftStats() {
        // BUGFIX: getShiftStatistics(null) returns zeros without querying (avoids findAll).
        var result = dashboardService.getShiftStatistics(null);

        assertThat(result.getL01Count()).isZero();
        assertThat(result.getL02Count()).isZero();
    }

    @Test
    @DisplayName("getShiftStatistics with periodId -> uses findByPeriodId")
    void shiftStatsWithPeriod() {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(schedule));

        var result = dashboardService.getShiftStatistics(1);

        assertThat(result.getL01Count()).isEqualTo(1);
        assertThat(result.getL02Count()).isZero();
    }

    @Test
    @DisplayName("getStaffWorkloadByPeriod -> group by staff")
    void workload() {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(schedule));

        var result = dashboardService.getStaffWorkloadByPeriod(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStaffId()).isEqualTo(1);
        assertThat(result.get(0).getL01Count()).isEqualTo(1);
    }

    @Test
    @DisplayName("getScheduleHeatmapData -> map date -> shift counts")
    void heatmap() {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(schedule));

        var result = dashboardService.getScheduleHeatmapData(1);

        assertThat(result.get("totalSchedules")).isEqualTo(1);
        assertThat(result.get("totalStaff")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        var heatmap = (java.util.Map<LocalDate, java.util.Map<String, Long>>) result.get("heatmap");
        assertThat(heatmap).containsKey(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("getPeriodSummaries -> per-period schedule + staff count")
    void periodSummaries() {
        // BE#20: getPeriodSummaries now calls aggregateByPeriod() + findAll() instead of
        // findByPeriodId per period (N+1 fix). Match the new implementation.
        Object[] aggregateRow = new Object[]{1, 1L, 1L};
        when(scheduleRepository.aggregateByPeriod()).thenReturn(java.util.List.<Object[]>of(aggregateRow));
        when(periodRepository.findAll()).thenReturn(List.of(period));

        var result = dashboardService.getPeriodSummaries();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriodId()).isEqualTo(1);
        assertThat(result.get(0).getScheduleCount()).isEqualTo(1);
        assertThat(result.get(0).getStaffCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("aggregateByDateRange -> tổng hợp theo range có filter")
    void aggregateByRange() {
        // aggregateByDateRange uses findAll() internally with date-range filtering done in-memory.
        when(scheduleRepository.findAll())
                .thenReturn(List.of(schedule));
        when(staffRepository.findById(1)).thenReturn(java.util.Optional.of(staff));

        var result = dashboardService.aggregateByDateRange(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 1);

        assertThat(result.getTotalSchedules()).isEqualTo(1);
        assertThat(result.getDaysInRange()).isEqualTo(30);
        assertThat(result.getShiftTypeTotals()).containsEntry("L01", 1L);
        assertThat(result.getPerStaff()).hasSize(1);
    }

    @Test
    @DisplayName("aggregateByDateRange với startDate null -> throw IllegalArgumentException")
    void aggregate_nullStart() {
        assertThatThrownBy(() -> dashboardService.aggregateByDateRange(null, LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("aggregateByDateRange với startDate > endDate -> throw")
    void aggregate_invalidRange() {
        assertThatThrownBy(() -> dashboardService.aggregateByDateRange(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
