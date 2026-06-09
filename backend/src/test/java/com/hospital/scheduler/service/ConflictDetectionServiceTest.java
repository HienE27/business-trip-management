package com.hospital.scheduler.service;

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

    @InjectMocks
    private ConflictDetectionService conflictDetectionService;

    private Staff testStaff;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        testStaff = Staff.builder()
                .id(1)
                .username("nurse1")
                .fullName("Nguyen Van A")
                .isActive(true)
                .build();

        monday = LocalDate.of(2026, 6, 1);
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
                            .shiftType(ShiftType.builder().id("L02").name("Lịch thông tầm").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L01", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng với lịch thông tầm"));
        }

        @Test
        @DisplayName("L02 + L01 cùng ngày -> phải REJECT (đảo ngược)")
        void L02AndL01SameDay_shouldReject() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L01").name("Lịch trực 24/24").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L02", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng với lịch trực 24/24"));
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
                            .shiftType(ShiftType.builder().id("L04").name("Phòng khám chuyên gia").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L03", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng với lịch phòng khám chuyên gia"));
        }

        @Test
        @DisplayName("L04 + L03 cùng ngày -> phải REJECT (đảo ngược)")
        void L04AndL03SameDay_shouldReject() {
            List<Schedule> existingSchedules = List.of(
                    Schedule.builder()
                            .id(100)
                            .staff(testStaff)
                            .workDate(monday)
                            .shiftType(ShiftType.builder().id("L03").name("Phòng khám dịch vụ").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    testStaff.getId(), monday, "L04", null);

            assertThat(conflicts)
                    .hasSize(1)
                    .anyMatch(c -> c.contains("Trùng với lịch phòng khám dịch vụ"));
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
                            .shiftType(ShiftType.builder().id("L02").name("Lịch thông tầm").build())
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
                            .shiftType(ShiftType.builder().id("L02").build())
                            .build()
            );
            when(scheduleRepository.findByStaffIdAndWorkDate(testStaff.getId(), monday))
                    .thenReturn(existingSchedules);

            assertThatThrownBy(() -> conflictDetectionService.validateAndThrow(
                    testStaff.getId(), monday, "L01", null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Phát hiện xung đột");
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
}
