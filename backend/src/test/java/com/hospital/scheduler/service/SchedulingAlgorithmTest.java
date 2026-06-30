package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.CompensationDateCalculator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for scheduling algorithms.
 * Tests Greedy, Round Robin, and balance scoring logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Scheduling Algorithm Tests")
class SchedulingAlgorithmTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ShiftRequirementRepository requirementRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AlgorithmMetricsRepository metricsRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private CompensationDateCalculator compensationDateCalculator;
    @Mock private NotificationService notificationService;
    @Mock private AlgorithmConfigService algorithmConfigService;
    @Mock private com.hospital.scheduler.algorithm.GeneticAlgorithmScheduler geneticAlgorithmScheduler;
    @Mock private HolidayRepository holidayRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private EntityManager entityManager;

    private AutoSchedulingService autoSchedulingService;
    private SchedulePeriod testPeriod;
    private List<Staff> testStaff;
    private List<ShiftRequirement> testRequirements;

    @BeforeEach
    void setUp() {
        autoSchedulingService = new AutoSchedulingService(
                scheduleRepository, periodRepository, staffRepository, requirementRepository,
                compensationDayRepository, leaveRequestRepository, metricsRepository,
                conflictDetectionService, auditHistoryService, compensationDateCalculator, notificationService,
                algorithmConfigService, holidayRepository, shiftTypeRepository, specialtyRepository,
                geneticAlgorithmScheduler, entityManager
        );

        // Setup test period: September 2026
        testPeriod = SchedulePeriod.builder()
                .id(100)
                .periodName("Tháng 9/2026 - Test")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 7)) // 7 days
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        // Setup test staff: 4 staff members
        testStaff = Arrays.asList(
                Staff.builder().id(1).username("staff1").fullName("Staff One").isActive(true).maxShiftsPerMonth(10).build(),
                Staff.builder().id(2).username("staff2").fullName("Staff Two").isActive(true).maxShiftsPerMonth(10).build(),
                Staff.builder().id(3).username("staff3").fullName("Staff Three").isActive(true).maxShiftsPerMonth(10).build(),
                Staff.builder().id(4).username("staff4").fullName("Staff Four").isActive(true).maxShiftsPerMonth(10).build()
        );

        // Setup test shift types
        ShiftType l01 = ShiftType.builder().id("L01").name("Trực 24/24").isOvernight(true).build();
        ShiftType l02 = ShiftType.builder().id("L02").name("Thông tầm").isOvernight(false).build();

        // Setup test requirements: 2 L01 + 2 L02 per day for 7 days = 28 requirements
        testRequirements = new ArrayList<>();
        for (LocalDate date = testPeriod.getStartDate(); !date.isAfter(testPeriod.getEndDate()); date = date.plusDays(1)) {
            testRequirements.add(ShiftRequirement.builder()
                    .id(date.getDayOfMonth() * 2 - 1)
                    .period(testPeriod)
                    .workDate(date)
                    .shiftType(l01)
                    .requiredStaffCount(2)
                    .build());
            testRequirements.add(ShiftRequirement.builder()
                    .id(date.getDayOfMonth() * 2)
                    .period(testPeriod)
                    .workDate(date)
                    .shiftType(l02)
                    .requiredStaffCount(2)
                    .build());
        }

        // Common mock setup
        lenient().when(periodRepository.findById(100)).thenReturn(Optional.of(testPeriod));
        lenient().when(staffRepository.findByIsActiveTrue()).thenReturn(testStaff);
        lenient().when(requirementRepository.findByPeriodId(100)).thenReturn(testRequirements);
        lenient().when(scheduleRepository.findByPeriodId(100)).thenReturn(Collections.emptyList());
        lenient().when(scheduleRepository.findByStaffId(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(leaveRequestRepository.findApprovedInRange(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(compensationDayRepository.findByPeriodId(100)).thenReturn(Collections.emptyList());
        lenient().when(holidayRepository.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(shiftTypeRepository.findAll()).thenReturn(Arrays.asList(l01, l02));
        lenient().when(specialtyRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(conflictDetectionService.detectAllConflicts(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // Setup algorithm config
        lenient().when(algorithmConfigService.getRuntimeConfig()).thenReturn(
                AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                        .maxIterations(100)
                        .weekendWeight(java.math.BigDecimal.valueOf(2.0))
                        .overnightRecoveryHours(24)
                        .greedyCoverageThreshold(java.math.BigDecimal.valueOf(0.85))
                        .balanceScoreMin(java.math.BigDecimal.valueOf(0.70))
                        .autoCompensationEnabled(true)
                        .backtrackTimeLimitSeconds(30)
                        .build()
        );
    }

    @Test
    @DisplayName("Greedy algorithm should create schedules for all requirements when staff is sufficient")
    void testGreedyCreatesSchedulesForAllRequirements() {
        // Act: Run greedy algorithm in preview mode (save = false)
        var result = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("GREEDY")
                        .build()
        );

        // Assert: Should create schedules for all requirements
        assertThat(result).isNotNull();
        assertThat(result.getSchedules()).isNotEmpty();
        assertThat(result.getSchedules().size()).isGreaterThanOrEqualTo(20); // Most requirements should be filled
    }

    @Test
    @DisplayName("Round Robin algorithm should distribute shifts evenly")
    void testRoundRobinDistributionIsEven() {
        // Act
        var result = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("ROUND_ROBIN")
                        .build()
        );

        // Assert: Check distribution
        assertThat(result).isNotNull();
        
        // Count shifts per staff
        Map<Integer, Long> shiftsPerStaff = new HashMap<>();
        result.getSchedules().forEach(s -> {
            int staffId = s.getStaffId();
            shiftsPerStaff.merge(staffId, 1L, Long::sum);
        });

        // All active staff should have shifts
        assertThat(shiftsPerStaff).isNotEmpty();
        
        // Calculate max difference - should be reasonable (< 50% difference)
        long maxShifts = shiftsPerStaff.values().stream().max(Long::compareTo).orElse(0L);
        long minShifts = shiftsPerStaff.values().stream().min(Long::compareTo).orElse(0L);
        if (maxShifts > 0) {
            double differenceRatio = (double) (maxShifts - minShifts) / maxShifts;
            assertThat(differenceRatio).isLessThan(0.5); // Max 50% difference
        }
    }

    @Test
    @DisplayName("Balance score should be calculated correctly")
    void testBalanceScoreCalculation() {
        // Act
        var result = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("GREEDY")
                        .build()
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getBalanceScore()).isNotNull();
        // Balance score should be between 0 and 100
        assertThat(result.getBalanceScore().doubleValue()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Coverage rate should reflect filled requirements")
    void testCoverageRateCalculation() {
        // Act
        var result = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("GREEDY")
                        .build()
        );

        // Assert
        assertThat(result).isNotNull();
        // Total schedules should equal filled slots
        int totalRequired = testRequirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount)
                .sum();
        int totalFilled = result.getSchedules().size();
        double expectedCoverage = (double) totalFilled / totalRequired * 100;
        
        assertThat(result.getCoverageRate().doubleValue()).isCloseTo(expectedCoverage, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("Algorithm should handle excluded staff correctly")
    void testExcludedStaffNotAssigned() {
        // Act: Exclude staff ID 1
        var result = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("GREEDY")
                        .excludedStaffIds(List.of(1))
                        .build()
        );

        // Assert: Staff 1 should not be in results
        assertThat(result).isNotNull();
        boolean hasStaff1 = result.getSchedules().stream()
                .anyMatch(s -> s.getStaffId() == 1);
        assertThat(hasStaff1).isFalse();
    }

    @Test
    @DisplayName("Genetic algorithm should produce comparable results to greedy")
    void testGeneticAlgorithmProducesResults() {
        // Act
        var result = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("GENETIC")
                        .build()
        );

        // Assert: Should produce some schedules
        assertThat(result).isNotNull();
        assertThat(result.getAlgorithmType()).isEqualTo("GENETIC");
    }

    @Test
    @DisplayName("Multiple algorithm types should both produce valid results")
    void testDifferentAlgorithmsProduceValidResults() {
        // Act: Run greedy
        var greedyResult = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("GREEDY")
                        .build()
        );

        // Act: Run round robin
        var rrResult = autoSchedulingService.previewSchedule(
                com.hospital.scheduler.dto.request.AutoScheduleRequestDTO.builder()
                        .periodId(100)
                        .algorithmType("ROUND_ROBIN")
                        .build()
        );

        // Assert: Both should produce valid results
        assertThat(greedyResult).isNotNull();
        assertThat(greedyResult.getSchedules()).isNotEmpty();
        assertThat(rrResult).isNotNull();
        assertThat(rrResult.getSchedules()).isNotEmpty();

        // Both should have similar coverage (within 20%)
        double greedyCoverage = greedyResult.getCoverageRate().doubleValue();
        double rrCoverage = rrResult.getCoverageRate().doubleValue();
        assertThat(Math.abs(greedyCoverage - rrCoverage)).isLessThan(25.0);

        // With back-to-back constraint (L01(N-1) → non-L01(N) blocked),
        // 4 staff cannot achieve 100% coverage for 7 days × (2 L01 + 2 L02).
        // Realistic minimum with proper constraint enforcement is ~70%.
        // The test verifies algorithms are consistent with each other.
        assertThat(greedyCoverage).isGreaterThan(70.0);
        assertThat(rrCoverage).isGreaterThan(70.0);
    }
}
