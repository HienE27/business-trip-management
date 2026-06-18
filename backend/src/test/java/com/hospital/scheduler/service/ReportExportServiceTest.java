package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.ScheduleRepository;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReportExportService Tests - Xuất báo cáo Excel")
class ReportExportServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @InjectMocks private ReportExportService reportExportService;

    private Schedule sampleSchedule;
    private SchedulePeriod period;
    private ShiftType shiftL01;
    private Staff staff;
    private Specialty specialty;

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

        specialty = Specialty.builder().id(1).name("Nội khoa").build();
        staff = Staff.builder()
                .id(1).username("staff1").fullName("Nguyen Van A").isActive(true)
                .specialty(specialty).build();
        staff.setStaffRoles(new java.util.HashSet<>());

        sampleSchedule = Schedule.builder()
                .id(100).period(period).workDate(LocalDate.of(2026, 6, 15))
                .staff(staff).shiftType(shiftL01).hasConflict(false)
                .build();
    }

    @Test
    @DisplayName("exportScheduleToExcel không filter -> trả về bytes")
    void exportAll() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = reportExportService.exportScheduleToExcel(1);

        assertThat(data).isNotEmpty();
        // Excel files start with PK (zip)
        assertThat(data[0]).isEqualTo((byte) 'P');
        assertThat(data[1]).isEqualTo((byte) 'K');
    }

    @Test
    @DisplayName("exportScheduleToExcel với filter shiftTypeId=non-matching -> empty file với header only")
    void exportFilteredNoMatch() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = reportExportService.exportScheduleToExcel(1, "L02", null, null, null);

        assertThat(data).isNotEmpty();
    }

    @Test
    @DisplayName("exportScheduleToExcel với date range filter")
    void exportWithDateRange() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = reportExportService.exportScheduleToExcel(1, null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(data).isNotEmpty();
    }

    @Test
    @DisplayName("exportScheduleToExcel với staffId filter")
    void exportWithStaffFilter() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = reportExportService.exportScheduleToExcel(1, null, 1, null, null);

        assertThat(data).isNotEmpty();
    }

    @Test
    @DisplayName("exportWorkloadReportToExcel")
    void exportWorkload() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = reportExportService.exportWorkloadReportToExcel(1);

        assertThat(data).isNotEmpty();
    }

    @Test
    @DisplayName("exportWorkloadReportToExcel với staff filter")
    void exportWorkloadWithFilter() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = reportExportService.exportWorkloadReportToExcel(1, 1, LocalDate.of(2026, 6, 1));

        assertThat(data).isNotEmpty();
    }

    @Test
    @DisplayName("exportWorkloadReportToExcel với 0 schedule")
    void exportWorkloadEmpty() throws Exception {
        when(scheduleRepository.findByPeriodId(999)).thenReturn(Collections.emptyList());

        byte[] data = reportExportService.exportWorkloadReportToExcel(999);

        assertThat(data).isNotEmpty();
    }
}
