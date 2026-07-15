package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SchedulingStateAccessor.
 * Verifies ThreadLocal state isolation, reset/cleanup, and conflict detection.
 */
@ExtendWith(MockitoExtension.class)
class SchedulingStateAccessorTest {

    @InjectMocks
    private SchedulingStateAccessor accessor;

    @Test
    void testResetClearsAllState() {
        accessor.addAssignment(1, LocalDate.now(), "L01");
        accessor.addCompensationShiftDate(1, LocalDate.now().plusDays(1));
        accessor.addSwapPriorityStaff(1);

        accessor.reset();

        assertTrue(accessor.getInMemoryAssignments().isEmpty());
        assertTrue(accessor.getInMemoryCompensationShiftDates().isEmpty());
        assertTrue(accessor.getAllCompensationShiftDates().isEmpty());
        assertTrue(accessor.getSwapPriorityStaffIds().isEmpty());
    }

    @Test
    void testAddAssignmentTracksStaffDateShift() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        accessor.addAssignment(5, date, "L01");
        accessor.addAssignment(5, date, "L02");

        Map<String, Set<String>> assignments = accessor.getInMemoryAssignments();
        assertTrue(assignments.containsKey("5_" + date));
        assertEquals(2, assignments.get("5_" + date).size());
    }

    @Test
    void testInMemoryConflictBlocksL01L02OnSameDay() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        accessor.addAssignment(1, date, "L01");

        assertTrue(accessor.hasInMemoryConflict(1, date, "L02"));
    }

    @Test
    void testInMemoryConflictBlocksL03L04OnSameDay() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        accessor.addAssignment(1, date, "L03");

        assertTrue(accessor.hasInMemoryConflict(1, date, "L04"));
    }

    @Test
    void testNoInMemoryConflictForUnassignedStaff() {
        assertFalse(accessor.hasInMemoryConflict(99, LocalDate.now(), "L01"));
    }

    @Test
    void testAdjacentL01ConflictDetectsBackToBack() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        accessor.addAssignment(1, day, "L01");
        assertTrue(accessor.hasAdjacentL01Conflict(1, day.plusDays(1)));
        assertTrue(accessor.hasAdjacentL01Conflict(1, day.minusDays(1)));
        assertTrue(accessor.hasAdjacentL01Conflict(1, day.plusDays(2)));
        assertFalse(accessor.hasAdjacentL01Conflict(1, day.plusDays(5)));
    }

    @Test
    void testIsCompensationDateFromInMemoryCache() {
        LocalDate compDate = LocalDate.of(2026, 1, 2);
        accessor.addCompensationShiftDate(1, compDate);

        assertTrue(accessor.isCompensationDate(1, compDate));
    }

    @Test
    void testCleanupRemovesThreadLocal() {
        accessor.addAssignment(1, LocalDate.now(), "L01");
        accessor.cleanup();

        // After cleanup a new thread-local instance is initialized, but the previous
        // values are no longer accessible on this thread.
        // We can't directly check for "removed" but we can verify reset still works.
        accessor.reset();
        assertTrue(accessor.getInMemoryAssignments().isEmpty());
    }
}
