package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}
