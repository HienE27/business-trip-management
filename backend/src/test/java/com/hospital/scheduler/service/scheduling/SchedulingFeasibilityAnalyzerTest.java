package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchedulingFeasibilityAnalyzerTest {

    @Mock private StaffRepository staffRepository;
    @Mock private ShiftRequirementRepository requirementRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private AlgorithmConfigService algorithmConfigService;

    private SchedulingFeasibilityAnalyzer analyzer;

    // Shared test fixtures
    private Staff staffNgoai, staffNoi, staffSan, staffMat, staffChild1, staffChild2;
    private Specialty specNgoai, specNoi, specSan, specMat, specChild;
    private ShiftType lt01, lt02, lt03, lt04;
    private SchedulePeriod period;

    @BeforeEach
    void setUp() {
        analyzer = new SchedulingFeasibilityAnalyzer(
                staffRepository, requirementRepository,
                leaveRequestRepository, compensationDayRepository,
                holidayRepository, algorithmConfigService);

        // Specialties
        specNgoai = makeSpecialty(1, "Ngoại");
        specNoi = makeSpecialty(2, "Nội");
        specSan = makeSpecialty(3, "Sản");
        specMat = makeSpecialty(4, "Mắt");
        specChild = makeSpecialty(5, "Nhi");

        // Staff
        staffNgoai = makeStaff(101, "BS. A", specNgoai, true);
        staffNoi = makeStaff(102, "BS. B", specNoi, true);
        staffSan = makeStaff(103, "BS. C", specSan, true);
        staffMat = makeStaff(104, "BS. D", specMat, true);
        staffChild1 = makeStaff(105, "BS. E", specChild, true);
        staffChild2 = makeStaff(106, "BS. F", specChild, true);

        // Shift types
        lt01 = makeShiftType("L01", "Trực 24/24");
        lt02 = makeShiftType("L02", "Thông tầm");
        lt03 = makeShiftType("L03", "PK Dịch vụ");
        lt04 = makeShiftType("L04", "PK Chuyên gia");

        period = makePeriod(1);
    }

    // ── Helper factories ────────────────────────────────────────────────────────

    private Specialty makeSpecialty(int id, String name) {
        Specialty s = new Specialty();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private Staff makeStaff(int id, String name, Specialty specialty, boolean active) {
        Staff s = new Staff();
        s.setId(id);
        s.setFullName(name);
        s.setSpecialty(specialty);
        s.setIsActive(active);
        return s;
    }

    private ShiftType makeShiftType(String id, String name) {
        ShiftType st = new ShiftType();
        st.setId(id);
        st.setName(name);
        return st;
    }

    private SchedulePeriod makePeriod(int id) {
        SchedulePeriod p = new SchedulePeriod();
        p.setId(id);
        return p;
    }

    private ShiftRequirement makeReq(int id, SchedulePeriod period, ShiftType shiftType,
                                    Specialty specialty, LocalDate date, int required) {
        ShiftRequirement r = new ShiftRequirement();
        r.setId(id);
        r.setPeriod(period);
        r.setShiftType(shiftType);
        r.setSpecialty(specialty);
        r.setWorkDate(date);
        r.setRequiredStaffCount(required);
        return r;
    }

    private LeaveRequest makeLeave(int id, Staff staff, LocalDate start, LocalDate end) {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(id);
        lr.setStaff(staff);
        lr.setStartDate(start);
        lr.setEndDate(end);
        return lr;
    }

    private CompensationDay makeCompDay(int id, Staff staff, LocalDate compDate) {
        CompensationDay cd = new CompensationDay();
        cd.setId(id);
        cd.setStaff(staff);
        cd.setCompensationDate(compDate);
        return cd;
    }

    // ── Stub helpers ────────────────────────────────────────────────────────────

    private void stubActiveStaff(List<Staff> staff) {
        when(staffRepository.findByIsActiveTrue()).thenReturn(staff);
        when(staffRepository.findByIsActiveTrueWithSpecialty()).thenReturn(staff);
    }

    private void stubRequirements(List<ShiftRequirement> reqs) {
        when(requirementRepository.findByPeriodId(anyInt())).thenReturn(reqs);
    }

    private void stubLeaves(List<LeaveRequest> leaves) {
        when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(leaves);
    }

    private void stubCompDays(List<CompensationDay> compDays) {
        when(compensationDayRepository.findInRange(any(), any())).thenReturn(compDays);
    }

    private void stubHolidays(List<Holiday> holidays) {
        when(holidayRepository.findActiveHolidaysBetween(any(), any())).thenReturn(holidays);
    }

    private void stubAutoGenConfig(boolean crossEnabled, List<String> allowed) {
        var cfg = com.hospital.scheduler.algorithm.AutoGenConfig.builder()
                .l04CrossSpecialty(crossEnabled)
                .l04CrossSpecialtyRatio(0.3f)
                .l04AllowedSpecialties(allowed != null ? allowed : List.of())
                .l04BalanceStrategy("FAIR_DISTRIBUTE")
                .build();
        when(algorithmConfigService.getAutoGenConfig()).thenReturn(Optional.of(cfg));
    }

    private void stubNoAutoGenConfig() {
        when(algorithmConfigService.getAutoGenConfig()).thenReturn(Optional.empty());
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("analyzeFeasibility — basic feasibility")
    class BasicFeasibility {

        @Test
        @DisplayName("empty requirements → not feasible, no analysis")
        void emptyRequirements() {
            stubActiveStaff(List.of(staffNgoai));
            stubRequirements(Collections.emptyList());
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());

            var report = analyzer.analyzeFeasibility(1);

            assertFalse(report.feasible());
            assertEquals(0, report.totalDays());
            assertEquals(0, report.feasibleDays());
            assertEquals(0, report.understaffedDays());
            assertTrue(report.warnings().get(0).contains("Không có yêu cầu"));
        }

        @Test
        @DisplayName("all days fully staffed → 100% coverage")
        void fullyStaffed() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 1),
                    makeReq(2, period, lt01, null, d2, 1)
            );
            // 5 staff, each day requires 1 → always enough
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan, staffMat, staffChild1));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            assertTrue(report.feasible());
            assertEquals(2, report.totalDays());
            assertEquals(2, report.feasibleDays());
            assertEquals(0, report.understaffedDays());
            assertEquals(100.0, report.coverageRate(), 0.01);
        }

        @Test
        @DisplayName("one day understaffed → reports correctly")
        void oneDayUnderstaffed() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 3),
                    makeReq(2, period, lt01, null, d2, 1)
            );
            // d1 requires 3, d2 requires 1; only 2 staff available
            stubActiveStaff(List.of(staffNgoai, staffNoi));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            assertFalse(report.feasible());
            assertEquals(2, report.totalDays());
            assertEquals(1, report.feasibleDays());
            assertEquals(1, report.understaffedDays());
            // 50% coverage
            assertEquals(50.0, report.coverageRate(), 0.01);
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — L04 specialty matching")
    class L04SpecialtyMatching {

        @Test
        @DisplayName("L04 with 2 staff of required specialty → both eligible")
        void l04MatchingSpecialty() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            // L04 for Nhi specialty, requires 2
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt04, specChild, d1, 2)
            );
            // 5 total staff, 2 of them are Nhi
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffChild1, staffChild2, staffMat));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var dayAnalysis = report.dailyAnalysis().get(0);
            var l04 = dayAnalysis.shiftTypes().get("L04");
            // Only staffChild1 (Nhi) and staffChild2 (Nhi) are eligible
            assertEquals(2, l04.eligibleStaff());
            assertEquals(2, l04.required());
            assertFalse(l04.isUnderstaffed());
        }

        @Test
        @DisplayName("L04 with only 1 staff of required specialty → understaffed")
        void l04Shortage() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            // L04 for Mắt specialty, requires 2
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt04, specMat, d1, 2)
            );
            // Only 1 Mắt staff
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffMat, staffChild1));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var dayAnalysis = report.dailyAnalysis().get(0);
            var l04 = dayAnalysis.shiftTypes().get("L04");
            assertEquals(1, l04.eligibleStaff());
            assertEquals(2, l04.required());
            assertTrue(l04.isUnderstaffed());
            assertTrue(l04.issue().contains("Thiếu"));
        }

        @Test
        @DisplayName("L04 with cross-specialty enabled → other specialty staff become eligible")
        void l04CrossSpecialty() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            // L04 for Nhi specialty, requires 3
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt04, specChild, d1, 3)
            );
            // Only 2 Nhi staff, cross-specialty will add Ngoại, Nội
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffChild1, staffChild2));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            // Cross-specialty: allow Ngoại and Nội
            stubAutoGenConfig(true, List.of("Ngoại", "Nội"));

            var report = analyzer.analyzeFeasibility(1);

            var dayAnalysis = report.dailyAnalysis().get(0);
            var l04 = dayAnalysis.shiftTypes().get("L04");
            // 2 Nhi + 1 Ngoại + 1 Nội = 4 eligible (cross-specialty)
            assertEquals(4, l04.eligibleStaff());
            assertEquals(3, l04.required());
            assertFalse(l04.isUnderstaffed());
        }

        @Test
        @DisplayName("L04 cross-specialty when staff not in allowlist → excluded")
        void l04CrossSpecialtyRestrictive() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt04, specChild, d1, 4)
            );
            // 5 staff, cross-specialty only allows Ngoại
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan, staffChild1, staffChild2));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(true, List.of("Ngoại")); // Only Ngoại in allowlist

            var report = analyzer.analyzeFeasibility(1);

            var dayAnalysis = report.dailyAnalysis().get(0);
            var l04 = dayAnalysis.shiftTypes().get("L04");
            // Only 1 Ngoại (cross) + 2 Nhi (match) = 3 eligible, need 4
            assertEquals(3, l04.eligibleStaff());
            assertEquals(4, l04.required());
            assertTrue(l04.isUnderstaffed());
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — leave and compensation filtering")
    class LeaveAndCompensation {

        @Test
        @DisplayName("staff on leave → excluded from eligible count")
        void staffOnLeave() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 3)
            );
            // 3 staff, but 1 is on leave
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(List.of(makeLeave(1, staffNgoai, d1, d1)));
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var l01 = report.dailyAnalysis().get(0).shiftTypes().get("L01");
            assertEquals(2, l01.eligibleStaff());  // 2 remaining
            assertEquals(1, l01.onLeave());        // 1 on leave
        }

        @Test
        @DisplayName("staff on compensation day → excluded from eligible count")
        void staffOnCompensation() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 3)
            );
            // 3 staff, but 1 is on compensation
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(List.of(makeCompDay(1, staffNoi, d1)));
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var l01 = report.dailyAnalysis().get(0).shiftTypes().get("L01");
            assertEquals(2, l01.eligibleStaff());  // 2 remaining
            assertEquals(1, l01.onCompensation()); // 1 on comp
        }

        @Test
        @DisplayName("staff on leave on different day → not excluded on other days")
        void leaveOnlyOnOneDay() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 3),
                    makeReq(2, period, lt01, null, d2, 3)
            );
            // Staff on leave only on d1
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(List.of(makeLeave(1, staffNgoai, d1, d1)));
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            // d1: 2 eligible (staffNgoai on leave)
            var d1Analysis = report.dailyAnalysis().get(0);
            assertEquals(2, d1Analysis.shiftTypes().get("L01").eligibleStaff());

            // d2: 3 eligible (no leave)
            var d2Analysis = report.dailyAnalysis().get(1);
            assertEquals(3, d2Analysis.shiftTypes().get("L01").eligibleStaff());
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — holidays")
    class Holidays {

        @Test
        @DisplayName("holiday date → skipped, no analysis entries")
        void holidaySkipped() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);
            LocalDate holiday = LocalDate.of(2026, 7, 3);  // distinct from d1, d2

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 1),
                    makeReq(2, period, lt01, null, d2, 1),
                    makeReq(3, period, lt01, null, d3, 1)
            );

            Holiday h = new Holiday();
            h.setHolidayDate(holiday);
            h.setName("Independence Day");
            h.setYear(2026);
            h.setIsActive(true);

            stubActiveStaff(List.of(staffNgoai));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(List.of(h));
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            // 3 days total (d1, d2, d3=holiday)
            assertEquals(3, report.totalDays());
            // d3 (holiday) has empty shiftTypes map
            var holidayDay = report.dailyAnalysis().stream()
                    .filter(d -> d.date().equals(holiday))
                    .findFirst().orElseThrow();
            assertTrue(holidayDay.shiftTypes().isEmpty());
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — L01/L02/L03 eligible count")
    class NonL04EligibleCount {

        @Test
        @DisplayName("L01 eligible = all active staff (no specialty constraint)")
        void l01EligibleAllStaff() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 5)
            );
            // 5 staff, all different specialties
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan, staffMat, staffChild1));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var l01 = report.dailyAnalysis().get(0).shiftTypes().get("L01");
            assertEquals(5, l01.eligibleStaff());
            assertFalse(l01.isUnderstaffed());
        }

        @Test
        @DisplayName("L02 eligible = all active staff")
        void l02EligibleAllStaff() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt02, null, d1, 3)
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var l02 = report.dailyAnalysis().get(0).shiftTypes().get("L02");
            assertEquals(3, l02.eligibleStaff());
            assertFalse(l02.isUnderstaffed());
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — availability summary")
    class AvailabilitySummary {

        @Test
        @DisplayName("availabilityByShiftType shows correct min/max/avg")
        void availabilitySummaryCorrect() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);

            // L04 Nhi: requires 2 staff
            // Day 1: 2 Nhi staff
            // Day 2: 1 Nhi staff (1 on leave)
            // Day 3: 0 Nhi staff (both on leave)
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt04, specChild, d1, 2),
                    makeReq(2, period, lt04, specChild, d2, 2),
                    makeReq(3, period, lt04, specChild, d3, 2)
            );
            stubActiveStaff(List.of(staffChild1, staffChild2));
            stubRequirements(reqs);
            stubLeaves(List.of(
                    makeLeave(1, staffChild2, d2, d3) // staffChild2 on leave d2-d3
            ));
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var summary = report.availabilityByShiftType().get("L04");
            assertNotNull(summary);
            assertEquals(2, summary.totalActiveStaff());
            assertEquals(1, summary.minDailyEligible());   // d3: 0 → 1 (divided by totalActive=2)
            assertEquals(2, summary.maxDailyEligible());    // d1: 2/2=1 → 2
            // avg of [1, 1, 0] = 0.67 → times 2 = 1.33
            assertTrue(summary.averageDailyEligible() > 0.9 && summary.averageDailyEligible() < 1.5);
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — understaffedDays counter")
    class UnderstaffedDaysCounter {

        @Test
        @DisplayName("understaffedDays = count of distinct understaffed calendar days")
        void understaffedDaysIsDayCount() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);

            // d1: L01 requires 5 (have 5) OK; L04 requires 2 (have 1: staffChild1 is Nhi) UNDERSTAFFED
            // d2: same UNDERSTAFFED
            // d3: same UNDERSTAFFED
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 5),
                    makeReq(2, period, lt04, specChild, d1, 2),  // need 2 Nhi staff, have only 1
                    makeReq(3, period, lt01, null, d2, 5),
                    makeReq(4, period, lt04, specChild, d2, 2),
                    makeReq(5, period, lt01, null, d3, 5),
                    makeReq(6, period, lt04, specChild, d3, 2)
            );
            // 5 staff, none are Nhi specialty
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan, staffMat, staffChild1));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            // L01: always 5/5 → OK
            // L04: always 0/1 → understaffed every day
            // BUT understaffedDays = count of days with ANY understaffed shift = 3
            assertEquals(3, report.understaffedDays());
            // feasibleDays = days with NO understaffed = 0
            assertEquals(0, report.feasibleDays());
            assertEquals(0.0, report.coverageRate(), 0.01);
        }
    }

    @Nested
    @DisplayName("analyzeFeasibility — warnings and recommendations")
    class WarningsAndRecommendations {

        @Test
        @DisplayName("coverage < 50% → strong warning")
        void lowCoverageWarning() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 5),
                    makeReq(2, period, lt01, null, d2, 5)
            );
            // 1 staff, need 5 each day → 0% coverage both days
            stubActiveStaff(List.of(staffNgoai));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubNoAutoGenConfig();

            var report = analyzer.analyzeFeasibility(1);

            assertTrue(report.warnings().stream().anyMatch(w -> w.contains("0%") || w.contains("50%")), "Should have warning for 0% coverage: " + report.warnings());
        }

        @Test
        @DisplayName("L04 shortage + cross-specialty off → recommends enabling cross-specialty")
        void l04ShortageRecommendsCrossSpecialty() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt04, specChild, d1, 5)
            );
            // Only 1 Nhi staff
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffChild1));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of()); // cross-specialty OFF

            var report = analyzer.analyzeFeasibility(1);

            assertTrue(report.recommendations().stream()
                    .anyMatch(r -> r.contains("cross-specialty") && r.contains("PK Chuyên gia")));
        }

        @Test
        @DisplayName("no shortage + coverage >= 80% → positive recommendation")
        void positiveRecommendation() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 2),
                    makeReq(2, period, lt01, null, d2, 2)
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            assertTrue(report.feasible());
            assertTrue(report.recommendations().stream()
                    .anyMatch(r -> r.contains("khả thi") || r.contains("hiện tại")));
        }

        @Test
        @DisplayName("eligible == required every day → no-buffer warning for that shift type")
        void noBufferWarning() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);

            // 3 staff Ngoại, require 3 every day → eligible == required (no buffer)
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 3),
                    makeReq(2, period, lt01, null, d2, 3),
                    makeReq(3, period, lt01, null, d3, 3)
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            // coverage 100%, feasible, but no buffer
            assertTrue(report.feasible());
            assertEquals(100.0, report.coverageRate(), 0.01);
            assertTrue(report.warnings().stream()
                    .anyMatch(w -> w.contains("KHÔNG có dự phòng")),
                    "Should warn about no buffer: " + report.warnings());
            assertTrue(report.recommendations().stream()
                    .anyMatch(r -> r.contains("buffer dự phòng")),
                    "Should recommend adding buffer: " + report.recommendations());
        }

        @Test
        @DisplayName("eligible > required every day → no no-buffer warning")
        void withBufferNoWarning() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            // 5 staff, require 2 every day → eligible > required (has buffer)
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 2),
                    makeReq(2, period, lt01, null, d2, 2)
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan, staffMat, staffChild1));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            assertTrue(report.feasible());
            assertFalse(report.warnings().stream()
                    .anyMatch(w -> w.contains("dự phòng")),
                    "Should NOT warn when eligible > required: " + report.warnings());
        }

        @Test
        @DisplayName("almost all days no buffer (N-1) → high risk warning")
        void almostNoBufferWarning() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);

            // Day 1: eligible == required (no buffer), Day 2: no buffer, Day 3: has buffer
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 2),  // have 3 staff → buffer
                    makeReq(2, period, lt01, null, d2, 3),  // have 3 staff → no buffer
                    makeReq(3, period, lt01, null, d3, 3)   // have 3 staff → no buffer
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            // 2 out of 3 days have no buffer → high risk
            assertTrue(report.warnings().stream()
                    .anyMatch(w -> w.contains("dự phòng") && w.contains("2/3")),
                    "Should warn about 2/3 days no buffer: " + report.warnings());
        }

        @Test
        @DisplayName("bufferRisk = HIGH when every day has no buffer")
        void bufferRiskHigh() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);

            // 3 staff, require 3 every day → eligible == required, no buffer all days
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 3),
                    makeReq(2, period, lt01, null, d2, 3),
                    makeReq(3, period, lt01, null, d3, 3)
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var l01Summary = report.availabilityByShiftType().get("L01");
            assertNotNull(l01Summary);
            assertEquals(SchedulingFeasibilityAnalyzer.BufferRisk.HIGH, l01Summary.bufferRisk());
            assertEquals(3, l01Summary.noBufferDays());
            assertEquals(3, l01Summary.totalDays());
            assertEquals(0, l01Summary.bufferMin());
        }

        @Test
        @DisplayName("backup staff = staff on leave/comp that could cover")
        void backupStaffShown() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);

            // 2 staff active, 1 on leave — backup should show the staff on leave
            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 2),
                    makeReq(2, period, lt01, null, d2, 2)
            );
            stubActiveStaff(List.of(staffNgoai, staffNoi, staffSan));
            stubRequirements(reqs);
            // staffSan on leave for all days
            stubLeaves(List.of(makeLeave(3, staffSan, d1, d2)));
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var report = analyzer.analyzeFeasibility(1);

            var l01Summary = report.availabilityByShiftType().get("L01");
            assertNotNull(l01Summary);
            // staffSan is on leave → should appear in backup list
            assertTrue(l01Summary.backups().stream()
                    .anyMatch(b -> b.staffName().contains("BS. C")),
                    "Backup list should contain staffSan who is on leave: " + l01Summary.backups());
        }
    }

    @Nested
    @DisplayName("isPeriodFeasible and getUnderstaffedDates")
    class ConvenienceMethods {

        @Test
        @DisplayName("isPeriodFeasible delegates to report")
        void delegates() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 1)
            );
            stubActiveStaff(List.of(staffNgoai));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            assertTrue(analyzer.isPeriodFeasible(1));
        }

        @Test
        @DisplayName("getUnderstaffedDates returns correct dates")
        void understaffedDates() {
            LocalDate d1 = LocalDate.of(2026, 7, 1);
            LocalDate d2 = LocalDate.of(2026, 7, 2);
            LocalDate d3 = LocalDate.of(2026, 7, 3);

            List<ShiftRequirement> reqs = List.of(
                    makeReq(1, period, lt01, null, d1, 5),
                    makeReq(2, period, lt01, null, d2, 5),
                    makeReq(3, period, lt01, null, d3, 5)
            );
            // 1 staff, need 5 each day → d1, d2, d3 all understaffed
            stubActiveStaff(List.of(staffNgoai));
            stubRequirements(reqs);
            stubLeaves(Collections.emptyList());
            stubCompDays(Collections.emptyList());
            stubHolidays(Collections.emptyList());
            stubAutoGenConfig(false, List.of());

            var dates = analyzer.getUnderstaffedDates(1);

            assertEquals(3, dates.size());
            assertTrue(dates.contains(d1));
            assertTrue(dates.contains(d2));
            assertTrue(dates.contains(d3));
        }
    }
}
