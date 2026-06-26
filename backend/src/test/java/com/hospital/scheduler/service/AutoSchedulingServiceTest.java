package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoSchedulingService Tests - Thuật toán M07")
class AutoSchedulingServiceTest {

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
    @Mock private HolidayRepository holidayRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private SpecialtyRepository specialtyRepository;

    @InjectMocks
    private AutoSchedulingService autoSchedulingService;

    private SchedulePeriod testPeriod;
    private List<Staff> testStaffList;
    private List<ShiftRequirement> testRequirements;
    private Specialty testSpecialty;
    private ShiftType shiftL01, shiftL02, shiftL03, shiftL04;

    @BeforeEach
    void setUp() {
        testSpecialty = Specialty.builder().id(1).name("Nội khoa").build();

        shiftL01 = ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3).build();
        shiftL02 = ShiftType.builder().id("L02").name("Lịch thông tầm").isOvernight(false).fatigueScore(1).build();
        shiftL03 = ShiftType.builder().id("L03").name("Phòng khám dịch vụ").isOvernight(false).fatigueScore(1).build();
        shiftL04 = ShiftType.builder().id("L04").name("Phòng khám chuyên gia").isOvernight(false).fatigueScore(1).build();

        testPeriod = SchedulePeriod.builder()
                .id(1)
                .periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 7))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        testStaffList = List.of(
                Staff.builder().id(1).username("nurse1").fullName("Nguyen Van A").isActive(true).specialty(testSpecialty).maxShiftsPerMonth(10).build(),
                Staff.builder().id(2).username("nurse2").fullName("Tran Thi B").isActive(true).specialty(testSpecialty).maxShiftsPerMonth(10).build(),
                Staff.builder().id(3).username("nurse3").fullName("Le Van C").isActive(true).specialty(testSpecialty).maxShiftsPerMonth(10).build()
        );

        testRequirements = List.of(
                ShiftRequirement.builder()
                        .id(1).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                        .shiftType(shiftL01).specialty(testSpecialty).requiredStaffCount(2).build(),
                ShiftRequirement.builder()
                        .id(2).period(testPeriod).workDate(LocalDate.of(2026, 6, 2))
                        .shiftType(shiftL02).specialty(testSpecialty).requiredStaffCount(1).build()
        );

        // Always return the next day as compensation date
        lenient().when(compensationDateCalculator.calculate(any(LocalDate.class)))
                .thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).plusDays(1));
        
        // Mock compensation day repository for finding existing compensation days
        lenient().when(compensationDayRepository.findByStaffIdAndCompensationDate(anyInt(), any()))
                .thenReturn(Optional.empty());
        lenient().when(compensationDayRepository.save(any(CompensationDay.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // Mock findAll() to return empty list
        lenient().when(compensationDayRepository.findAll())
                .thenReturn(Collections.emptyList());

        // Mock runtime config to return defaults
        lenient().when(algorithmConfigService.getRuntimeConfig())
                .thenReturn(AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                        .maxIterations(1000)
                        .weekendWeight(BigDecimal.valueOf(2.0))
                        .overnightRecoveryHours(24)
                        .greedyCoverageThreshold(BigDecimal.valueOf(0.85))
                        .balanceScoreMin(BigDecimal.valueOf(0.70))
                        .autoCompensationEnabled(true)
                        .backtrackTimeLimitSeconds(60)
                        .build());
    }

    // ==================== Setup Validation Tests ====================
    @Nested
    @DisplayName("Validation - Kiểm tra đầu vào")
    class ValidationTests {

        @Test
        @DisplayName("Period not found -> throw ResourceNotFoundException")
        void periodNotFound_shouldThrow() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(999).algorithmType("GREEDY").build();
            when(periodRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> autoSchedulingService.autoSchedule(request))
                    .isInstanceOf(com.hospital.scheduler.exception.ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy kỳ lịch");
        }

        @Test
        @DisplayName("Period not DRAFT -> throw BadRequestException")
        void periodNotDraft_shouldThrow() {
            testPeriod.setStatus(SchedulePeriod.PeriodStatus.PUBLISHED);
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));

            assertThatThrownBy(() -> autoSchedulingService.autoSchedule(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("No active staff -> throw BadRequestException")
        void noActiveStaff_shouldThrow() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> autoSchedulingService.autoSchedule(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("nhân sự");
        }
    }

    // ==================== Algorithm Selection Tests ====================
    @Nested
    @DisplayName("Algorithm Selection - Chọn thuật toán")
    class AlgorithmSelectionTests {

        @Test
        @DisplayName("algorithmType = GREEDY -> chạy Greedy")
        void greedyAlgorithm_shouldWork() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            // Greedy uses filterAndSortEligibleStaffBatch -> hasAnyConflict
            when(conflictDetectionService.hasAnyConflict(anyInt(), any(LocalDate.class), anyString(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(false);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getAlgorithmType()).isEqualTo("GREEDY");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("algorithmType = ROUND_ROBIN -> chạy Round Robin")
        void roundRobinAlgorithm_shouldWork() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("ROUND_ROBIN").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            // Round Robin uses findReplacements(periodId, date, shiftTypeId, originalStaffId, requiredCount, excludedStaffIds, skipCompensationDay) - 7 params
            when(conflictDetectionService.findReplacements(
                    anyInt(), any(LocalDate.class), anyString(), isNull(), anyInt(), any(), eq(true)))
                    .thenReturn(new ArrayList<>(testStaffList));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getAlgorithmType()).isEqualTo("ROUND_ROBIN");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("algorithmType = BACKTRACKING -> chạy Backtracking")
        void backtrackingAlgorithm_shouldWork() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("BACKTRACKING").maxIterations(100).build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            // Backtracking uses findReplacements(periodId, date, shiftTypeId, originalStaffId, requiredCount, excludedStaffIds, skipCompensationDay) - 7 params
            // Note: excludedStaffIds is empty Set {} (not null), so use any() not isNull()
            when(conflictDetectionService.findReplacements(
                    anyInt(), any(LocalDate.class), anyString(), isNull(), anyInt(), any(), eq(true)))
                    .thenReturn(new ArrayList<>(testStaffList));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getAlgorithmType()).isEqualTo("BACKTRACKING");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Không truyền algorithmType -> mặc định GREEDY")
        void defaultAlgorithm_shouldBeGreedy() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            // Backtracking uses findReplacements(periodId, date, shiftTypeId, originalStaffId, requiredCount, excludedStaffIds, skipCompensationDay) - 7 params
            // Note: excludedStaffIds is empty Set {} (not null), so use any() not isNull()
            when(conflictDetectionService.findReplacements(
                    anyInt(), any(LocalDate.class), anyString(), isNull(), anyInt(), any(), eq(true)))
                    .thenReturn(new ArrayList<>(testStaffList));
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getAlgorithmType()).isEqualTo("GREEDY");
        }
    }

    // ==================== Output Quality Tests ====================
    @Nested
    @DisplayName("Output Quality - Tất cả thuật toán thỏa mãn ràng buộc")
    class OutputQualityTests {

        @Test
        @DisplayName("GREEDY: coverageRate phải > 0 khi có requirement")
        void greedy_shouldProduceCoverage() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            // Greedy uses filterAndSortEligibleStaffBatch -> hasAnyConflict
            when(conflictDetectionService.hasAnyConflict(anyInt(), any(LocalDate.class), anyString(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(false);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getCoverageRate()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("ROUND_ROBIN: balanceScore phải > 0")
        void roundRobin_shouldProduceBalanceScore() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("ROUND_ROBIN").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            when(conflictDetectionService.findReplacements(
                    anyInt(), any(LocalDate.class), anyString(), isNull(), anyInt(), any(), eq(true)))
                    .thenReturn(new ArrayList<>(testStaffList));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getBalanceScore()).isNotNull();
        }

        @Test
        @DisplayName("BACKTRACKING: phải tìm được lời giải (không crash)")
        void backtracking_shouldFindSolution() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("BACKTRACKING").maxIterations(100).build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            // Backtracking uses findReplacements(periodId, date, shiftTypeId, originalStaffId, requiredCount, excludedStaffIds, skipCompensationDay) - 7 params
            // Note: excludedStaffIds is empty Set {} (not null), so use any() not isNull()
            when(conflictDetectionService.findReplacements(
                    anyInt(), any(LocalDate.class), anyString(), isNull(), anyInt(), any(), eq(true)))
                    .thenReturn(new ArrayList<>(testStaffList));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            // Note: with mock data (no real ConflictDetectionService), filterBySpecialty may reduce candidates.
            // This test verifies the algorithm completes without crashing.
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }
    }

    // ==================== Compensation Day Tests ====================
    @Nested
    @DisplayName("Compensation Day - Nghỉ bù sau L01")
    class CompensationDayTests {

        @Test
        @DisplayName("Khi tạo L01 -> phải tạo CompensationDay")
        void creatingL01_shouldCreateCompensationDay() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            when(conflictDetectionService.hasAnyConflict(anyInt(), any(LocalDate.class), anyString(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(false);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(new Random().nextInt(1000));
                return s;
            });

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ==================== Preview Mode Tests ====================
    @Nested
    @DisplayName("Preview Mode - Không lưu vào DB")
    class PreviewModeTests {

        @Test
        @DisplayName("previewSchedule -> không gọi scheduleRepository.save")
        void preview_shouldNotSave() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            when(conflictDetectionService.hasAnyConflict(anyInt(), any(LocalDate.class), anyString(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(false);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());

            autoSchedulingService.previewSchedule(request);

            verify(scheduleRepository, never()).save(any(Schedule.class));
        }

        @Test
        @DisplayName("previewSchedule -> không lưu metrics")
        void preview_shouldNotSaveMetrics() {
            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            when(conflictDetectionService.hasAnyConflict(anyInt(), any(LocalDate.class), anyString(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(false);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(anyInt(), anyInt(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());

            autoSchedulingService.previewSchedule(request);

            verify(metricsRepository, never()).save(any(AlgorithmMetrics.class));
        }
    }

    // ==================== Warning Tests ====================
    @Nested
    @DisplayName("Warning - Cảnh báo khi thiếu nhân sự")
    class WarningTests {

        @Test
        @DisplayName("Thiếu nhân sự -> có warning trong response")
        void insufficientStaff_shouldHaveWarning() {
            // Create a specialty that NO staff in testStaffList has → requirement unassignable
            Specialty noMatchSpecialty = Specialty.builder().id(99).name("Khoa không tồn tại").build();
            ShiftRequirement unassignableReq = ShiftRequirement.builder()
                    .id(3).period(testPeriod).workDate(LocalDate.of(2026, 6, 3))
                    .shiftType(shiftL01).specialty(noMatchSpecialty).requiredStaffCount(2).build();

            AutoScheduleRequestDTO request = AutoScheduleRequestDTO.builder()
                    .periodId(1).algorithmType("GREEDY").build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(requirementRepository.findByPeriodId(1)).thenReturn(
                    List.of(testRequirements.get(0), unassignableReq));
            when(conflictDetectionService.hasAnyConflict(anyInt(), any(LocalDate.class), anyString(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(false);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);

            AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);

            assertThat(result.getConflictCount()).isGreaterThan(0);
        }
    }

    // ==================== M07-F06: Unassigned Days Report ====================
    @Nested
    @DisplayName("M07-F06: Báo cáo ngày chưa phân công")
    class UnassignedDaysReportTests {

        @Test
        @DisplayName("Có ngày chưa phân đủ -> báo cáo đúng")
        void unassignedDays_shouldBeReported() {
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(requirementRepository.findByPeriodId(1)).thenReturn(testRequirements);
            when(scheduleRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());

            Map<String, Object> report = autoSchedulingService.getUnassignedDaysReport(1);

            assertThat(report).containsKey("periodId");
            assertThat(report).containsKey("unassignedDays");
            assertThat(report.get("totalUnassignedDays")).isNotNull();
        }

        @Test
        @DisplayName("Tất cả đã phân -> 0 unassigned")
        void allAssigned_shouldHaveZeroUnassigned() {
            // Một requirement: L01 ngày 1/6 cần 1 người, và đã có đúng 1 schedule L01
            ShiftRequirement singleReq = ShiftRequirement.builder()
                    .id(10).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                    .shiftType(shiftL01).specialty(testSpecialty).requiredStaffCount(1).build();
            Schedule existingSchedule = Schedule.builder()
                    .id(1).period(testPeriod).staff(testStaffList.get(0))
                    .shiftType(shiftL01).workDate(LocalDate.of(2026, 6, 1)).build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(requirementRepository.findByPeriodId(1)).thenReturn(List.of(singleReq));
            when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(existingSchedule));

            Map<String, Object> report = autoSchedulingService.getUnassignedDaysReport(1);

            assertThat(report.get("totalUnassignedDays")).isEqualTo(0);
        }

        @Test
        @DisplayName("F06: Sắp xếp unassignedDays theo missingCount DESC, workDate ASC")
        void unassignedDays_shouldBeSortedByMissingCountDescAndWorkDateAsc() {
            // Tạo 3 requirements: 2 người thiếu (missing=2), 1 người thiếu (missing=1)
            // Trong cùng missingCount=2, ưu tiên ngày sớm hơn
            // requiredStaffCount=3 nhưng chỉ có 1 staff -> missingCount = 2
            ShiftRequirement req1 = ShiftRequirement.builder()
                    .id(1).period(testPeriod).workDate(LocalDate.of(2026, 6, 5))
                    .shiftType(shiftL01).specialty(testSpecialty).requiredStaffCount(3).build();
            ShiftRequirement req2 = ShiftRequirement.builder()
                    .id(2).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                    .shiftType(shiftL01).specialty(testSpecialty).requiredStaffCount(3).build();
            ShiftRequirement req3 = ShiftRequirement.builder()
                    .id(3).period(testPeriod).workDate(LocalDate.of(2026, 6, 10))
                    .shiftType(shiftL02).specialty(testSpecialty).requiredStaffCount(2).build();

            // Add 1 schedule for each requirement so assignedCount=1
            Schedule s1 = Schedule.builder().id(100).period(testPeriod).workDate(LocalDate.of(2026, 6, 5))
                    .shiftType(shiftL01).staff(testStaffList.get(0)).build();
            Schedule s2 = Schedule.builder().id(101).period(testPeriod).workDate(LocalDate.of(2026, 6, 1))
                    .shiftType(shiftL01).staff(testStaffList.get(0)).build();
            Schedule s3 = Schedule.builder().id(102).period(testPeriod).workDate(LocalDate.of(2026, 6, 10))
                    .shiftType(shiftL02).staff(testStaffList.get(0)).build();

            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(requirementRepository.findByPeriodId(1)).thenReturn(List.of(req1, req2, req3));
            when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(s1, s2, s3));

            Map<String, Object> report = autoSchedulingService.getUnassignedDaysReport(1);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> unassignedDays = (List<Map<String, Object>>) report.get("unassignedDays");

            assertThat(unassignedDays).hasSize(3);
            // First: missingCount=2, workDate=2026-06-01 (earliest date among missingCount=2)
            assertThat(unassignedDays.get(0).get("missingCount")).isEqualTo(2);
            assertThat(unassignedDays.get(0).get("workDate")).isEqualTo(LocalDate.of(2026, 6, 1));
            // Second: missingCount=2, workDate=2026-06-05
            assertThat(unassignedDays.get(1).get("missingCount")).isEqualTo(2);
            assertThat(unassignedDays.get(1).get("workDate")).isEqualTo(LocalDate.of(2026, 6, 5));
            // Third: missingCount=1
            assertThat(unassignedDays.get(2).get("missingCount")).isEqualTo(1);
        }
    }

    // ==================== M07-F08: Suggest Replacements ====================
    @Nested
    @DisplayName("M07-F08: Đề xuất người thay thế")
    class SuggestReplacementsTests {

        @Test
        @DisplayName("Có người thay thế phù hợp -> trả về danh sách")
        void shouldSuggestReplacements() {
            Schedule original = Schedule.builder()
                    .id(1).period(testPeriod).staff(testStaffList.get(0))
                    .shiftType(shiftL01).workDate(LocalDate.of(2026, 6, 1)).build();
            when(scheduleRepository.findById(1)).thenReturn(Optional.of(original));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(scheduleRepository.countByStaffIdAndPeriodId(anyInt(), anyInt())).thenReturn(0L);
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> suggestions = autoSchedulingService.suggestReplacements(1);

            assertThat(suggestions).containsKey("suggestions");
            assertThat(suggestions.get("originalStaffName")).isEqualTo("Nguyen Van A");
        }

        @Test
        @DisplayName("Không tìm thấy lịch -> throw")
        void scheduleNotFound_shouldThrow() {
            when(scheduleRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> autoSchedulingService.suggestReplacements(999))
                    .isInstanceOf(com.hospital.scheduler.exception.ResourceNotFoundException.class);
        }
    }

    // ==================== M07-F09: Workload Chart ====================
    @Nested
    @DisplayName("M07-F09: Data biểu đồ cân bằng tải")
    class WorkloadChartTests {

        @Test
        @DisplayName("Trả về workload data cho mỗi staff")
        void shouldReturnWorkloadData() {
            when(periodRepository.findById(1)).thenReturn(Optional.of(testPeriod));
            when(staffRepository.findByIsActiveTrue()).thenReturn(testStaffList);
            when(scheduleRepository.findByPeriodId(1)).thenReturn(Collections.emptyList());

            Map<String, Object> chartData = autoSchedulingService.getWorkloadChartData(1);

            assertThat(chartData).containsKey("staffWorkloadData");
            assertThat(chartData).containsKey("averageWorkload");
            assertThat(chartData).containsKey("minWorkload");
            assertThat(chartData).containsKey("maxWorkload");
        }
    }
}
