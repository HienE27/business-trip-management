package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.ScheduleRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies filter behavior of {@link ReportExportService#exportScheduleToExcel}
 * and {@link ReportExportService#exportWorkloadReportToExcel} by parsing the
 * generated Excel output and asserting on row contents.
 *
 * Companion to {@link ReportExportServiceTest} which only checks "non-empty bytes".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReportExportService filter behavior — Excel content assertions")
class ReportExportServiceFilteredTest {

    @Mock private ScheduleRepository scheduleRepository;
    @InjectMocks private ReportExportService reportExportService;

    private static final int PERIOD_ID = 1;

    private SchedulePeriod period;
    private ShiftType shiftL01, shiftL02, shiftL03, shiftL04;
    private Staff staffA, staffB, staffC;
    private Specialty specialty;

    private final List<Schedule> seedSchedules = new ArrayList<>();

    @BeforeEach
    void setUp() {
        period = SchedulePeriod.builder()
                .id(PERIOD_ID).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .build();

        shiftL01 = ShiftType.builder().id("L01").name("Trực 24/24").isOvernight(true)
                .startTime(java.time.LocalTime.of(7, 30)).endTime(java.time.LocalTime.of(7, 30))
                .fatigueScore(3).build();
        shiftL02 = ShiftType.builder().id("L02").name("Thông tầm").isOvernight(false)
                .startTime(java.time.LocalTime.of(7, 30)).endTime(java.time.LocalTime.of(17, 0))
                .fatigueScore(1).build();
        shiftL03 = ShiftType.builder().id("L03").name("PK dịch vụ").isOvernight(false)
                .startTime(java.time.LocalTime.of(7, 30)).endTime(java.time.LocalTime.of(17, 0))
                .fatigueScore(1).build();
        shiftL04 = ShiftType.builder().id("L04").name("PK chuyên gia").isOvernight(false)
                .startTime(java.time.LocalTime.of(7, 30)).endTime(java.time.LocalTime.of(17, 0))
                .fatigueScore(1).build();

        specialty = Specialty.builder().id(1).name("Nội khoa").build();

        staffA = staff(1, "Nguyen Van A", specialty);
        staffB = staff(2, "Tran Thi B", specialty);
        staffC = staff(3, "Le Van C", specialty);

        // 8 schedules across 4 shift types, 3 staff, 5 dates
        seedSchedules.add(sched(100, staffA, shiftL01, LocalDate.of(2026, 6,  1), false, period));
        seedSchedules.add(sched(101, staffB, shiftL02, LocalDate.of(2026, 6,  3), false, period));
        seedSchedules.add(sched(102, staffA, shiftL03, LocalDate.of(2026, 6,  5), false, period));
        seedSchedules.add(sched(103, staffC, shiftL04, LocalDate.of(2026, 6,  8), true,  period));
        seedSchedules.add(sched(104, staffA, shiftL01, LocalDate.of(2026, 6, 12), false, period));
        seedSchedules.add(sched(105, staffB, shiftL02, LocalDate.of(2026, 6, 15), false, period));
        seedSchedules.add(sched(106, staffC, shiftL03, LocalDate.of(2026, 6, 20), false, period));
        seedSchedules.add(sched(107, staffA, shiftL04, LocalDate.of(2026, 6, 25), false, period));

        when(scheduleRepository.findByPeriodId(PERIOD_ID)).thenReturn(seedSchedules);
    }

    private static Staff staff(int id, String name, Specialty sp) {
        Staff s = Staff.builder().id(id).username("u" + id).fullName(name).isActive(true)
                .specialty(sp).build();
        s.setStaffRoles(new java.util.HashSet<>());
        return s;
    }

    private static Schedule sched(int id, Staff st, ShiftType sh, LocalDate date, boolean conflict, SchedulePeriod p) {
        return Schedule.builder().id(id).period(p).staff(st).shiftType(sh).workDate(date)
                .hasConflict(conflict).build();
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private static Sheet sheetOf(byte[] bytes) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            return wb.getSheetAt(0);
        }
    }

    private static List<String> columnValues(Sheet sheet, int col) {
        List<String> values = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            var cell = row.getCell(col);
            values.add(cell == null ? "" : cell.toString());
        }
        return values;
    }

    // ─── schedule export tests ─────────────────────────────────────────────

    @Test
    @DisplayName("No filter → all 8 schedules appear in sheet")
    void noFilterReturnsAll() throws Exception {
        byte[] data = reportExportService.exportScheduleToExcel(PERIOD_ID, null, null, null, null);

        Sheet sheet = sheetOf(data);
        // 8 data rows + 1 header
        assertThat(sheet.getLastRowNum()).isEqualTo(8);
    }

    @Test
    @DisplayName("Filter shiftTypeId=L01 → only L01 rows kept (2 rows)")
    void filterByShiftTypeKeepsOnlyMatching() throws Exception {
        byte[] data = reportExportService.exportScheduleToExcel(PERIOD_ID, "L01", null, null, null);

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(2);

        // Col index 5 = "Loại ca" name column
        List<String> loaiCa = columnValues(sheet, 5);
        assertThat(loaiCa).containsExactly("Trực 24/24", "Trực 24/24");
    }

    @Test
    @DisplayName("Filter staffId=1 → only staffA rows kept (4 rows)")
    void filterByStaffKeepsOnlyMatching() throws Exception {
        byte[] data = reportExportService.exportScheduleToExcel(PERIOD_ID, null, 1, null, null);

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(4);

        // Col index 3 = full name
        List<String> names = columnValues(sheet, 3);
        assertThat(names).allMatch(n -> n.equals("Nguyen Van A"));
    }

    @Test
    @DisplayName("Filter date range 2026-06-10 → 2026-06-22 → 3 rows in range")
    void filterByDateRangeKeepsOnlyMatching() throws Exception {
        byte[] data = reportExportService.exportScheduleToExcel(
                PERIOD_ID, null, null,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 22));

        Sheet sheet = sheetOf(data);
        // Seeds in [06-10, 06-22]: 06-12, 06-15, 06-20 = 3 rows
        assertThat(sheet.getLastRowNum()).isEqualTo(3);

        // Col index 1 = dd/MM/yyyy date string
        List<String> dates = columnValues(sheet, 1);
        assertThat(dates).containsExactlyInAnyOrder("12/06/2026", "15/06/2026", "20/06/2026");
    }

    @Test
    @DisplayName("Combined filters shiftTypeId=L01 + staffId=1 + date range → only row 100")
    void combinedFiltersNarrowToOneRow() throws Exception {
        byte[] data = reportExportService.exportScheduleToExcel(
                PERIOD_ID, "L01", 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(1);

        List<String> names = columnValues(sheet, 3);
        assertThat(names).containsExactly("Nguyen Van A");
    }

    @Test
    @DisplayName("Filter that matches nothing → header only, no data rows")
    void filterMatchesNothingReturnsHeaderOnly() throws Exception {
        when(scheduleRepository.findByPeriodId(999)).thenReturn(List.of());

        byte[] data = reportExportService.exportScheduleToExcel(999, "L01", 999, null, null);

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(0);
        // Header still present at row 0
        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("STT");
    }

    @Test
    @DisplayName("Blank shiftTypeId is treated as no filter")
    void blankShiftTypeIdIsNoOp() throws Exception {
        byte[] data = reportExportService.exportScheduleToExcel(PERIOD_ID, "  ", null, null, null);

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(8);
    }

    // ─── workload export tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Workload filter staffId=1 → counts only staffA")
    void workloadFilterByStaff() throws Exception {
        byte[] data = reportExportService.exportWorkloadReportToExcel(PERIOD_ID, 1, null);

        Sheet sheet = sheetOf(data);
        // Header + 1 data row for staffA
        assertThat(sheet.getLastRowNum()).isEqualTo(1);
        assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Nguyen Van A");
        // Col 3 = Tổng số ca (staffA has 4 schedules)
        assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Workload no filter → 3 staff rows")
    void workloadNoFilter() throws Exception {
        byte[] data = reportExportService.exportWorkloadReportToExcel(PERIOD_ID, null, null);

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(3);
    }

    @Test
    @DisplayName("Workload with date range that excludes all → header only")
    void workloadEmptyByDateRange() throws Exception {
        byte[] data = reportExportService.exportWorkloadReportToExcel(
                PERIOD_ID, null, LocalDate.of(2027, 1, 1));

        Sheet sheet = sheetOf(data);
        assertThat(sheet.getLastRowNum()).isEqualTo(0);
    }
}
