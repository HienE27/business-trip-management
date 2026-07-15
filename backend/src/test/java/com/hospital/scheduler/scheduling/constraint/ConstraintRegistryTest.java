package com.hospital.scheduler.scheduling.constraint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConstraintRegistry.
 */
class ConstraintRegistryTest {

    private ConstraintRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ConstraintRegistry.createDefault();
    }

    @Test
    void testCreateDefault() {
        assertNotNull(registry);
        assertFalse(registry.getAllConstraints().isEmpty());
    }

    @Test
    void testGetAllConstraints() {
        List<Constraint> constraints = registry.getAllConstraints();
        
        assertEquals(6, constraints.size());
    }

    @Test
    void testGetHardConstraints() {
        List<Constraint> hardConstraints = registry.getHardConstraints();
        
        // BR-01 to BR-05 are HARD
        assertEquals(5, hardConstraints.size());
        
        for (Constraint c : hardConstraints) {
            assertEquals(Constraint.Type.HARD, c.type());
        }
    }

    @Test
    void testGetSoftConstraints() {
        List<Constraint> softConstraints = registry.getSoftConstraints();
        
        // BR-06 and BR-07 are SOFT
        assertEquals(1, softConstraints.size());
        
        for (Constraint c : softConstraints) {
            assertEquals(Constraint.Type.SOFT, c.type());
        }
    }

    @Test
    void testRegisterConstraint() {
        ConstraintRegistry newRegistry = new ConstraintRegistry();
        int initialSize = newRegistry.getAllConstraints().size();
        
        newRegistry.register(new ShiftConflictConstraint());
        
        assertEquals(initialSize + 1, newRegistry.getAllConstraints().size());
    }

    @Test
    void testConstraintNames() {
        List<Constraint> constraints = registry.getAllConstraints();
        
        List<String> names = constraints.stream()
                .map(Constraint::name)
                .toList();
        
        assertTrue(names.contains("ShiftConflictConstraint"));
        assertTrue(names.contains("RestDayConstraint"));
        assertTrue(names.contains("AdjacentL01Constraint"));
        assertTrue(names.contains("LeaveConflictConstraint"));
        assertTrue(names.contains("MaxShiftsConstraint"));
        assertTrue(names.contains("DuplicateShiftConstraint"));
    }
}
