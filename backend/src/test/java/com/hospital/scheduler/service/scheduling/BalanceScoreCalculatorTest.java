package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.algorithm.scoring.ShiftTypeWeights;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BalanceScoreCalculator with weighted-volume CV.
 *
 * L01 weight=2 (24h + compensation day), L02/L03/L04 weight=1.
 * Score = max(0, 100 - CV) of total weighted volume per staff.
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

        Schedule s1 = Schedule.builder()
                .staff(staff).shiftType(shiftType)
                .workDate(LocalDate.now()).build();
        BigDecimal score = calculator.calculateBalanceScore(List.of(s1), 1);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void testPerfectBalanceYieldsHighScore() {
        ShiftType l01 = new ShiftType();
        l01.setId("L01");

        // 3 staff, each with 2 L01 shifts → each weighted = 4, CV=0, score=100
        List<Schedule> schedules = new ArrayList<>();
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
                        .shiftType(l01)
                        .workDate(LocalDate.of(2026, 1, i + 1))
                        .build();
                schedules.add(s);
            }
        }

        BigDecimal score = calculator.calculateBalanceScore(schedules, 3);
        assertTrue(score.compareTo(BigDecimal.valueOf(99)) > 0,
                "Perfect balance should yield score > 99, got: " + score);
    }

    @Test
    void testWeightedVolumePenalizesL01Concentration() {
        ShiftType l01 = new ShiftType(); l01.setId("L01");
        ShiftType l02 = new ShiftType(); l02.setId("L02");

        // Staff A: 5 L01 → weighted = 10
        // Staff B: 5 L02 → weighted = 5
        // Mean = (10+5)/2 = 7.5
        // Variance = ((10-7.5)^2 + (5-7.5)^2)/2 = (6.25+6.25)/2 = 6.25
        // StdDev = 2.5
        // CV = (2.5/7.5)*100 = 33.33
        // Score = max(0, 100-33.33) = 66.67
        // Old per-type CV: L01 CV=0 (only A), L02 CV=0 (only B) → avg=0 → score=100
        // New weighted volume: one-sided L01 concentration penalized
        List<Schedule> schedules = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Staff staffA = new Staff(); staffA.setId(1);
            Staff staffB = new Staff(); staffB.setId(2);
            schedules.add(Schedule.builder()
                    .staff(staffA).shiftType(l01)
                    .workDate(LocalDate.of(2026, 1, i + 1)).build());
            schedules.add(Schedule.builder()
                    .staff(staffB).shiftType(l02)
                    .workDate(LocalDate.of(2026, 1, i + 1)).build());
        }

        BigDecimal score = calculator.calculateBalanceScore(schedules, 2);
        // Expected: ~66.67 (weighted shows imbalance)
        // Old per-type CV: L01 CV=0 (only A), L02 CV=0 (only B) → avg=0 → score=100
        // New weighted volume: shows total workload imbalance
        assertTrue(score.compareTo(BigDecimal.valueOf(80)) < 0,
                "Weighted score should be < 80 due to L01 weight=2 imbalance, got: " + score);
        assertTrue(score.compareTo(BigDecimal.valueOf(50)) > 0,
                "Score should still be reasonable (>50), got: " + score);
    }

    @Test
    void testEvenWeightedVolumeYieldsHighScore() {
        ShiftType l01 = new ShiftType(); l01.setId("L01");
        ShiftType l02 = new ShiftType(); l02.setId("L02");

        // 3 staff, each with balanced mix:
        // Staff A: 2 L01 + 1 L02 = 2*2 + 1 = 5
        // Staff B: 1 L01 + 3 L02 = 1*2 + 3 = 5
        // Staff C: 0 L01 + 5 L02 = 0*2 + 5 = 5
        // All have weighted=5 → CV=0 → score=100
        List<Schedule> schedules = new ArrayList<>();
        Staff sA = new Staff(); sA.setId(1);
        Staff sB = new Staff(); sB.setId(2);
        Staff sC = new Staff(); sC.setId(3);

        for (int i = 0; i < 2; i++)
            schedules.add(Schedule.builder().staff(sA).shiftType(l01).workDate(LocalDate.of(2026,1,i+1)).build());
        schedules.add(Schedule.builder().staff(sA).shiftType(l02).workDate(LocalDate.of(2026,1,10)).build());
        schedules.add(Schedule.builder().staff(sB).shiftType(l01).workDate(LocalDate.of(2026,1,20)).build());
        for (int i = 0; i < 3; i++)
            schedules.add(Schedule.builder().staff(sB).shiftType(l02).workDate(LocalDate.of(2026,1,i+21)).build());
        for (int i = 0; i < 5; i++)
            schedules.add(Schedule.builder().staff(sC).shiftType(l02).workDate(LocalDate.of(2026,2,i+1)).build());

        BigDecimal score = calculator.calculateBalanceScore(schedules, 3);
        assertTrue(score.compareTo(BigDecimal.valueOf(95)) > 0,
                "Even weighted volume should yield score > 95, got: " + score);
    }
}
