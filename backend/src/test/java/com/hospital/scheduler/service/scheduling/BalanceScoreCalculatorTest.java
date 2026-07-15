package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BalanceScoreCalculator.
 * Verifies per-type CV computation and edge cases (empty schedules, single staff).
 */
@ExtendWith(MockitoExtension.class)
class BalanceScoreCalculatorTest {

    @InjectMocks
    private BalanceScoreCalculator calculator;

    @Test
    void testEmptySchedulesReturnsZero() {
        BigDecimal score = calculator.calculateBalanceScore(List.of(), 10);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void testSingleStaffReturnsZero() {
        ShiftType shiftType = new ShiftType();
        shiftType.setId("L01");
        Staff staff = new Staff();
        staff.setId(1);

        Schedule s1 = Schedule.builder().staff(staff).shiftType(shiftType).workDate(java.time.LocalDate.now()).build();
        BigDecimal score = calculator.calculateBalanceScore(List.of(s1), 1);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void testPerfectBalanceYieldsHighScore() {
        ShiftType shiftType = new ShiftType();
        shiftType.setId("L01");

        // 3 staff, each with 2 L01 shifts = perfect balance
        List<Schedule> schedules = new java.util.ArrayList<>();
        for (int staffId = 1; staffId <= 3; staffId++) {
            Staff staff = new Staff();
            staff.setId(staffId);
            Specialty spec = new Specialty();
            spec.setId(1);
            spec.setName("Ngoại");
            staff.setSpecialty(spec);
            for (int i = 0; i < 2; i++) {
                Schedule s = Schedule.builder()
                        .staff(staff)
                        .shiftType(shiftType)
                        .workDate(java.time.LocalDate.of(2026, 1, i + 1))
                        .build();
                schedules.add(s);
            }
        }

        BigDecimal score = calculator.calculateBalanceScore(schedules, 3);
        // Perfect balance should produce a very high score (close to 100)
        assertTrue(score.compareTo(BigDecimal.valueOf(99)) > 0,
                "Perfect balance should yield score > 99, got: " + score);
    }
}
