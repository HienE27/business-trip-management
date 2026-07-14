package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_WEIGHTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the shift-type weighted fitness helpers on
 * {@link ScheduleChromosome}.
 *
 * <p>Guards against a naive {@code calculateCoverage()} /
 * {@code calculateBalance()} letting a chromosome that filled only
 * light shifts (L03/L04) appear well-covered / well-balanced while
 * leaving heavy L01 (24/24) duty unfilled.  The weight table is
 * consumed from the single source of truth: {@link CspConstants#SHIFT_WEIGHTS}.
 *
 * <p>Unlike the old version, this test uses the package-private
 * {@code (requirements, staffPool, genes)} constructor rather than
 * reflection, so it will fail with a compile error (not a silent pass)
 * if the constructor signature ever changes.
 */
@DisplayName("ScheduleChromosome shift-weight fitness")
class SchedulingFitnessShiftWeightTest {

    // ─── Test helpers ───────────────────────────────────────────

    /**
     * Build a chromosome from parallel {@code genes} and {@code shiftTypeIds}
     * arrays.  Gene value = staff index; {@code -1} = unassigned.
     */
    private static ScheduleChromosome chromosome(int[] genes, String... shiftTypeIds) {
        if (genes.length != shiftTypeIds.length) {
            throw new IllegalArgumentException("genes and shiftTypeIds must have same length");
        }
        LocalDate base = LocalDate.of(2026, 8, 1);
        List<ShiftRequirementInfo> reqs = java.util.stream.IntStream.range(0, shiftTypeIds.length)
                .mapToObj(i -> new ShiftRequirementInfo(shiftTypeIds[i], base.plusDays(i), 1))
                .toList();
        List<Staff> pool = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> Staff.builder()
                        .id(100 + i)
                        .fullName("Staff " + (i + 1))
                        .build())
                .toList();
        return new ScheduleChromosome(reqs, pool, genes);
    }

    // ─── Weighted coverage tests ─────────────────────────────────

    @Nested
    @DisplayName("weighted coverage")
    class WeightedCoverage {

        @Test
        @DisplayName("favours heavy L01 fills over light L03 fills on mixed requirements")
        void heavyL01BetterThanLightL03() {
            // Mixed requirements: 2 L01 (weight 3.0) + 2 L03 (weight 1.0).
            // Total weighted demand = 2*3 + 2*1 = 8.
            //
            // fillsHeavy: genes 0→L01, 1→L01, 2→unassigned, 3→unassigned
            //   weighted = 2*3 + 0 = 6  → coverage = 6/8 = 0.75
            //
            // fillsLight: genes 0→unassigned, 1→unassigned, 2→L03, 3→L03
            //   weighted = 0 + 2*1 = 2  → coverage = 2/8 = 0.25
            double heavyCov = chromosome(new int[]{0, 1, -1, -1}, "L01", "L01", "L03", "L03")
                    .calculateWeightedCoverage(SHIFT_WEIGHTS);
            double lightCov = chromosome(new int[]{-1, -1, 2, 3}, "L01", "L01", "L03", "L03")
                    .calculateWeightedCoverage(SHIFT_WEIGHTS);

            assertThat(heavyCov).isEqualTo(0.75);
            assertThat(lightCov).isEqualTo(0.25);
            assertThat(heavyCov).isGreaterThan(lightCov);
        }

        @Test
        @DisplayName("all slots filled returns 1.0")
        void allFilled_returnsOne() {
            double cov = chromosome(new int[]{0, 1, 2, 3}, "L01", "L02", "L03", "L04")
                    .calculateWeightedCoverage(SHIFT_WEIGHTS);
            assertThat(cov).isEqualTo(1.0);
        }

        @Test
        @DisplayName("no slots filled returns 0.0")
        void noneFilled_returnsZero() {
            double cov = chromosome(new int[]{-1, -1, -1, -1}, "L01", "L02", "L03", "L04")
                    .calculateWeightedCoverage(SHIFT_WEIGHTS);
            assertThat(cov).isEqualTo(0.0);
        }

        @Test
        @DisplayName("partially filled returns exact ratio")
        void partialFill_exactRatio() {
            // 3 slots, weights 3+1+1=5 total; 2 assigned with weight 3+1=4
            double cov = chromosome(new int[]{0, 1, -1}, "L01", "L03", "L04")
                    .calculateWeightedCoverage(SHIFT_WEIGHTS);
            assertThat(cov).isEqualTo(4.0 / 5.0);
        }
    }

    // ─── Weighted balance tests ─────────────────────────────────

    @Nested
    @DisplayName("weighted balance")
    class WeightedBalance {

        @Test
        @DisplayName("even L01 distribution outscores concentrated distribution")
        void balancedBetterThanConcentrated() {
            // 4 L01 requirements: 3 to staff 0, 1 unassigned
            double unbalanced = chromosome(new int[]{0, 0, 0, -1},
                    "L01", "L01", "L01", "L01")
                    .calculateWeightedBalance(SHIFT_WEIGHTS);

            // Same 3 L01 spread across staff 0, 1, 2
            double balanced = chromosome(new int[]{0, 1, 2, -1},
                    "L01", "L01", "L01", "L01")
                    .calculateWeightedBalance(SHIFT_WEIGHTS);

            assertThat(balanced).isGreaterThan(unbalanced);
        }

        @Test
        @DisplayName("mixed types: heavier shift concentrated on one staff scores lower")
        void mixed_heavyConcentrated_lower() {
            // Staff 0 gets both L01 (3.0) + L02 (1.5); staff 1 gets nothing
            double concentrated = chromosome(new int[]{0, 0, -1, -1},
                    "L01", "L02", "L03", "L04")
                    .calculateWeightedBalance(SHIFT_WEIGHTS);

            // Staff 0 gets L01, staff 1 gets L02
            double spread = chromosome(new int[]{0, 1, -1, -1},
                    "L01", "L02", "L03", "L04")
                    .calculateWeightedBalance(SHIFT_WEIGHTS);

            assertThat(spread).isGreaterThan(concentrated);
        }
    }

    // ─── Unweighted coverage (unchanged behaviour) ───────────────

    @Nested
    @DisplayName("unweighted coverage")
    class UnweightedCoverage {

        @Test
        @DisplayName("all filled returns 1.0")
        void allFilled_returnsOne() {
            double cov = chromosome(new int[]{0, 1, 2, 3}, "L01", "L02", "L03", "L04")
                    .calculateCoverage();
            assertThat(cov).isEqualTo(1.0);
        }

        @Test
        @DisplayName("none filled returns 0.0")
        void noneFilled_returnsZero() {
            double cov = chromosome(new int[]{-1, -1, -1, -1}, "L01", "L02", "L03", "L04")
                    .calculateCoverage();
            assertThat(cov).isEqualTo(0.0);
        }

        @Test
        @DisplayName("half filled returns 0.5")
        void halfFilled_returnsHalf() {
            double cov = chromosome(new int[]{0, 1, -1, -1}, "L01", "L02", "L03", "L04")
                    .calculateCoverage();
            assertThat(cov).isEqualTo(0.5);
        }
    }

    // ─── Edge cases ─────────────────────────────────────────────

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty chromosome returns 0.0 for weighted coverage")
        void emptyCoverage_returnsZero() {
            List<ShiftRequirementInfo> emptyReqs = List.of();
            List<Staff> pool = List.of(Staff.builder().id(1).fullName("S1").build());
            var c = new ScheduleChromosome(emptyReqs, pool, new int[]{});
            assertThat(c.calculateWeightedCoverage(SHIFT_WEIGHTS)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("genes length mismatch throws IllegalArgumentException")
        void lengthMismatch_throws() {
            LocalDate base = LocalDate.of(2026, 8, 1);
            List<ShiftRequirementInfo> reqs = List.of(
                    new ShiftRequirementInfo("L01", base, 1));
            List<Staff> pool = List.of(Staff.builder().id(1).fullName("S1").build());
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new ScheduleChromosome(reqs, pool, new int[]{0, 1})) // 2 genes, 1 req
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("genes length");
        }
    }
}
