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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchedulePdfExportService Tests - Xuất báo cáo PDF")
class SchedulePdfExportServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @InjectMocks private SchedulePdfExportService pdfService;

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
    @DisplayName("exportScheduleToPdf không filter -> trả về PDF bytes")
    void exportAll() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = pdfService.exportScheduleToPdf(1);

        assertThat(data).isNotEmpty();
        assertThat(data.length).isGreaterThan(4);
        // PDF files start with %PDF
        assertThat((char) data[0]).isEqualTo('%');
        assertThat((char) data[1]).isEqualTo('P');
        assertThat((char) data[2]).isEqualTo('D');
        assertThat((char) data[3]).isEqualTo('F');
    }

    @Test
    @DisplayName("exportScheduleToPdf với filter shiftTypeId")
    void exportWithShiftTypeFilter() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = pdfService.exportScheduleToPdf(1, "L01", null, null, null);

        assertThat(data).isNotEmpty();
        assertThat((char) data[0]).isEqualTo('%');
    }

    @Test
    @DisplayName("exportScheduleToPdf với filter staffId")
    void exportWithStaffFilter() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = pdfService.exportScheduleToPdf(1, null, 1, null, null);

        assertThat(data).isNotEmpty();
    }

    @Test
    @DisplayName("exportScheduleToPdf với date range filter")
    void exportWithDateRange() throws Exception {
        when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(sampleSchedule));

        byte[] data = pdfService.exportScheduleToPdf(1, null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(data).isNotEmpty();
    }
}
