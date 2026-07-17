package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.service.AlgorithmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cross-specialty fairness tests for {@link FairShareCalculator}.
 *
 * <p>Contract under test:
 *  - L01/L02/L03 use the full staff pool for fair share.
 *  - L04 uses a SPECIFIC-pool when cross-specialty is disabled (only staff
 *    matching the requirement's specialty count toward the L04 pool).
 *  - L04 uses the ELIGIBLE-pool (CORE specialties = Nội/Ngoại) when cross-
 *    specialty is enabled. Per-specialty demand is spread across that pool.
 *  - The result exposes a per-specialty breakdown via keys like "L04:42".
 */
@DisplayName("FairShareCalculator — M05 cross-specialty fairness")
class FairShareCalculatorTest {

    private AlgorithmConfigService algoConfig;
    private StaffEligibilityFilter eligibilityFilter;
    private FairShareCalculator calculator;

    // Stable IDs for spec/staff so we can assert specific keys.
    private static final int SPEC_NOI = 1;
    private static final int SPEC_NGOAI = 2;
    private static final int SPEC_OTHER = 3;

    @BeforeEach
    void setUp() {
        algoConfig = mock(AlgorithmConfigService.class);
        eligibilityFilter = mock(StaffEligibilityFilter.class);
        calculator = new FairShareCalculator(algoConfig, eligibilityFilter);
    }

    /** Build a staff with the given specialty, marked active + eligible for L04. */
    private static Staff staff(int id, int specId, String specName) {
        Specialty spec = Specialty.builder().id(specId).name(specName).isActive(true).build();
        return Staff.builder()
                .id(id).username("s" + id).fullName("Staff " + id).isActive(true)
                .specialty(spec).maxShiftsPerMonth(20).build();
    }

    /** Build a ShiftRequirement with the given (shift type, specialty, count). */
    private static ShiftRequirement req(String typeId, int specId, int count, LocalDate date) {
        ShiftType shiftType = ShiftType.builder().id(typeId).name(typeId).isActive(true).build();
        Specialty spec = Specialty.builder().id(specId).name("spec-" + specId).build();
        return ShiftRequirement.builder()
                .shiftType(shiftType)
                .specialty(spec)
                .requiredStaffCount(count)
                .workDate(date)
                .build();
    }

    @Nested
    @DisplayName("Default behavior (L01/L02/L03 use full staff pool)")
    class DefaultPool {

        @Test
        @DisplayName("L01 demand spread across full 10-staff pool")
        void l01_usesFullPool() {
            List<ShiftRequirement> reqs = new ArrayList<>();
            for (int d = 0; d < 5; d++) {
                reqs.add(req("L01", SPEC_NOI, 2, LocalDate.of(2026, 7, 1).plusDays(d)));
            }
            // 5 days × 2 staff = 10 L01 slots across 10 staff → fair share = 1
            var result = calculator.computeFairSharePerType(reqs, 10);

            assertThat(result.get("L01"))
                    .as("L01 should be ceil(10/10) = 1 per staff")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("L02 with 3 staff → fair share uses full 3-staff pool")
        void l02_usesFullPool() {
            var reqs = List.of(req("L02", SPEC_NOI, 6, LocalDate.of(2026, 7, 1)));
            var result = calculator.computeFairSharePerType(reqs, 3);

            assertThat(result.get("L02"))
                    .as("L02 should be ceil(6/3) = 2 per staff")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("L03 with 1 staff → fair share is just demand")
        void l03_singleStaff() {
            var reqs = List.of(req("L03", SPEC_NOI, 4, LocalDate.of(2026, 7, 1)));
            var result = calculator.computeFairSharePerType(reqs, 1);

            assertThat(result.get("L03"))
                    .as("L03 single staff = full demand")
                    .isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("L04 cross-specialty DISABLED — uses matching-spec pool only")
    class L04Disabled {

        @BeforeEach
        void disableCrossSpecialty() {
            when(algoConfig.getAutoGenConfig()).thenReturn(Optional.of(
                    AutoGenConfig.builder()
                            .l04CrossSpecialty(false)
                            .l04CrossSpecialtyRatio(0.3f)
                            .l04AllowedSpecialties(List.of())
                            .l04BalanceStrategy("FAIR_DISTRIBUTE")
                            .build()
            ));
            when(eligibilityFilter.getL04CrossSpecialtyConfig())
                    .thenReturn(StaffEligibilityFilter.CrossSpecialtyConfig.disabled());
        }

        @Test
        @DisplayName("L04 demand split per specialty; only matching staff count toward pool")
        void l04_usesPerSpecialtyPool() {
            // 4 L04-Noi slots + 2 L04-Ngoai slots; staff pool = 5 Noi + 3 Ngoai + 4 Other
            List<ShiftRequirement> reqs = List.of(
                    req("L04", SPEC_NOI, 4, LocalDate.of(2026, 7, 1)),
                    req("L04", SPEC_NGOAI, 2, LocalDate.of(2026, 7, 2))
            );

            List<Staff> active = new ArrayList<>();
            for (int i = 0; i < 5; i++) active.add(staff(100 + i, SPEC_NOI, "Nội"));
            for (int i = 0; i < 3; i++) active.add(staff(200 + i, SPEC_NGOAI, "Ngoại"));
            for (int i = 0; i < 4; i++) active.add(staff(300 + i, SPEC_OTHER, "Khác"));

            // staffPool arg is ignored when L04 has specialty-bound requirements
            var result = calculator.computeFairSharePerTypeWithStaff(reqs, 99, active);

            // Total L04 demand = 6 across all specialty-bound slots
            assertThat(result.get("L04"))
                    .as("L04 total = ceil(6 / max(1, eligibleAcrossAll))")
                    .isGreaterThanOrEqualTo(1);

            // Per-specialty breakdown: Noi = ceil(4/5) = 1, Ngoai = ceil(2/3) = 1
            assertThat(result.get("L04:" + SPEC_NOI))
                    .as("Noi staff share 4 L04 slots across 5 staff")
                    .isEqualTo(1);
            assertThat(result.get("L04:" + SPEC_NGOAI))
                    .as("Ngoai staff share 2 L04 slots across 3 staff")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("L04 cross-specialty ENABLED — uses eligible CORE pool (Nội/Ngoại)")
    class L04Enabled {

        @BeforeEach
        void enableCrossSpecialty() {
            when(algoConfig.getAutoGenConfig()).thenReturn(Optional.of(
                    AutoGenConfig.builder()
                            .l04CrossSpecialty(true)
                            .l04CrossSpecialtyRatio(0.3f)
                            .l04AllowedSpecialties(List.of("Nội", "Ngoại"))
                            .l04BalanceStrategy("FAIR_DISTRIBUTE")
                            .build()
            ));
            when(eligibilityFilter.getL04CrossSpecialtyConfig())
                    .thenReturn(new StaffEligibilityFilter.CrossSpecialtyConfig(true, 0.3f,
                            List.of("Nội", "Ngoại"), "FAIR_DISTRIBUTE"));
        }

        @Test
        @DisplayName("When cross enabled, only Nội/Ngoại staff count toward L04 pool")
        void l04_usesEligibleCorePool() {
            // Demand: 10 L04-Noi + 10 L04-Ngoai = 20 total
            List<ShiftRequirement> reqs = List.of(
                    req("L04", SPEC_NOI, 10, LocalDate.of(2026, 7, 1)),
                    req("L04", SPEC_NGOAI, 10, LocalDate.of(2026, 7, 2))
            );

            // 5 Nội + 5 Ngoại (eligible CORE) + 10 Khác (NOT eligible for L04 even when cross on)
            List<Staff> active = new ArrayList<>();
            for (int i = 0; i < 5; i++) active.add(staff(100 + i, SPEC_NOI, "Nội"));
            for (int i = 0; i < 5; i++) active.add(staff(200 + i, SPEC_NGOAI, "Ngoại"));
            for (int i = 0; i < 10; i++) active.add(staff(300 + i, SPEC_OTHER, "Khác"));

            var result = calculator.computeFairSharePerTypeWithStaff(reqs, 99, active);

            // Eligible CORE pool = 10 staff (5 Nội + 5 Ngoại) — Khác is filtered out
            // because StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES contains only
            // the configured cross-specialty allowed set when cross is on.
            // Per the production rule: totalEligibleL04Staff counts staff whose specialty
            // name ∈ ALL_ELIGIBLE_SPECIALTIES. The implementation reads ALL_ELIGIBLE_SPECIALTIES
            // regardless of cross-flag for the "enabled" branch, so eligible = 10 here.
            int totalEligible = 10;
            int expectedL04Total = (int) Math.ceil((double) 20 / totalEligible);
            assertThat(result.get("L04"))
                    .as("L04 total = ceil(%d / %d)", 20, totalEligible)
                    .isEqualTo(expectedL04Total);

            // Per-spec breakdown should still exist (Noi, Ngoai keys)
            assertThat(result).containsKey("L04:" + SPEC_NOI);
            assertThat(result).containsKey("L04:" + SPEC_NGOAI);
        }

        @Test
        @DisplayName("Sanity: ALL_ELIGIBLE_SPECIALTIES contains the expected core names")
        void eligibleSpecialties_constant() {
            // Guards against accidental rename / removal of the core specialty list.
            // This constant drives the M05 cross-specialty fairness boundary.
            assertThat(StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES)
                    .as("Core L04-eligible specialties must include Nội and Ngoại")
                    .contains("Nội", "Ngoại");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty requirements → safe default fair-share of 1 for every type")
        void emptyRequirements_safeDefault() {
            var result = calculator.computeFairSharePerType(List.of(), 10);
            assertThat(result.get("L01")).isEqualTo(1);
            assertThat(result.get("L02")).isEqualTo(1);
            assertThat(result.get("L03")).isEqualTo(1);
            assertThat(result.get("L04")).isEqualTo(1);
        }
    }
}