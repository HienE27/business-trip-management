package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScheduleService Tests - Core scheduling operations")
class ScheduleServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository periodRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private ShiftRequirementRepository requirementRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;
    @Mock private CompensationDateCalculator compensationDateCalculator;
    @Mock private NotificationService notificationService;
    @Mock private com.hospital.scheduler.repository.HolidayRepository holidayRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private Staff testStaff;
    private Staff adminStaff;
    private SchedulePeriod draftPeriod;
    private SchedulePeriod publishedPeriod;
    private ShiftType shiftL01;
    private ShiftType shiftL02;
    private ShiftType shiftL03;
    private Schedule testSchedule;

    @BeforeEach
    void setUp() {
        testStaff = Staff.builder()
                .id(1).username("nurse1").fullName("Nguyen Van A").isActive(true)
                .maxShiftsPerMonth(5)
                .build();
        testStaff.setStaffRoles(new HashSet<>());

        adminStaff = Staff.builder()
                .id(2).username("admin").fullName("Admin User").isActive(true)
                .build();
        adminStaff.setStaffRoles(new HashSet<>());

        draftPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        publishedPeriod = SchedulePeriod.builder()
                .id(2).periodName("Tháng 5/2026")
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 31))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        shiftL01 = ShiftType.builder()
                .id("L01").name("Lịch trực 24/24").isOvernight(true).fatigueScore(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(7, 30))
                .build();

        shiftL02 = ShiftType.builder()
                .id("L02").name("Lịch thông tầm").isOvernight(false).fatigueScore(1)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(17, 30))
                .build();

        shiftL03 = ShiftType.builder()
                .id("L03").name("Phòng khám dịch vụ").isOvernight(false).fatigueScore(2)
                .startTime(java.time.LocalTime.of(8, 0))
                .endTime(java.time.LocalTime.of(16, 0))
                .build();

        testSchedule = Schedule.builder()
                .id(100).period(draftPeriod).workDate(LocalDate.of(2026, 6, 15))
                .staff(testStaff).shiftType(shiftL01).hasConflict(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== getSchedulesByPeriod ====================
    @Nested
    @DisplayName("getSchedulesByPeriod - Lấy lịch theo kỳ")
    class GetSchedulesByPeriod {

        @Test
        @DisplayName("Có dữ liệu -> trả về danh sách")
        void hasData_shouldReturnList() {
            when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(testSchedule));
            when(compensationDayRepository.findByPeriodIdWithStaff(1)).thenReturn(Collections.emptyList());
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<ScheduleResponse> result = scheduleService.getSchedulesByPeriod(1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        }

        @Test
        @DisplayName("Không có dữ liệu -> trả về danh sách rỗng")
        void noData_shouldReturnEmptyList() {
            when(scheduleRepository.findByPeriodId(999)).thenReturn(Collections.emptyList());

            List<ScheduleResponse> result = scheduleService.getSchedulesByPeriod(999);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Có compensation day -> bao gồm trong response")
        void withCompensationDay_shouldIncludeInResponse() {
            CompensationDay compDay = CompensationDay.builder()
                    .id(1).schedule(testSchedule).staff(testStaff).period(draftPeriod)
                    .compensationDate(LocalDate.of(2026, 6, 16))
                    .build();
            when(scheduleRepository.findByPeriodId(1)).thenReturn(List.of(testSchedule));
            when(compensationDayRepository.findByPeriodIdWithStaff(1)).thenReturn(List.of(compDay));
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<ScheduleResponse> result = scheduleService.getSchedulesByPeriod(1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCompensationDate()).isEqualTo(LocalDate.of(2026, 6, 16));
        }
    }

    // ==================== getSchedulesByStaff ====================
    @Nested
    @DisplayName("getSchedulesByStaff - Lấy lịch theo nhân sự")
    class GetSchedulesByStaff {

        @Test
        @DisplayName("Có lịch của nhân sự -> trả về danh sách")
        void hasSchedules_shouldReturnList() {
            when(scheduleRepository.findByStaffId(1)).thenReturn(List.of(testSchedule));
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByScheduleId(anyInt())).thenReturn(Collections.emptyList());

            List<ScheduleResponse> result = scheduleService.getSchedulesByStaff(1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStaff().getFullName()).isEqualTo("Nguyen Van A");
        }

        @Test
        @DisplayName("Nhân sự không có lịch -> trả về danh sách rỗng")
        void noSchedules_shouldReturnEmptyList() {
            when(scheduleRepository.findByStaffId(999)).thenReturn(Collections.emptyList());

            List<ScheduleResponse> result = scheduleService.getSchedulesByStaff(999);

            assertThat(result).isEmpty();
        }
    }

    // ==================== createSchedule ====================
    @Nested
    @DisplayName("createSchedule - Tạo lịch mới")
    class CreateSchedule {

        @Test
        @DisplayName("Hợp lệ, không conflict -> tạo thành công")
        void validRequest_shouldCreate() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 6, 15))
                    .build();

            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(shiftTypeRepository.findById("L02")).thenReturn(Optional.of(shiftL02));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    1, 1, "L02", LocalDate.of(2026, 6, 15))).thenReturn(Optional.empty());
            doNothing().when(conflictDetectionService).validateAndThrow(anyInt(), any(), any(), any(), anyInt());
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> {
                        Schedule s = inv.getArgument(0);
                        s.setId(100);
                        s.setCreatedAt(LocalDateTime.now());
                        s.setUpdatedAt(LocalDateTime.now());
                        return s;
                    });
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);

            ScheduleResponse result = scheduleService.createSchedule(request);

            assertThat(result.getId()).isEqualTo(100);
            verify(auditHistoryService).logAction(eq("schedule"), eq(100), eq(AuditHistory.ActionType.INSERT),
                    isNull(), any(), eq(2));
            verify(notificationService).createNotification(eq(1), any(NotificationDTO.class));
        }

        @Test
        @DisplayName("Kỳ lịch không tồn tại -> throw ResourceNotFoundException")
        void periodNotFound_shouldThrow() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(999).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 6, 15))
                    .build();
            when(periodRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.createSchedule(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy kỳ lịch với ID: 999");
        }

        @Test
        @DisplayName("Nhân sự không tồn tại -> throw ResourceNotFoundException")
        void staffNotFound_shouldThrow() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(999).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 6, 15))
                    .build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(staffRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.createSchedule(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy nhân sự với ID: 999");
        }

        @Test
        @DisplayName("Ngày làm việc ngoài kỳ lịch -> throw BadRequestException")
        void dateOutsidePeriod_shouldThrow() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 7, 15))
                    .build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));

            assertThatThrownBy(() -> scheduleService.createSchedule(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ngày làm việc phải nằm trong kỳ lịch");
        }

        @Test
        @DisplayName("Kỳ lịch đã PUBLISHED -> throw BadRequestException")
        void periodPublished_shouldThrow() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(2).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 5, 15))
                    .build();
            when(periodRepository.findById(2)).thenReturn(Optional.of(publishedPeriod));

            assertThatThrownBy(() -> scheduleService.createSchedule(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("Có xung đột -> throw ConflictException")
        void hasConflict_shouldThrow() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L01")
                    .workDate(LocalDate.of(2026, 6, 15))
                    .build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(shiftTypeRepository.findById("L01")).thenReturn(Optional.of(shiftL01));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    1, 1, "L01", LocalDate.of(2026, 6, 15))).thenReturn(Optional.empty());
            doThrow(new ConflictException("Phát hiện xung đột"))
                    .when(conflictDetectionService).validateAndThrow(anyInt(), any(), any(), any(), anyInt());

            assertThatThrownBy(() -> scheduleService.createSchedule(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Phát hiện xung đột");
        }

        @Test
        @DisplayName("Lịch trùng lặp -> throw ConflictException")
        void duplicateSchedule_shouldThrow() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 6, 15))
                    .build();
            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(shiftTypeRepository.findById("L02")).thenReturn(Optional.of(shiftL02));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    1, 1, "L02", LocalDate.of(2026, 6, 15))).thenReturn(Optional.of(testSchedule));

            assertThatThrownBy(() -> scheduleService.createSchedule(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("đã được phân công ca này");
        }
    }

    // ==================== createSchedule with L01 (Compensation Day) ====================
    @Nested
    @DisplayName("createSchedule - L01 auto-creates compensation day")
    class CreateScheduleWithL01 {

        @Test
        @DisplayName("Tạo L01 -> tự động tạo compensation day")
        void L01_shouldCreateCompensationDay() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L01")
                    .workDate(LocalDate.of(2026, 6, 1))
                    .build();

            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(shiftTypeRepository.findById("L01")).thenReturn(Optional.of(shiftL01));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    1, 1, "L01", LocalDate.of(2026, 6, 1))).thenReturn(Optional.empty());
            doNothing().when(conflictDetectionService).validateAndThrow(anyInt(), any(), any(), any(), anyInt());
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> {
                        Schedule s = inv.getArgument(0);
                        s.setId(100);
                        s.setCreatedAt(LocalDateTime.now());
                        s.setUpdatedAt(LocalDateTime.now());
                        return s;
                    });
            when(compensationDateCalculator.calculate(any())).thenReturn(LocalDate.of(2026, 6, 2));
            when(compensationDayRepository.findByStaffIdAndCompensationDate(1, LocalDate.of(2026, 6, 2)))
                    .thenReturn(Optional.empty());
            when(compensationDayRepository.findByScheduleId(100)).thenReturn(Collections.emptyList());
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);

            ScheduleResponse result = scheduleService.createSchedule(request);

            assertThat(result.getId()).isEqualTo(100);
            verify(compensationDayRepository).save(argThat(cd ->
                    cd.getCompensationDate().equals(LocalDate.of(2026, 6, 2))));
        }

        @Test
        @DisplayName("Tạo L01 -> gửi thông báo với ngày nghỉ bù")
        void L01_shouldSendNotificationWithCompensationDate() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L01")
                    .workDate(LocalDate.of(2026, 6, 1))
                    .build();

            when(periodRepository.findById(1)).thenReturn(Optional.of(draftPeriod));
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(shiftTypeRepository.findById("L01")).thenReturn(Optional.of(shiftL01));
            when(scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    1, 1, "L01", LocalDate.of(2026, 6, 1))).thenReturn(Optional.empty());
            doNothing().when(conflictDetectionService).validateAndThrow(anyInt(), any(), any(), any(), anyInt());
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> {
                        Schedule s = inv.getArgument(0);
                        s.setId(100);
                        s.setCreatedAt(LocalDateTime.now());
                        s.setUpdatedAt(LocalDateTime.now());
                        return s;
                    });
            when(compensationDateCalculator.calculate(any())).thenReturn(LocalDate.of(2026, 6, 2));
            when(compensationDayRepository.findByStaffIdAndCompensationDate(1, LocalDate.of(2026, 6, 2)))
                    .thenReturn(Optional.empty());
            CompensationDay savedCompDay = CompensationDay.builder()
                    .id(1).schedule(testSchedule).staff(testStaff)
                    .compensationDate(LocalDate.of(2026, 6, 2)).build();
            when(compensationDayRepository.findByScheduleId(100)).thenReturn(List.of(savedCompDay));
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);

            scheduleService.createSchedule(request);

            ArgumentCaptor<NotificationDTO> notifCaptor = ArgumentCaptor.forClass(NotificationDTO.class);
            verify(notificationService).createNotification(eq(1), notifCaptor.capture());
            assertThat(notifCaptor.getValue().getMessage()).contains("Ngày nghỉ bù");
        }
    }

    // ==================== updateSchedule ====================
    @Nested
    @DisplayName("updateSchedule - Cập nhật lịch")
    class UpdateSchedule {

        @Test
        @DisplayName("DRAFT period, hợp lệ -> cập nhật thành công")
        void validUpdate_shouldSucceed() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L03")
                    .workDate(LocalDate.of(2026, 6, 16))
                    .build();

            when(scheduleRepository.findById(100)).thenReturn(Optional.of(testSchedule));
            when(shiftTypeRepository.findById("L03")).thenReturn(Optional.of(shiftL03));
            doNothing().when(conflictDetectionService).validateAndThrow(anyInt(), any(), any(), anyInt(), anyInt());
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);
            when(compensationDayRepository.findByScheduleId(100)).thenReturn(Collections.emptyList());
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            ScheduleResponse result = scheduleService.updateSchedule(100, request);

            assertThat(result.getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 16));
            verify(auditHistoryService).logAction(eq("schedule"), eq(100), eq(AuditHistory.ActionType.UPDATE),
                    anyString(), any(), eq(2));
        }

        @Test
        @DisplayName("PUBLISHED period -> throw BadRequestException")
        void publishedPeriod_shouldThrow() {
            Schedule updatedSchedule = Schedule.builder()
                    .id(100).period(publishedPeriod).workDate(LocalDate.of(2026, 5, 15))
                    .staff(testStaff).shiftType(shiftL02).hasConflict(false)
                    .build();
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(2).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 5, 16))
                    .build();
            when(scheduleRepository.findById(100)).thenReturn(Optional.of(updatedSchedule));

            assertThatThrownBy(() -> scheduleService.updateSchedule(100, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("Chuyển L01 -> L02 -> xóa compensation day")
        void changeFromL01ToL02_shouldDeleteCompensationDay() {
            ScheduleRequest request = ScheduleRequest.builder()
                    .periodId(1).staffId(1).shiftTypeId("L02")
                    .workDate(LocalDate.of(2026, 6, 15))
                    .build();

            when(scheduleRepository.findById(100)).thenReturn(Optional.of(testSchedule));
            when(shiftTypeRepository.findById("L02")).thenReturn(Optional.of(shiftL02));
            doNothing().when(conflictDetectionService).validateAndThrow(anyInt(), any(), any(), anyInt(), anyInt());
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);
            when(compensationDayRepository.findByScheduleId(100)).thenReturn(Collections.emptyList());
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            scheduleService.updateSchedule(100, request);

            verify(compensationDayRepository).deleteAll(anyList());
        }
    }

    // ==================== deleteSchedule ====================
    @Nested
    @DisplayName("deleteSchedule - Xóa lịch")
    class DeleteSchedule {

        @Test
        @DisplayName("DRAFT period, L01 -> xóa cả compensation day")
        void deleteL01_shouldDeleteCompensationDay() {
            when(scheduleRepository.findById(100)).thenReturn(Optional.of(testSchedule));
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);
            when(compensationDayRepository.findByScheduleId(100)).thenReturn(Collections.emptyList());

            scheduleService.deleteSchedule(100);

            verify(compensationDayRepository).deleteAll(anyList());
            verify(scheduleRepository).delete(testSchedule);
        }

        @Test
        @DisplayName("PUBLISHED period -> throw BadRequestException")
        void publishedPeriod_shouldThrow() {
            Schedule publishedSchedule = Schedule.builder()
                    .id(100).period(publishedPeriod).workDate(LocalDate.of(2026, 5, 15))
                    .staff(testStaff).shiftType(shiftL02).hasConflict(false)
                    .build();
            when(scheduleRepository.findById(100)).thenReturn(Optional.of(publishedSchedule));

            assertThatThrownBy(() -> scheduleService.deleteSchedule(100))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("Không tìm thấy lịch -> throw ResourceNotFoundException")
        void notFound_shouldThrow() {
            when(scheduleRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.deleteSchedule(999))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== getSchedulesByPeriodAndDate ====================
    @Nested
    @DisplayName("getSchedulesByPeriodAndDate - Lấy lịch theo kỳ và ngày")
    class GetSchedulesByPeriodAndDate {

        @Test
        @DisplayName("Có lịch trong ngày -> trả về danh sách")
        void hasSchedules_shouldReturnList() {
            when(scheduleRepository.findByPeriodIdAndWorkDate(1, LocalDate.of(2026, 6, 15)))
                    .thenReturn(List.of(testSchedule));
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByScheduleId(anyInt())).thenReturn(Collections.emptyList());

            List<ScheduleResponse> result = scheduleService.getSchedulesByPeriodAndDate(
                    1, LocalDate.of(2026, 6, 15));

            assertThat(result).hasSize(1);
        }
    }

    // ==================== overrideConflict ====================
    @Nested
    @DisplayName("overrideConflict - Override xung đột")
    class OverrideConflict {

        @Test
        @DisplayName("Override thành công -> set hasConflict = false")
        void override_shouldSetHasConflictFalse() {
            Schedule conflictSchedule = Schedule.builder()
                    .id(100).period(draftPeriod).workDate(LocalDate.of(2026, 6, 15))
                    .staff(testStaff).shiftType(shiftL01).hasConflict(true)
                    .build();
            when(scheduleRepository.findByIdWithDetails(100)).thenReturn(Optional.of(conflictSchedule));
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> {
                        Schedule s = inv.getArgument(0);
                        s.setId(100);
                        return s;
                    });
            when(authContextService.getCurrentStaff()).thenReturn(adminStaff);
            when(conflictDetectionService.detectAllConflicts(anyInt(), any(), any(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByScheduleId(anyInt())).thenReturn(Collections.emptyList());

            ScheduleResponse result = scheduleService.overrideConflict(100, "Override by manager");

            assertThat(result.getHasConflict()).isFalse();
        }

        @Test
        @DisplayName("Không tìm thấy lịch -> throw ResourceNotFoundException")
        void notFound_shouldThrow() {
            when(scheduleRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.overrideConflict(999, "Test"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
