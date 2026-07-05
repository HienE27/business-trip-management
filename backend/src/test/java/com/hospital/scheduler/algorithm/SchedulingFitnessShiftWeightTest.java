package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the shift-type weighted fitness helpers on
 * {@link ScheduleChromosome}.
 *
 * <p>Guards audit item 13: a naive {@code calculateCoverage()} /
 * {@code calculateBalance()} would let a chromosome that filled only
 * light shifts (L03/L04) appear well covered / well balanced while
 * leaving heavy L01 (24/24) duty unfilled. The weight table matches
 * the one in {@link SchedulingFitnessFunction}.
 */
class SchedulingFitnessShiftWeightTest {

    private static final Map<String, Double> WEIGHTS = Map.of(
            "L01", 3.0,
            "L02", 1.5,
            "L03", 1.0,
            "L04", 1.0
    );

    /** Build a chromosome with the given {@code genes} mapping onto the
     * matching shift requirements. Gene {@code -1} means "unassigned". */
    private static ScheduleChromosome chromosome(int[] genes, String... shiftTypeIds) {
        if (genes.length != shiftTypeIds.length) {
            throw new IllegalArgumentException("genes and shiftTypeIds length mismatch");
        }
        LocalDate base = LocalDate.of(2026, 8, 1);
        List<ShiftRequirementInfo> reqs = new ArrayList<>(shiftTypeIds.length);
        for (int i = 0; i < shiftTypeIds.length; i++) {
            reqs.add(new ShiftRequirementInfo(shiftTypeIds[i], base.plusDays(i), 1));
        }
        List<Staff> staffPool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            staffPool.add(Staff.builder().id(100 + i).fullName("Staff " + (i + 1)).build());
        }
        ScheduleChromosome c = new ScheduleChromosome(reqs, staffPool);

        // ScheduleChromosome has no public seed constructor; use reflection
        // so the test stays meaningful without changing the production API.
        try {
            Field genesField = ScheduleChromosome.class.getDeclaredField("genes");
            genesField.setAccessible(true);
            genesField.set(c, genes.clone());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to seed genes via reflection", e);
        }
        return c;
    }

    @Test
    void weightedCoverage_favoursHeavyShiftsOverLightFills() {
        // Mixed requirement list: 2 L01 (heavy) + 2 L03 (light).
        // Total weighted demand = 2*3 + 2*1 = 8.
        //
        // Chromosome "fillsHeavy": fills both L01 requirements with L01.
        //   Filled weighted = 2*3 + 0 = 6. Coverage = 6/8 = 0.75.
        //
        // Chromosome "fillsLight": fills both L03 requirements with L03.
        //   Filled weighted = 0 + 2*1 = 2. Coverage = 2/8 = 0.25.
        ScheduleChromosome fillsHeavy = chromosome(
                new int[]{0, 1, -1, -1}, "L01", "L01", "L03", "L03");
        ScheduleChromosome fillsLight = chromosome(
                new int[]{-1, -1, 2, 3}, "L01", "L01", "L03", "L03");

        double heavyCoverage = fillsHeavy.calculateWeightedCoverage(WEIGHTS);
        double lightCoverage = fillsLight.calculateWeightedCoverage(WEIGHTS);

        assertEquals(0.75, heavyCoverage, 1e-9);
        assertEquals(0.25, lightCoverage, 1e-9);
        assertTrue(heavyCoverage > lightCoverage,
                "Filling only L01 should look better than filling only L03 when requirement mix is mixed"
                        + " (heavy=" + heavyCoverage + ", light=" + lightCoverage + ")");
    }

    @Test
    void weightedBalance_penalisesUnbalancedHeavyLoads() {
        // 3 L01 requirements, all to staff 0. staff 1-3 are underloaded.
        ScheduleChromosome unbalanced = chromosome(new int[]{0, 0, 0, -1},
                "L01", "L01", "L01", "L01");

        // Spread the same 3 L01 across staff 0, 1, 2.
        ScheduleChromosome balanced = chromosome(new int[]{0, 1, 2, -1},
                "L01", "L01", "L01", "L01");

        double unbalancedScore = unbalanced.calculateWeightedBalance(WEIGHTS);
        double balancedScore = balanced.calculateWeightedBalance(WEIGHTS);

        assertTrue(balancedScore > unbalancedScore,
                "Balanced L01 distribution should outscore concentrated: "
                        + "balanced=" + balancedScore + ", unbalanced=" + unbalancedScore);
    }

    @Test
    void weightedCoverage_allSlotsFilled_returnsOne() {
        ScheduleChromosome full = chromosome(new int[]{0, 1, 2, 3},
                "L01", "L02", "L03", "L04");
        assertEquals(1.0, full.calculateWeightedCoverage(WEIGHTS), 1e-9);
    }

    @Test
    void weightedCoverage_noSlotsFilled_returnsZero() {
        ScheduleChromosome empty = chromosome(new int[]{-1, -1, -1, -1},
                "L01", "L02", "L03", "L04");
        assertEquals(0.0, empty.calculateWeightedCoverage(WEIGHTS), 1e-9);
    }
}
