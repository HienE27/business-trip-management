package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StaffEligibilityFilter business shift conflict detection.
 */
@ExtendWith(MockitoExtension.class)
class StaffEligibilityFilterTest {

    @Mock
    private ConflictDetectionService conflictDetectionService;

    @Mock
    private AlgorithmConfigService algorithmConfigService;

    @InjectMocks
    private StaffEligibilityFilter filter;

    @Test
    void testL01L02ConflictIsBusinessConflict() {
        assertTrue(filter.isBusinessShiftConflict("L01", "L02"));
        assertTrue(filter.isBusinessShiftConflict("L02", "L01"));
    }

    @Test
    void testL03L04ConflictIsBusinessConflict() {
        assertTrue(filter.isBusinessShiftConflict("L03", "L04"));
        assertTrue(filter.isBusinessShiftConflict("L04", "L03"));
    }

    @Test
    void testSameTypeIsNotBusinessConflict() {
        assertFalse(filter.isBusinessShiftConflict("L01", "L01"));
        assertFalse(filter.isBusinessShiftConflict("L02", "L02"));
    }

    @Test
    void testMixedShiftTypesAreNotBusinessConflicts() {
        // L01 vs L03: not a documented business conflict
        assertFalse(filter.isBusinessShiftConflict("L01", "L03"));
        // L02 vs L04: not a documented business conflict
        assertFalse(filter.isBusinessShiftConflict("L02", "L04"));
    }

    /**
     * BUGFIX (M07-CROSSCONFIG): when the user disables
     * l04.crossSpecialtyEnabled, getCrossSpecialtyConfig must return a
     * config with enabled=false so cross-specialty staff are filtered out of
     * the eligible pool — not just deprioritized.
     */
    @Test
    void l04CrossSpecialty_disabledInConfig_returnsDisabledConfig() {
        var cfg = new com.hospital.scheduler.algorithm.AutoGenConfig(
                true,                              // enabled
                1, 1, 1, 1,                        // min/day
                0, 0, 0, 0,                        // max/day
                1, 2, 1, 1,                        // min/week
                0, 0, 0, 0,                        // max/week
                "SKIP", List.of(),
                /* l04CrossSpecialty */ false,     // ← the toggle the user flipped OFF
                0.3f, List.of(), "FAIR_DISTRIBUTE");
        when(algorithmConfigService.getAutoGenConfig()).thenReturn(Optional.of(cfg));

        var resolved = filter.getCrossSpecialtyConfig("L04");

        assertFalse(resolved.enabled(),
                "BUGFIX M07-CROSSCONFIG: getCrossSpecialtyConfig must reflect the user's OFF toggle "
                        + "so cross-specialty staff are excluded from the eligible pool.");
    }

    @Test
    void l04CrossSpecialty_enabledInConfig_returnsEnabledConfig() {
        var cfg = new com.hospital.scheduler.algorithm.AutoGenConfig(
                true,
                1, 1, 1, 1, 0, 0, 0, 0,
                1, 2, 1, 1, 0, 0, 0, 0,
                "SKIP", List.of(),
                /* l04CrossSpecialty */ true,
                0.5f, List.of(), "FAIR_DISTRIBUTE");
        when(algorithmConfigService.getAutoGenConfig()).thenReturn(Optional.of(cfg));

        var resolved = filter.getCrossSpecialtyConfig("L04");

        assertTrue(resolved.enabled());
        assertEquals(0.5f, resolved.ratio());
    }

    @Test
    void l01_l02_l03_crossSpecialty_alwaysDisabled_regardlessOfConfig() {
        // L01/L02/L03 have no specialty config; the helper short-circuits
        // before consulting the AutoGenConfig and returns disabled() so the
        // eligibility filter never enters the cross-specialty code path for
        // those shift types. No mock setup needed — calling the helper with
        // the algorithm config mocked would be a tautology.
        for (String type : List.of("L01", "L02", "L03")) {
            var resolved = filter.getCrossSpecialtyConfig(type);
            assertFalse(resolved.enabled(),
                    type + " must never have cross-specialty enabled "
                            + "(no specialty config exists for non-L04 shift types).");
        }
    }
}
