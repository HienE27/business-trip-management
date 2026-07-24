package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO;
import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.AlgorithmMetrics;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.AlgorithmMetricsRepository;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full E2E integration test for {@link AutoSchedulingService}.
 *
 * <p>Tests the full auto-scheduling flow end-to-end against a real MySQL 8
 * database (existing container at {@code localhost:3306/test_scheduler}):
 * seed staff + period + config, run the scheduler, verify schedules are
 * generated and written to DB, assert no business-rule violations
 * (BR-01..BR-07) are produced.
 *
 * <p>Each test is {@link Transactional @Transactional} and rolls back after
 * completion, leaving no side effects for subsequent tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AutoSchedulingService — E2E integration")
class AutoSchedulingServiceIntegrationTest {

    @Autowired AutoSchedulingService autoSchedulingService;
    @Autowired AlgorithmConfigService algorithmConfigService;
    @Autowired SchedulePeriodRepository periodRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired ShiftRequirementRepository requirementRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired SpecialtyRepository specialtyRepository;
    @Autowired ShiftTypeRepository shiftTypeRepository;
    @Autowired AlgorithmMetricsRepository metricsRepository;
    @Autowired CompensationDayRepository compensationDayRepository;

    /** Số lượng staff tạo trong mỗi test. */
    private static final int STAFF_COUNT = 10;

    // ── Seed helpers ─────────────────────────────────────────────────

    private List<ShiftType> seedShiftTypes() {
        ShiftType l01 = ShiftType.builder().id("L01").name("Trực 24/24")
                .startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(7, 30).plusHours(24))
                .isOvernight(true).fatigueScore(5).isActive(true).build();
        ShiftType l02 = ShiftType.builder().id("L02").name("Thông tầm")
                .startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(11, 0))
                .isOvernight(false).fatigueScore(2).isActive(true).build();
        ShiftType l03 = ShiftType.builder().id("L03").name("Phòng khám dịch vụ")
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(16, 30))
                .isOvernight(false).fatigueScore(1).isActive(true).build();
        ShiftType l04 = ShiftType.builder().id("L04").name("Phòng khám chuyên gia")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .isOvernight(false).fatigueScore(1).isActive(true).build();
        return shiftTypeRepository.saveAll(List.of(l01, l02, l03, l04));
    }

    private List<Specialty> seedSpecialties() {
        Specialty ngoai = Specialty.builder().name("Ngoại").isActive(true).build();
        Specialty noi = Specialty.builder().name("Nội").isActive(true).build();
        return specialtyRepository.saveAll(List.of(ngoai, noi));
    }

    private List<Staff> seedStaff(int count, List<Specialty> specialties) {
        List<Staff> staffList = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Specialty spec = specialties.get(i % specialties.size());
            staffList.add(Staff.builder()
                    .staffCode("STF" + String.format("%03d", i))
                    .username("staff" + i)
                    .passwordHash("$2a$10$placeholder") // not used in scheduling
                    .fullName("Nhân viên " + i)
                    .specialty(spec)
                    .maxShiftsPerMonth(30)
                    .build());
        }
        return staffRepository.saveAll(staffList);
    }

    private SchedulePeriod seedDraftPeriod(LocalDate start, LocalDate end) {
        return periodRepository.save(SchedulePeriod.builder()
                .periodName("Kỳ test " + start + " - " + end)
                .startDate(start)
                .endDate(end)
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build());
    }

    private AutoGenConfig defaultAutoGenConfig() {
        // Slightly lower requirements so 10 staff × 30 days is feasible
        return new AutoGenConfig(
                true,
                2, 2, 2, 1,  // l01-l04 minPerDay
                2, 2, 2, 2,  // l01-l04 maxPerDay
                3, 3, 3, 3,  // l01-l04 minPerWeek
                6, 6, 6, 6,  // l01-l04 maxPerWeek
                "SKIP", List.of(),    // holidayMode, removedShiftTypes
                false, 0.3f, List.of(), // l04CrossSpecialty, ratio, allowed
                List.of("Ngoại", "Nội"), // l01AllowedSpecialties
                List.of("Ngoại", "Nội"), // l02AllowedSpecialties
                List.of("Ngoại", "Nội"), // l03AllowedSpecialties
                5, 5, 5, 5,      // target per month
                "FAIR_DISTRIBUTE" // l04BalanceStrategy
        );
    }

    /** Shared setup: shift types + specialties + staff + period + config. */
    @BeforeEach
    void setUp() {
        seedShiftTypes();
        List<Specialty> specialties = seedSpecialties();
        seedStaff(STAFF_COUNT, specialties);
        // No period seeded here — each test seeds its own period
    }

    // ── Business-rule assertion helpers ──────────────────────────────

    /** BR-01: L01 và L02 không được gán cho cùng một staff trong cùng ngày. */
    private static void assertNoL01L02SameDay(List<Schedule> schedules) {
        Map<String, Set<String>> staffDayTypes = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStaff().getId() + "|" + s.getWorkDate(),
                        Collectors.mapping(s -> s.getShiftType().getId(), Collectors.toSet())));
        for (var entry : staffDayTypes.entrySet()) {
            boolean hasL01 = entry.getValue().contains("L01");
            boolean hasL02 = entry.getValue().contains("L02");
            assertThat(hasL01 && hasL02)
                    .as("BR-01: staffId=%s có cả L01 và L02 cùng ngày", entry.getKey())
                    .isFalse();
        }
    }

    /** BR-02: L03 và L04 không được gán cho cùng một staff trong cùng ngày. */
    private static void assertNoL03L04SameDay(List<Schedule> schedules) {
        Map<String, Set<String>> staffDayTypes = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStaff().getId() + "|" + s.getWorkDate(),
                        Collectors.mapping(s -> s.getShiftType().getId(), Collectors.toSet())));
        for (var entry : staffDayTypes.entrySet()) {
            boolean hasL03 = entry.getValue().contains("L03");
            boolean hasL04 = entry.getValue().contains("L04");
            assertThat(hasL03 && hasL04)
                    .as("BR-02: staffId=%s có cả L03 và L04 cùng ngày", entry.getKey())
                    .isFalse();
        }
    }

    /** BR-03: L01 sau 7h30 -> không gán L02/L03/L04 cùng ngày (L01 occupies full day). */
    private static void assertNoL01WithOtherShifts(List<Schedule> schedules) {
        Map<String, Set<String>> staffDayTypes = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStaff().getId() + "|" + s.getWorkDate(),
                        Collectors.mapping(s -> s.getShiftType().getId(), Collectors.toSet())));
        for (var entry : staffDayTypes.entrySet()) {
            if (entry.getValue().contains("L01")) {
                long otherCount = entry.getValue().stream()
                        .filter(t -> !"L01".equals(t)).count();
                assertThat(otherCount)
                        .as("BR-03: staffId=%s L01 + %d other shifts cùng ngày", entry.getKey(), otherCount)
                        .isZero();
            }
        }
    }

    /** BR-04: L01 không được gán cho cùng staff trong 2 ngày liên tiếp. */
    private static void assertNoConsecutiveL01(List<Schedule> schedules) {
        Map<Integer, List<LocalDate>> staffL01Dates = schedules.stream()
                .filter(s -> "L01".equals(s.getShiftType().getId()))
                .collect(Collectors.groupingBy(
                        s -> s.getStaff().getId(),
                        Collectors.mapping(Schedule::getWorkDate, Collectors.toList())));
        for (var entry : staffL01Dates.entrySet()) {
            List<LocalDate> dates = entry.getValue().stream().sorted().toList();
            for (int i = 1; i < dates.size(); i++) {
                long diff = dates.get(i).toEpochDay() - dates.get(i - 1).toEpochDay();
                assertThat(diff)
                        .as("BR-04: staffId=%s L01 ngày %s và %s cách nhau %d ngày (< 2)",
                                entry.getKey(), dates.get(i - 1), dates.get(i), diff)
                        .isGreaterThanOrEqualTo(2);
            }
        }
    }

    /** BR-06: maxShiftsPerStaff không được vượt quá. */
    private static void assertMaxShiftsPerStaffRespected(List<Schedule> schedules, int maxCap) {
        Map<Integer, Long> countByStaff = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));
        for (var entry : countByStaff.entrySet()) {
            assertThat(entry.getValue())
                    .as("BR-06: staffId=%d có %d schedules > %d cap", entry.getKey(), entry.getValue(), maxCap)
                    .isLessThanOrEqualTo(maxCap);
        }
    }

    // ── Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auto-schedule ENHANCED_GREEDY → sinh lịch, ghi DB, không violate BR")
    void autoSchedule_enhancedGreedy_savesToDbAndRespectsConstraints() {
        // Seed 30-day draft period
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // Save AutoGenConfig (runtime config uses defaults from DB / AlgorithmConfigService)
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        // Run scheduler
        AutoScheduleResponse response = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .overwriteExisting(true)
                        .build());

        // Assert response
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);
        assertThat(response.getPeriodId()).isEqualTo(period.getId());

        // Assert schedules written to DB
        List<Schedule> schedules = scheduleRepository.findByPeriodId(period.getId());
        assertThat(schedules).hasSize(response.getTotalSchedulesCreated());

        // Assert business rules (runtime defaults: maxShiftsPerStaff=12)
        assertNoL01L02SameDay(schedules);
        assertNoL03L04SameDay(schedules);
        assertNoL01WithOtherShifts(schedules);
        assertNoConsecutiveL01(schedules);
        assertMaxShiftsPerStaffRespected(schedules, 12);
    }

    @Test
    @DisplayName("POST /auto-schedule CP_SAT → sinh lịch, ghi DB, không violate BR")
    void autoSchedule_cpSat_savesToDbAndRespectsConstraints() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        AutoScheduleResponse response = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("CP_SAT")
                        .overwriteExisting(true)
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);

        List<Schedule> schedules = scheduleRepository.findByPeriodId(period.getId());
        assertThat(schedules).hasSize(response.getTotalSchedulesCreated());

        assertNoL01L02SameDay(schedules);
        assertNoL03L04SameDay(schedules);
        assertNoConsecutiveL01(schedules);
        assertMaxShiftsPerStaffRespected(schedules, 12);
    }

    @Test
    @DisplayName("POST /auto-schedule BEAM_SEARCH → sinh lịch")
    void autoSchedule_beamSearch_savesToDb() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        AutoScheduleResponse response = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("BEAM_SEARCH")
                        .overwriteExisting(true)
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);
    }

    @Test
    @DisplayName("POST /auto-schedule RANDOM_RESTART_HC → sinh lịch")
    void autoSchedule_randomRestartHC_savesToDb() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        AutoScheduleResponse response = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("RANDOM_RESTART_HC")
                        .overwriteExisting(true)
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);
    }

    @Test
    @DisplayName("POST /auto-schedule SIMULATED_ANNEALING → sinh lịch")
    void autoSchedule_simulatedAnnealing_savesToDb() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        AutoScheduleResponse response = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("SIMULATED_ANNEALING")
                        .overwriteExisting(true)
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);
    }

    @Test
    @DisplayName("POST /auto-schedule/preview → trả lịch nhưng không ghi DB")
    void previewSchedule_doesNotPersist() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10)); // short period
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        AutoScheduleResponse response = autoSchedulingService.previewSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);

        // Verify DB has no schedules — preview should NOT persist
        List<Schedule> dbSchedules = scheduleRepository.findByPeriodId(period.getId());
        assertThat(dbSchedules).isEmpty();
    }

    @Test
    @DisplayName("POST /apply-preview → lưu edited preview vào DB (requirements pre-seeded)")
    void applyPreviewSchedule_savesEditedPlans() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 5));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        // Persist requirements via autoSchedule
        AutoScheduleResponse seedRun = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .overwriteExisting(true)
                        .build());
        assertThat(seedRun.isSuccess()).isTrue();

        List<ShiftRequirement> reqs = requirementRepository.findByPeriodId(period.getId());
        assertThat(reqs).isNotEmpty();

        // Map requirements by (workDate, shiftTypeId) to find requirementIds
        Map<String, ShiftRequirement> reqByKey = new java.util.HashMap<>();
        for (ShiftRequirement r : reqs) {
            String key = r.getWorkDate() + "|" + r.getShiftType().getId();
            // For L04 with multiple specialties, keep only the first one
            reqByKey.putIfAbsent(key, r);
        }

        List<Staff> staffList = staffRepository.findAll();
        assertThat(staffList).isNotEmpty();

        // Build items: avoid L01+L02 same day, L03+L04 same day, and L01 compensation day
        // Staff1: L03 8/3, L04 8/4, L02 8/5
        // Staff2: L04 8/3, L03 8/4, L01 8/5  (L01 comp day = 8/6, outside period)
        // Staff3: L01 8/3 (comp day 8/4 → no shift on 8/4), L02 8/5
        List<AutoScheduleApplyPreviewRequestDTO.PreviewScheduleItem> items = new java.util.ArrayList<>();
        // Staff1: L03 8/3, L04 8/4, L02 8/5
        items.add(buildItem(staffList.get(0), "2026-08-03", "L03", reqByKey));
        items.add(buildItem(staffList.get(0), "2026-08-04", "L04", reqByKey));
        items.add(buildItem(staffList.get(0), "2026-08-05", "L02", reqByKey));
        // Staff2: L04 8/3, L03 8/4, L01 8/5
        items.add(buildItem(staffList.get(1), "2026-08-03", "L04", reqByKey));
        items.add(buildItem(staffList.get(1), "2026-08-04", "L03", reqByKey));
        items.add(buildItem(staffList.get(1), "2026-08-05", "L01", reqByKey));
        // Staff3: L01 8/3 (comp day 8/4 → skip 8/4), L02 8/5
        if (staffList.size() > 2) {
            items.add(buildItem(staffList.get(2), "2026-08-03", "L01", reqByKey));
            items.add(buildItem(staffList.get(2), "2026-08-05", "L02", reqByKey));
        }

        // Apply preview items
        AutoScheduleResponse applied = autoSchedulingService.applyPreviewSchedule(
                AutoScheduleApplyPreviewRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .schedules(items)
                        .build());

        assertThat(applied.isSuccess()).isTrue();
        assertThat(applied.getTotalSchedulesCreated()).isEqualTo(items.size());

        List<Schedule> dbSchedules = scheduleRepository.findByPeriodId(period.getId());
        assertThat(dbSchedules).hasSize(items.size());
    }

    /** Helper: build a preview item only if the requirement exists. */
    private AutoScheduleApplyPreviewRequestDTO.PreviewScheduleItem buildItem(
            Staff staff, String workDate, String shiftTypeId,
            Map<String, ShiftRequirement> reqByKey) {
        ShiftRequirement req = reqByKey.get(workDate + "|" + shiftTypeId);
        if (req == null) return null;
        return AutoScheduleApplyPreviewRequestDTO.PreviewScheduleItem.builder()
                .staffId(staff.getId())
                .workDate(workDate)
                .shiftTypeId(shiftTypeId)
                .requirementId(req.getId())
                .build();
    }

    // ============================================================
    // Gate 1 — Persistence E2E: proposal/preview/cancel/confirm
    // ============================================================

    @Test
    @DisplayName("GATE 1a: Preview (save=false) — không ghi schedules, requirements, metrics vào DB")
    void previewSchedule_noDbWrites_comprehensive() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        // Pre-condition: DB empty for this period
        List<Schedule> beforeSchedules = scheduleRepository.findByPeriodId(period.getId());
        List<ShiftRequirement> beforeReqs = requirementRepository.findByPeriodId(period.getId());
        List<AlgorithmMetrics> beforeMetrics = metricsRepository.findByPeriodId(period.getId());
        List<CompensationDay> beforeCompDays = compensationDayRepository.findByPeriodId(period.getId());
        assertThat(beforeSchedules).isEmpty();
        assertThat(beforeMetrics).isEmpty();
        assertThat(beforeCompDays).isEmpty();
        // Requirements may exist if syncExistingRequirementsWithConfig was called on a prior run

        // Execute preview (save=false)
        AutoScheduleResponse response = autoSchedulingService.previewSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);

        // Post-condition: NO schedules, NO metrics, NO comp days in DB
        List<Schedule> afterSchedules = scheduleRepository.findByPeriodId(period.getId());
        List<AlgorithmMetrics> afterMetrics = metricsRepository.findByPeriodId(period.getId());
        List<CompensationDay> afterCompDays = compensationDayRepository.findByPeriodId(period.getId());
        assertThat(afterSchedules).as("Preview must NOT persist schedules").isEmpty();
        assertThat(afterMetrics).as("Preview must NOT persist metrics").isEmpty();
        assertThat(afterCompDays).as("Preview must NOT persist compensation days").isEmpty();

        // Requirements: preview generates in-memory only — no new rows in DB
        List<ShiftRequirement> afterReqs = requirementRepository.findByPeriodId(period.getId());
        assertThat(afterReqs).as("Preview must NOT add new requirement rows")
                .hasSize(beforeReqs.size());
    }

    @Test
    @DisplayName("GATE 1b: Preview with recommendedConfig — used in-memory, not persisted to config DB")
    void previewSchedule_withRecommendedConfig_inMemoryOnly() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));

        // Save original config to DB
        AutoGenConfig originalConfig = defaultAutoGenConfig();
        algorithmConfigService.saveAutoGenConfig(originalConfig);

        // Build a DIFFERENT recommended config (higher l01MaxPerDay)
        AutoGenConfig recommendedConfig = new AutoGenConfig(
                true,
                5, 5, 5, 3,   // l01-l04 minPerDay (higher)
                5, 5, 5, 4,   // l01-l04 maxPerDay (higher)
                3, 3, 3, 3,
                6, 6, 6, 6,
                "SKIP", List.of(),
                false, 0.3f, List.of(),
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội"),
                5, 5, 5, 5,
                "FAIR_DISTRIBUTE"
        );

        // Read config from DB before preview
        AutoGenConfig dbConfigBefore = algorithmConfigService.getAutoGenConfig().orElse(null);
        assertThat(dbConfigBefore).isNotNull();
        assertThat(dbConfigBefore.l01MaxPerDay()).isEqualTo(originalConfig.l01MaxPerDay());

        // Execute preview with recommendedConfig (save=false)
        AutoScheduleResponse response = autoSchedulingService.previewSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .recommendedConfig(recommendedConfig)
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);

        // Verify config in DB is UNCHANGED (still original, not recommended)
        AutoGenConfig dbConfigAfter = algorithmConfigService.getAutoGenConfig().orElse(null);
        assertThat(dbConfigAfter).isNotNull();
        assertThat(dbConfigAfter.l01MaxPerDay())
                .as("recommendedConfig must NOT be persisted to DB — config table unchanged")
                .isEqualTo(originalConfig.l01MaxPerDay());

        // Verify no schedules in DB
        List<Schedule> dbSchedules = scheduleRepository.findByPeriodId(period.getId());
        assertThat(dbSchedules).isEmpty();
    }

    @Test
    @DisplayName("GATE 1c: Cancel — không ghi DB, chỉ reset in-memory lock")
    void cancelSchedule_noDbWrites() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        // Verify initial DB state
        List<Schedule> beforeSchedules = scheduleRepository.findByPeriodId(period.getId());
        List<ShiftRequirement> beforeReqs = requirementRepository.findByPeriodId(period.getId());
        List<AlgorithmMetrics> beforeMetrics = metricsRepository.findByPeriodId(period.getId());

        assertThat(beforeSchedules).isEmpty();

        // Call cancel — only modifies in-memory lock state
        boolean released = autoSchedulingService.markLockStale(period.getId());
        // May return false if no lock was held (period never ran) — that's OK
        logCancelResult(released, period.getId());

        // Verify DB state is identical — no side effects
        List<Schedule> afterSchedules = scheduleRepository.findByPeriodId(period.getId());
        List<ShiftRequirement> afterReqs = requirementRepository.findByPeriodId(period.getId());
        List<AlgorithmMetrics> afterMetrics = metricsRepository.findByPeriodId(period.getId());

        assertThat(afterSchedules).as("Cancel must NOT create schedules").hasSameSizeAs(beforeSchedules);
        assertThat(afterReqs).as("Cancel must NOT create requirements").hasSameSizeAs(beforeReqs);
        assertThat(afterMetrics).as("Cancel must NOT create metrics").hasSameSizeAs(beforeMetrics);
    }

    private void logCancelResult(boolean released, int periodId) {
        // Helper to suppress unused-return-value warnings
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("Cancel period {}: released={}", periodId, released);
    }

    @Test
    @DisplayName("GATE 1d: Confirm (save=true) — ghi schedules, requirements, metrics vào DB")
    void autoSchedule_confirmed_savesToDb_comprehensive() {
        SchedulePeriod period = seedDraftPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        algorithmConfigService.saveAutoGenConfig(defaultAutoGenConfig());

        // Pre-condition: DB empty for this period
        List<Schedule> beforeSchedules = scheduleRepository.findByPeriodId(period.getId());
        List<AlgorithmMetrics> beforeMetrics = metricsRepository.findByPeriodId(period.getId());
        assertThat(beforeSchedules).isEmpty();
        assertThat(beforeMetrics).isEmpty();

        // Execute confirm (save=true)
        AutoScheduleResponse response = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(period.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .overwriteExisting(true)
                        .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalSchedulesCreated()).isGreaterThan(0);

        // Post-condition: schedules persisted
        List<Schedule> afterSchedules = scheduleRepository.findByPeriodId(period.getId());
        assertThat(afterSchedules).as("Confirm must persist schedules to DB")
                .hasSize(response.getTotalSchedulesCreated());

        // Requirements persisted (syncExistingRequirementsWithConfig + persistRequirementsIfTransient)
        List<ShiftRequirement> afterReqs = requirementRepository.findByPeriodId(period.getId());
        assertThat(afterReqs).as("Confirm must persist requirements to DB").isNotEmpty();

        // Metrics recorded
        List<AlgorithmMetrics> afterMetrics = metricsRepository.findByPeriodId(period.getId());
        assertThat(afterMetrics).as("Confirm must record metrics").isNotEmpty();

        // Compensation days created (if autoCompensation enabled — default in runtime config)
        List<CompensationDay> afterCompDays = compensationDayRepository.findByPeriodId(period.getId());
        // At least some L01 shifts → compensation days created
        boolean hasL01Shifts = afterSchedules.stream()
                .anyMatch(s -> "L01".equals(s.getShiftType().getId()));
        if (hasL01Shifts) {
            assertThat(afterCompDays).as("Confirm must create compensation days for L01 shifts")
                    .isNotEmpty();
        }

        // Verify business rules
        assertNoL01L02SameDay(afterSchedules);
        assertNoL03L04SameDay(afterSchedules);
        assertNoL01WithOtherShifts(afterSchedules);
        assertNoConsecutiveL01(afterSchedules);
        assertMaxShiftsPerStaffRespected(afterSchedules, 12);
    }
}
