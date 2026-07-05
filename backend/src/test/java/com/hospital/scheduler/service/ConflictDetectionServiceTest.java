package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.*;
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

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConflictDetectionService Tests - Ràng buộc nghiệp vụ M02-M05")
class ConflictDetectionServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private CompensationDayRepository compensationDayRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleConflictRepository scheduleConflictRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private ShiftTypeRepository shiftTypeRepository;
    @Mock
    private ConflictBroadcastService conflictBroadcastService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private ConflictDetectionService conflictDetectionService;

    private Staff testStaff;
    private LocalDate monday;
    private SchedulePeriod period1;
    private ShiftType shiftL01;
    private ShiftType shiftL02;
    private ShiftType shiftL03;
    private ShiftType shiftL04;

    @BeforeEach
    void setUp() {
        testStaff = Staff.builder()
                .id(1)
                .username("nurse1")
                .fullName("Nguyen Van A")
                .isActive(true)
                .maxShiftsPerMonth(5)
                .build();

        monday = LocalDate.of(2026, 6, 1);

        period1 = SchedulePeriod.builder()
                .id(1)
                .periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        // Mock ShiftTypeRepository to return ShiftType objects with isOvernight set
        shiftL01 = ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).build();
        shiftL02 = ShiftType.builder().id("L02").name("Lịch thông tầm").isOvernight(false).build();
        shiftL03 = ShiftType.builder().id("L03").name("Phòng khám dịch vụ").isOvernight(false).build();
        shiftL04 = ShiftType.builder().id("L04").name("Phòng khám chuyên gia").isOvernight(false).build();

        // Default: no adjacent shifts (back-to-back guard)
        when(scheduleRepository.findByStaffIdAndDateRange(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        when(shiftTypeRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return switch (id) {
                case "L01" -> Optional.of(shiftL01);
                case "L02" -> Optional.of(shiftL02);
                case "L03" -> Optional.of(shiftL03);
                case "L04" -> Optional.of(shiftL04);
                default -> Optional.empty();
            };
        });
    }

    // ==================== M02: L01 vs L02 ====================
    @Nested
    @DisplayName("M02: Lịch trực 24/24 (L01) vs Lịch thông tầm (L02)")
    class L01vsL02Conflict {

        @Test
        @DisplayName("L01 + L02 cùng ngày -> phải REJECT")
        void L01AndL02SameDay_shouldReject() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L02").name("Lịch thông tầm").isOvernight(false).build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng loại ca"));
        }

        @Test
        @DisplayName("L02 + L01 cùng ngày -> phải REJECT (đảo ngược)")
        void L02AndL01SameDay_shouldReject() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L01").name("Lịch trực 24/24").isOvernight(true).build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L02", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng loại ca"));
        }

        @Test
        @DisplayName("L01 + L02 khác ngày -> OK")
        void L01AndL02DifferentDays_shouldPass() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L02").name("Lịch thông tầm").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            LocalDate tuesday = monday.plusDays(1);
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), tuesday))
                    .thenReturn(Collections.emptyList());

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), tuesday, "L01", null);

            assertThat(conflicts).isEmpty();
        }
    }

    // ==================== M03-M04: L03 vs L04 ====================
    @Nested
    @DisplayName("M03-M04: Phòng khám dịch vụ (L03) vs Phòng khám chuyên gia (L04)")
    class L03vsL04Conflict {

        @Test
        @DisplayName("L03 + L04 cùng ngày -> phải REJECT")
        void L03AndL04SameDay_shouldReject() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L04").name("Phòng khám chuyên gia").isOvernight(false).build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L03", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng phòng khám dịch vụ và phòng khám chuyên gia"));
        }

        @Test
        @DisplayName("L04 + L03 cùng ngày -> phải REJECT (đảo ngược)")
        void L04AndL03SameDay_shouldReject() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L03").name("Phòng khám dịch vụ").isOvernight(false).build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L04", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng phòng khám dịch vụ và phòng khám chuyên gia"));
        }

        @Test
        @DisplayName("L03 + L04 khác ngày -> OK")
        void L03AndL04DifferentDays_shouldPass() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L04").name("Phòng khám chuyên gia").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            LocalDate tuesday = monday.plusDays(1);
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), tuesday))
                    .thenReturn(Collections.emptyList());

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), tuesday, "L03", null);

            assertThat(conflicts).isEmpty();
        }
    }

    // ==================== M02: Compensation Day ====================
    @Nested
    @DisplayName("M02: Ngày nghỉ bù (Compensation Day)")
    class CompensationDayConflict {

        @Test
        @DisplayName("Xếp lịch vào ngày nghỉ bù -> REJECT")
        void scheduleOnCompensationDay_shouldReject() {
            LocalDate compensationDate = monday.plusDays(1);
            CompensationDay compDay = CompensationDay.builder()
                    .id(1)
                    .staff(testStaff)
                    .compensationDate(compensationDate)
                    .build();
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), compensationDate))
                    .thenReturn(Optional.of(compDay));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), compensationDate, "L01", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("ngày nghỉ bù"));
        }

        @Test
        @DisplayName("Không có ngày nghỉ bù -> OK")
        void noCompensationDay_shouldPass() {
            LocalDate normalDate = monday.plusDays(3);
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), normalDate))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), normalDate))
                    .thenReturn(Collections.emptyList());

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), normalDate, "L01", null);

            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("L02 vào ngày nghỉ bù -> REJECT")
        void L02OnCompensationDay_shouldReject() {
            LocalDate compensationDate = monday.plusDays(1);
            CompensationDay compDay = CompensationDay.builder()
                    .id(2)
                    .staff(testStaff)
                    .compensationDate(compensationDate)
                    .build();
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), compensationDate))
                    .thenReturn(Optional.of(compDay));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), compensationDate, "L02", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("ngày nghỉ bù"));
        }

        @Test
        @DisplayName("L03 vào ngày nghỉ bù -> REJECT")
        void L03OnCompensationDay_shouldReject() {
            LocalDate compensationDate = monday.plusDays(2);
            CompensationDay compDay = CompensationDay.builder()
                    .id(3)
                    .staff(testStaff)
                    .compensationDate(compensationDate)
                    .build();
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), compensationDate))
                    .thenReturn(Optional.of(compDay));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), compensationDate, "L03", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("ngày nghỉ bù"));
        }

        @Test
        @DisplayName("L04 vào ngày nghỉ bù -> REJECT")
        void L04OnCompensationDay_shouldReject() {
            LocalDate compensationDate = monday.plusDays(3);
            CompensationDay compDay = CompensationDay.builder()
                    .id(4)
                    .staff(testStaff)
                    .compensationDate(compensationDate)
                    .build();
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), compensationDate))
                    .thenReturn(Optional.of(compDay));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), compensationDate, "L04", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("ngày nghỉ bù"));
        }
    }

    // ==================== M01: Nghỉ phép ====================
    @Nested
    @DisplayName("M01: Nghỉ phép đã duyệt (Leave Request)")
    class LeaveRequestConflict {

        @Test
        @DisplayName("Xếp lịch vào ngày nghỉ phép đã duyệt -> REJECT")
        void scheduleOnApprovedLeave_shouldReject() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1)
                    .staff(testStaff)
                    .startDate(monday)
                    .endDate(monday.plusDays(2))
                    .status(LeaveRequest.LeaveStatus.APPROVED)
                    .build();
            when(leaveRequestRepository.findByStaffIdAndDateRange(testStaff.getId(), monday, monday))
                    .thenReturn(List.of(leave));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("nghỉ phép"));
        }

        @Test
        @DisplayName("Xếp lịch vào ngày nghỉ phép CHƯA duyệt -> OK (chờ duyệt)")
        void scheduleOnPendingLeave_shouldPass() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1)
                    .staff(testStaff)
                    .startDate(monday)
                    .endDate(monday.plusDays(2))
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .build();
            when(leaveRequestRepository.findByStaffIdAndDateRange(testStaff.getId(), monday, monday))
                    .thenReturn(List.of(leave));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null);

            assertThat(conflicts).isEmpty();
        }
    }

    // ==================== Multiple Conflicts ====================
    @Nested
    @DisplayName("Multiple conflicts detection")
    class MultipleConflicts {

        @Test
        @DisplayName("Nhiều xung đột cùng lúc -> trả về tất cả")
        void multipleConflicts_shouldReturnAll() {
            LocalDate tuesday = monday.plusDays(1);
            CompensationDay compDay = CompensationDay.builder()
                    .id(1)
                    .staff(testStaff)
                    .compensationDate(tuesday)
                    .build();
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), tuesday))
                    .thenReturn(Optional.of(compDay));

            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(tuesday)
                            .shiftType(ShiftType.builder().id("L02").name("Lịch thông tầm").isOvernight(false).build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), tuesday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), tuesday, "L01", null);

            assertThat(conflicts).hasSize(2);
        }
    }

    // ==================== ValidateAndThrow ====================
    @Nested
    @DisplayName("validateAndThrow - throws ConflictException")
    class ValidateAndThrow {

        @Test
        @DisplayName("Có conflict -> throw ConflictException")
        void hasConflict_shouldThrow() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L02").name("Lịch thông tầm").isOvernight(false).build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            assertThatThrownBy(() -> conflictDetectionService.validateAndThrow(
                    testStaff.getId(), monday, "L01", null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Trùng loại ca");
        }

        @Test
        @DisplayName("Không conflict -> không throw")
        void noConflict_shouldNotThrow() {
            when(compensationDayRepository.findByStaffIdAndCompensationDate(testStaff.getId(), monday))
                    .thenReturn(Optional.empty());
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(Collections.emptyList());

            assertThatCode(() -> conflictDetectionService.validateAndThrow(
                    testStaff.getId(), monday, "L01", null))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== BACK_TO_BACK_SHIFT ====================
    @Nested
    @DisplayName("P2-13: Ràng buộc ca trực liền kề (BACK_TO_BACK_SHIFT)")
    class BackToBackShift {

        @Test
        @DisplayName("Co ca truc lien ke ngay hom truoc -> conflict")
        void adjacentPrevDay_shouldConflict() {
            LocalDate prevDay = monday.minusDays(1);
            Schedule adjacentSchedule = Schedule.builder()
                    .id(200)
                    .staff(testStaff)
                    .workDate(prevDay)
                    .shiftType(shiftL01)
                    .period(period1)
                    .build();

            when(scheduleRepository.findByStaffIdAndDateRangeAndPeriodId(testStaff.getId(), period1.getId(), prevDay, prevDay))
                    .thenReturn(List.of(adjacentSchedule));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null, period1.getId(), false, false);

            assertThat(conflicts)
                    .anyMatch(c -> c.contains("ca trực liền kề"));
        }

        @Test
        @DisplayName("Co ca truc lien ke ngay hom sau -> conflict")
        void adjacentNextDay_shouldConflict() {
            LocalDate nextDay = monday.plusDays(1);
            Schedule adjacentSchedule = Schedule.builder()
                    .id(201)
                    .staff(testStaff)
                    .workDate(nextDay)
                    .shiftType(shiftL01)
                    .period(period1)
                    .build();

            when(scheduleRepository.findByStaffIdAndDateRangeAndPeriodId(testStaff.getId(), period1.getId(), nextDay, nextDay))
                    .thenReturn(List.of(adjacentSchedule));

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null, period1.getId(), false, false);

            assertThat(conflicts)
                    .anyMatch(c -> c.contains("ca trực liền kề"));
        }

        @Test
        @DisplayName("Khong co ca truc lien ke -> OK")
        void noAdjacentShift_shouldPass() {
            when(scheduleRepository.findByStaffIdAndDateRangeAndPeriodId(anyInt(), anyInt(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null, period1.getId(), false, false);

            assertThat(conflicts)
                    .noneMatch(c -> c.contains("ca trực liền kề"));
        }

        @Test
        @DisplayName("Ca truc lien ke la chinh no -> duoc phep khi exclude")
        void excludeSelf_shouldPass() {
            LocalDate prevDay = monday.minusDays(1);
            Schedule sameSchedule = Schedule.builder()
                    .id(100)
                    .staff(testStaff)
                    .workDate(prevDay)
                    .shiftType(shiftL01)
                    .period(period1)
                    .build();

            when(scheduleRepository.findByStaffIdAndDateRangeAndPeriodId(testStaff.getId(), period1.getId(), prevDay, prevDay))
                    .thenReturn(List.of(sameSchedule));

            // excludeScheduleId = 100, same schedule ID -> skip it
            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", 100, period1.getId(), false, false);

            assertThat(conflicts)
                    .noneMatch(c -> c.contains("ca trực liền kề"));
        }
    }

    // ==================== checkPeriodConflicts: periodId scoping ====================
    @Nested
    @DisplayName("checkPeriodConflicts: KHÔNG false-positive khi có schedule từ period khác")
    class CheckPeriodConflictsPeriodScoping {

        /**
         * Bug: detectAllConflicts không nhận periodId → detectBackToBackConflict
         * gọi findByStaffIdAndDateRange (null periodId) → lấy schedule từ MỌI period.
         * → Period July check thấy L01 từ period June → false positive.
         * Fix: truyền periodId → findByStaffIdAndDateRangeAndPeriodId → đúng period.
         */
        @Test
        @DisplayName("Schedule cùng staff, cùng ngày nhưng period khác -> KHÔNG conflict")
        void scheduleSameStaffSameDayDifferentPeriod_noConflict() {
            SchedulePeriod periodJune = SchedulePeriod.builder()
                    .id(10)
                    .periodName("Tháng 6/2026")
                    .startDate(LocalDate.of(2026, 6, 1))
                    .endDate(LocalDate.of(2026, 6, 30))
                    .build();

            SchedulePeriod periodJuly = SchedulePeriod.builder()
                    .id(20)
                    .periodName("Tháng 7/2026")
                    .startDate(LocalDate.of(2026, 7, 1))
                    .endDate(LocalDate.of(2026, 7, 31))
                    .build();

            // L01 ngày 1/7 trong period July
            Schedule scheduleJuly = Schedule.builder()
                    .id(200)
                    .staff(testStaff)
                    .workDate(LocalDate.of(2026, 7, 1))
                    .shiftType(shiftL01)
                    .period(periodJuly)
                    .hasConflict(false)
                    .build();

            // L01 ngày 1/6 trong period June — cùng ngày (1), cùng staff, nhưng period KHÁC
            Schedule scheduleJune = Schedule.builder()
                    .id(100)
                    .staff(testStaff)
                    .workDate(LocalDate.of(2026, 6, 1))
                    .shiftType(shiftL01)
                    .period(periodJune)
                    .build();

            // Khi check period July: findByPeriodId trả về chỉ scheduleJuly
            when(scheduleRepository.findByPeriodId(20))
                    .thenReturn(List.of(scheduleJuly));

            // findApprovedInRange: no leaves
            when(leaveRequestRepository.findApprovedInRange(any(), any()))
                    .thenReturn(Collections.emptyList());

            // findInRange: no compensation days
            when(compensationDayRepository.findInRange(any(), any()))
                    .thenReturn(Collections.emptyList());

            // findByWorkDateWithDetails: trả về cả scheduleJune và scheduleJuly cho ngày 1/6 và 1/7
            // Nhưng batch check phải filter theo periodId → scheduleJuly (period 20) không conflict
            // với scheduleJune (period 10) vì khác period
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 1)))
                    .thenReturn(List.of(scheduleJuly));
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 6, 30)))
                    .thenReturn(List.of(scheduleJune)); // adjacent cho July 1
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 2)))
                    .thenReturn(Collections.emptyList());

            ConflictCheckResponse result = conflictDetectionService.checkPeriodConflicts(20);

            assertThat(result.isHasConflicts()).isFalse();
            assertThat(result.getConflicts()).isEmpty();
        }

        @Test
        @DisplayName("Schedule cùng staff, cùng ngày, cùng period -> CÓ conflict")
        void scheduleSameStaffSameDaySamePeriod_hasConflict() {
            // L01 ngày 1/7 và L02 ngày 1/7 — cùng period → conflict
            Schedule scheduleL01 = Schedule.builder()
                    .id(300)
                    .staff(testStaff)
                    .workDate(LocalDate.of(2026, 7, 1))
                    .shiftType(shiftL01)
                    .period(period1)
                    .hasConflict(false)
                    .build();

            Schedule scheduleL02 = Schedule.builder()
                    .id(301)
                    .staff(testStaff)
                    .workDate(LocalDate.of(2026, 7, 1))
                    .shiftType(shiftL02)
                    .period(period1)
                    .hasConflict(false)
                    .build();

            when(scheduleRepository.findByPeriodId(period1.getId()))
                    .thenReturn(List.of(scheduleL01, scheduleL02));
            when(leaveRequestRepository.findApprovedInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 1)))
                    .thenReturn(List.of(scheduleL01, scheduleL02));
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 6, 30)))
                    .thenReturn(Collections.emptyList());
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 2)))
                    .thenReturn(Collections.emptyList());
            when(scheduleConflictRepository.findByScheduleIdAndIsResolvedFalse(any()))
                    .thenReturn(Collections.emptyList());
            when(scheduleConflictRepository.save(any(ScheduleConflict.class)))
                    .thenAnswer(inv -> {
                        ScheduleConflict c = inv.getArgument(0);
                        c.setId(9002);
                        return c;
                    });

            ConflictCheckResponse result = conflictDetectionService.checkPeriodConflicts(period1.getId());

            assertThat(result.isHasConflicts()).isTrue();
            assertThat(result.getTotalConflicts()).isGreaterThan(0);
        }

        @Test
        @DisplayName("L01 adjacent trong cùng period -> CÓ back-to-back conflict")
        void adjacentL01SamePeriod_hasConflict() {
            Schedule scheduleL01Mon = Schedule.builder()
                    .id(400)
                    .staff(testStaff)
                    .workDate(LocalDate.of(2026, 7, 6)) // Monday
                    .shiftType(shiftL01)
                    .period(period1)
                    .hasConflict(false)
                    .build();

            Schedule adjacentL01 = Schedule.builder()
                    .id(401)
                    .staff(testStaff)
                    .workDate(LocalDate.of(2026, 7, 5)) // Sunday — adjacent
                    .shiftType(shiftL01)
                    .period(period1) // same period
                    .build();

            when(scheduleRepository.findByPeriodId(period1.getId()))
                    .thenReturn(List.of(scheduleL01Mon));
            when(leaveRequestRepository.findApprovedInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 6)))
                    .thenReturn(List.of(scheduleL01Mon));
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 5)))
                    .thenReturn(List.of(adjacentL01));
            when(scheduleRepository.findByWorkDateWithDetails(LocalDate.of(2026, 7, 7)))
                    .thenReturn(Collections.emptyList());
            when(scheduleConflictRepository.findByScheduleIdAndIsResolvedFalse(any()))
                    .thenReturn(Collections.emptyList());
            when(scheduleConflictRepository.save(any(ScheduleConflict.class)))
                    .thenAnswer(inv -> {
                        ScheduleConflict c = inv.getArgument(0);
                        c.setId(9003);
                        return c;
                    });

            ConflictCheckResponse result = conflictDetectionService.checkPeriodConflicts(period1.getId());

            assertThat(result.isHasConflicts()).isTrue();
            assertThat(result.getConflicts()).anyMatch(c ->
                    c.getConflictReasons().stream()
                            .anyMatch(r -> r.contains("liền kề")));
        }
    }

    // ==================== checkPeriodConflicts broadcast dedupe ====================
    @Nested
    @DisplayName("checkPeriodConflicts: chỉ broadcast khi conflict mới")
    class CheckPeriodConflictsBroadcast {

        @Test
        @DisplayName("Conflict mới trên schedule -> broadcast 1 lần")
        void newConflict_shouldBroadcastOnce() {
            Schedule schedule = Schedule.builder()
                    .id(500)
                    .staff(testStaff)
                    .workDate(monday)
                    .shiftType(shiftL01)
                    .period(period1)
                    .hasConflict(false)
                    .build();

            // Adjacent schedule triggers a back-to-back conflict regardless
            // of max-shifts accounting (which only triggers when periodId is
            // threaded through detectAllConflicts).
            Schedule adjacentSchedule = Schedule.builder()
                    .id(501)
                    .staff(testStaff)
                    .workDate(monday.minusDays(1))
                    .shiftType(shiftL01)
                    .period(period1)
                    .build();

            when(scheduleRepository.findByPeriodId(period1.getId()))
                    .thenReturn(List.of(schedule));
            // Batch methods (new implementation — no O(N) individual queries)
            when(leaveRequestRepository.findApprovedInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduleRepository.findByWorkDateWithDetails(monday))
                    .thenReturn(List.of(schedule));
            when(scheduleRepository.findByWorkDateWithDetails(monday.minusDays(1)))
                    .thenReturn(List.of(adjacentSchedule));
            when(scheduleRepository.findByWorkDateWithDetails(monday.plusDays(1)))
                    .thenReturn(Collections.emptyList());
            when(scheduleConflictRepository.findByScheduleIdAndIsResolvedFalse(schedule.getId()))
                    .thenReturn(Collections.emptyList()); // no prior conflict -> new
            when(scheduleConflictRepository.save(any(ScheduleConflict.class)))
                    .thenAnswer(inv -> {
                        ScheduleConflict c = inv.getArgument(0);
                        c.setId(9001);
                        return c;
                    });

            conflictDetectionService.checkPeriodConflicts(period1.getId());

            // new conflict -> exactly one broadcast
            verify(conflictBroadcastService, times(1))
                    .broadcastConflict(any(ScheduleConflict.class), any());
        }

        @Test
        @DisplayName("Conflict đã tồn tại (unresolved) -> KHÔNG broadcast lại")
        void existingConflict_shouldNotBroadcastAgain() {
            Schedule schedule = Schedule.builder()
                    .id(502)
                    .staff(testStaff)
                    .workDate(monday)
                    .shiftType(shiftL01)
                    .period(period1)
                    .hasConflict(true)
                    .build();

            ScheduleConflict preExisting = ScheduleConflict.builder()
                    .id(8001)
                    .schedule(schedule)
                    .conflictType(ScheduleConflict.ConflictType.OTHER)
                    .description("pre-existing")
                    .isResolved(false)
                    .build();

            // Same adjacent-shift trigger as above so detectAllConflicts finds
            // a conflict — but the schedule already has an unresolved conflict
            // so the broadcast must be skipped.
            Schedule adjacentSchedule = Schedule.builder()
                    .id(503)
                    .staff(testStaff)
                    .workDate(monday.minusDays(1))
                    .shiftType(shiftL01)
                    .period(period1)
                    .build();

            when(scheduleRepository.findByPeriodId(period1.getId()))
                    .thenReturn(List.of(schedule));
            // Batch methods (new implementation — no O(N) individual queries)
            when(leaveRequestRepository.findApprovedInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findInRange(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduleRepository.findByWorkDateWithDetails(monday))
                    .thenReturn(List.of(schedule));
            when(scheduleRepository.findByWorkDateWithDetails(monday.minusDays(1)))
                    .thenReturn(List.of(adjacentSchedule));
            when(scheduleRepository.findByWorkDateWithDetails(monday.plusDays(1)))
                    .thenReturn(Collections.emptyList());
            when(scheduleConflictRepository.findByScheduleIdAndIsResolvedFalse(schedule.getId()))
                    .thenReturn(List.of(preExisting));

            conflictDetectionService.checkPeriodConflicts(period1.getId());

            // re-running the check on a schedule that already has an unresolved
            // conflict must NOT spam a duplicate WebSocket notification.
            verify(conflictBroadcastService, never()).broadcastConflict(any(), any());
        }
    }
}
