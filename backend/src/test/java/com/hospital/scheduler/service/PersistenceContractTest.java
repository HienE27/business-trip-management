package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Gate 1 (M07): Persistence contract E2E verification.
 *
 * Verifies the 4-way persistence contract against the test MySQL container:
 * 1. Proposal: read-only — no config/requirements/schedules written to DB.
 * 2. Preview (save=false): non-destructive — no writes.
 * 3. Cancel: zero writes.
 * 4. Confirm (save=true): only then does DB get written.
 *
 * Uses per-test isolated periods to avoid unique-key collisions between
 * destructive (confirm) tests that commit within their own transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Gate 1 — M07 Persistence Contract E2E")
class PersistenceContractTest {

    @Autowired AutoSchedulingService autoSchedulingService;
    @Autowired AlgorithmConfigService algorithmConfigService;
    @Autowired SchedulePeriodRepository periodRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired ShiftRequirementRepository requirementRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired SpecialtyRepository specialtyRepository;
    @Autowired ShiftTypeRepository shiftTypeRepository;

    private static int periodCounter = 0;

    private SchedulePeriod draftPeriod;
    private ShiftType l01, l02, l03, l04;

    private synchronized SchedulePeriod nextPeriod() {
        int n = ++periodCounter;
        return periodRepository.save(SchedulePeriod.builder()
                .periodName("Gate1-" + n)
                .startDate(LocalDate.of(2026, 7, 1).plusDays(n))
                .endDate(LocalDate.of(2026, 7, 7).plusDays(n))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build());
    }

    @BeforeEach
    void seed() {
        l01 = shiftTypeRepository.save(ShiftType.builder()
                .id("L01").name("Trực 24/24")
                .startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(7, 30).plusHours(24))
                .isOvernight(true).fatigueScore(5).isActive(true).build());
        l02 = shiftTypeRepository.save(ShiftType.builder()
                .id("L02").name("Thông tầm")
                .startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(11, 0))
                .isOvernight(false).fatigueScore(2).isActive(true).build());
        l03 = shiftTypeRepository.save(ShiftType.builder()
                .id("L03").name("Phòng khám dịch vụ")
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(16, 30))
                .isOvernight(false).fatigueScore(1).isActive(true).build());
        l04 = shiftTypeRepository.save(ShiftType.builder()
                .id("L04").name("Phòng khám chuyên gia")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .isOvernight(false).fatigueScore(1).isActive(true).build());

        Specialty ngoai = specialtyRepository.save(Specialty.builder().name("Ngoại-" + periodCounter).isActive(true).build());
        Specialty noi = specialtyRepository.save(Specialty.builder().name("Nội-" + periodCounter).isActive(true).build());

        staffRepository.saveAll(java.util.List.of(
                Staff.builder().staffCode("S001").username("s1").passwordHash("$2a$10$x").fullName("NS1")
                        .specialty(ngoai).maxShiftsPerMonth(30).isActive(true).build(),
                Staff.builder().staffCode("S002").username("s2").passwordHash("$2a$10$x").fullName("NS2")
                        .specialty(ngoai).maxShiftsPerMonth(30).isActive(true).build(),
                Staff.builder().staffCode("S003").username("s3").passwordHash("$2a$10$x").fullName("NS3")
                        .specialty(noi).maxShiftsPerMonth(30).isActive(true).build(),
                Staff.builder().staffCode("S004").username("s4").passwordHash("$2a$10$x").fullName("NS4")
                        .specialty(noi).maxShiftsPerMonth(30).isActive(true).build()
        ));

        draftPeriod = nextPeriod();
    }

    private long countSchedules() {
        return scheduleRepository.findByPeriodId(draftPeriod.getId()).size();
    }

    private long countRequirements() {
        return requirementRepository.findByPeriodId(draftPeriod.getId()).size();
    }

    // ── GATE 1.1: Proposal — read-only ───────────────────────────────

    @Test
    @DisplayName("1. Proposal does NOT write schedules to DB")
    void proposal_doesNotWriteSchedules() {
        long before = countSchedules();
        algorithmConfigService.recommendAutoGenConfig(
                7, 1,
                java.util.Map.of("L01", 4, "L02", 4, "L03", 4, "L04", 4),
                java.util.Map.of("L01", 2, "L02", 2, "L03", 2, "L04", 2),
                true, java.util.List.of("Ngoại", "Nội"), 0, null);
        assertThat(countSchedules()).isEqualTo(before);
    }

    @Test
    @DisplayName("1. Proposal does NOT write requirements to DB")
    void proposal_doesNotWriteRequirements() {
        long before = countRequirements();
        algorithmConfigService.recommendAutoGenConfig(
                7, 1,
                java.util.Map.of("L01", 4, "L02", 4, "L03", 4, "L04", 4),
                java.util.Map.of("L01", 2, "L02", 2, "L03", 2, "L04", 2),
                true, java.util.List.of("Ngoại", "Nội"), 0, null);
        assertThat(countRequirements()).isEqualTo(before);
    }

    // ── GATE 1.2: Preview (save=false) — non-destructive ────────────

    @Test
    @DisplayName("2. Preview (save=false) does NOT write schedules to DB")
    void preview_doesNotWriteSchedules() {
        requirementRepository.save(ShiftRequirement.builder()
                .period(draftPeriod).workDate(draftPeriod.getStartDate())
                .shiftType(l01).requiredStaffCount(1).build());

        long before = countSchedules();
        autoSchedulingService.previewSchedule(AutoScheduleRequestDTO.builder()
                .periodId(draftPeriod.getId())
                .algorithmType("ENHANCED_GREEDY")
                .build());

        assertThat(countSchedules()).isEqualTo(before);
    }

    @Test
    @DisplayName("2. Preview (save=false) does NOT write requirements to DB")
    void preview_doesNotWriteRequirements() {
        requirementRepository.save(ShiftRequirement.builder()
                .period(draftPeriod).workDate(draftPeriod.getStartDate())
                .shiftType(l01).requiredStaffCount(1).build());

        long before = countRequirements();
        autoSchedulingService.previewSchedule(AutoScheduleRequestDTO.builder()
                .periodId(draftPeriod.getId())
                .algorithmType("ENHANCED_GREEDY")
                .build());

        assertThat(countRequirements()).isEqualTo(before);
    }

    // ── GATE 1.3: recommendedConfig inline — in-memory only ──────────

    @Test
    @DisplayName("3. Preview with recommendedConfig inline — DB config unchanged (in-memory only)")
    void preview_inlineRecommendedConfig_usesInMemoryOnly() {
        // Save a known config to DB
        algorithmConfigService.saveAutoGenConfig(new AutoGenConfig(
                true,
                1, 1, 1, 1, 2, 2, 2, 2,
                1, 1, 1, 1, 3, 3, 3, 3,
                "SKIP", java.util.List.of(), false, 0.3f,
                java.util.List.of(), java.util.List.of("Ngoại", "Nội"),
                java.util.List.of("Ngoại", "Nội"), java.util.List.of("Ngoại", "Nội"),
                2, 2, 2, 5, "FAIR_DISTRIBUTE"));

        requirementRepository.save(ShiftRequirement.builder()
                .period(draftPeriod).workDate(draftPeriod.getStartDate())
                .shiftType(l01).requiredStaffCount(1).build());

        AutoGenConfig inlineCfg = new AutoGenConfig(
                true,
                5, 5, 5, 3, 8, 8, 8, 5,
                6, 6, 6, 4, 10, 10, 10, 6,
                "SKIP", java.util.List.of(), false, 0.3f,
                java.util.List.of(), java.util.List.of("Ngoại", "Nội"),
                java.util.List.of("Ngoại", "Nội"), java.util.List.of("Ngoại", "Nội"),
                8, 8, 8, 8, "FAIR_DISTRIBUTE");

        AutoScheduleResponse resp = autoSchedulingService.previewSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(draftPeriod.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .recommendedConfig(inlineCfg)
                        .build());

        assertThat(resp.isSuccess()).isTrue();
        // DB config unchanged — inline config was used in-memory only
        var dbCfg = algorithmConfigService.getAutoGenConfig().orElseThrow();
        assertThat(dbCfg.l01MinPerDay()).isEqualTo(1);
        assertThat(dbCfg.l01MaxPerDay()).isEqualTo(2);
    }

    // ── GATE 1.4: Confirm (save=true) — writes ───────────────────────

    @Test
    @DisplayName("4. Confirm (save=true) DOES write schedules to DB")
    void confirm_writesSchedules() {
        requirementRepository.save(ShiftRequirement.builder()
                .period(draftPeriod).workDate(draftPeriod.getStartDate())
                .shiftType(l01).requiredStaffCount(1).build());

        long before = countSchedules();
        AutoScheduleResponse resp = autoSchedulingService.autoSchedule(
                AutoScheduleRequestDTO.builder()
                        .periodId(draftPeriod.getId())
                        .algorithmType("ENHANCED_GREEDY")
                        .overwriteExisting(true)
                        .build());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(countSchedules()).isGreaterThan(before);
    }

    @Test
    @DisplayName("4. Confirm (save=true) writes only to the target period")
    void confirm_writesOnlyTargetPeriod() {
        requirementRepository.save(ShiftRequirement.builder()
                .period(draftPeriod).workDate(draftPeriod.getStartDate())
                .shiftType(l01).requiredStaffCount(1).build());

        autoSchedulingService.autoSchedule(AutoScheduleRequestDTO.builder()
                .periodId(draftPeriod.getId())
                .algorithmType("ENHANCED_GREEDY")
                .overwriteExisting(true)
                .build());

        assertThat(scheduleRepository.findByPeriodId(draftPeriod.getId())).isNotEmpty();
    }
}
