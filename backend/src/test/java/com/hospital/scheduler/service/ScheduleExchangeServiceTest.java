package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleExchangeDTO;
import com.hospital.scheduler.dto.response.ScheduleExchangeResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
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
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScheduleExchangeService Tests - Đổi trực giữa nhân sự")
class ScheduleExchangeServiceTest {

    @Mock private ScheduleExchangeRepository exchangeRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private ConflictDetectionService conflictDetectionService;
    @Mock private CompensationDateCalculator compensationDateCalculator;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ScheduleExchangeService exchangeService;

    private Staff staffA;
    private Staff staffB;
    private SchedulePeriod testPeriod;
    private Schedule scheduleA;
    private Schedule scheduleB;
    private ScheduleExchange testExchange;

    @BeforeEach
    void setUp() {
        testPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.PUBLISHED)
                .build();

        staffA = Staff.builder()
                .id(1).username("nurseA").fullName("Nguyen Van A").isActive(true).build();
        staffB = Staff.builder()
                .id(2).username("nurseB").fullName("Tran Thi B").isActive(true).build();

        ShiftType shiftL01 = ShiftType.builder()
                .id("L01").name("Lịch trực 24/24").isOvernight(true).build();

        scheduleA = Schedule.builder()
                .id(10).period(testPeriod).staff(staffA).shiftType(shiftL01)
                .workDate(LocalDate.of(2026, 6, 5))
                .build();
        scheduleB = Schedule.builder()
                .id(20).period(testPeriod).staff(staffB).shiftType(shiftL01)
                .workDate(LocalDate.of(2026, 6, 10))
                .build();

        testExchange = ScheduleExchange.builder()
                .id(1).period(testPeriod)
                .requester(staffA).target(staffB)
                .requesterSchedule(scheduleA).targetSchedule(scheduleB)
                .reason("Muốn đổi ngày trực")
                .status(ScheduleExchange.ExchangeStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== getAllExchanges ====================
    @Nested
    @DisplayName("getAllExchanges - Lấy tất cả yêu cầu đổi ca")
    class GetAllExchanges {

        @Test
        @DisplayName("Có dữ liệu -> trả về danh sách")
        void hasData_shouldReturnList() {
            when(exchangeRepository.findAll()).thenReturn(List.of(testExchange));

            List<ScheduleExchangeResponse> result = exchangeService.getAllExchanges();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRequester().getFullName()).isEqualTo("Nguyen Van A");
            assertThat(result.get(0).getStatus())
                    .isEqualTo(ScheduleExchangeResponse.ExchangeStatus.PENDING);
        }

        @Test
        @DisplayName("Không có dữ liệu -> trả về danh sách rỗng")
        void noData_shouldReturnEmptyList() {
            when(exchangeRepository.findAll()).thenReturn(Collections.emptyList());

            List<ScheduleExchangeResponse> result = exchangeService.getAllExchanges();

            assertThat(result).isEmpty();
        }
    }

    // ==================== getPendingExchanges ====================
    @Nested
    @DisplayName("getPendingExchanges - Lấy yêu cầu đang chờ")
    class GetPendingExchanges {

        @Test
        @DisplayName("Có PENDING -> trả về danh sách")
        void hasPending_shouldReturnList() {
            when(exchangeRepository.findByStatus(ScheduleExchange.ExchangeStatus.PENDING))
                    .thenReturn(List.of(testExchange));

            List<ScheduleExchangeResponse> result = exchangeService.getPendingExchanges();

            assertThat(result).hasSize(1);
        }
    }

    // ==================== getExchangeById ====================
    @Nested
    @DisplayName("getExchangeById - Lấy theo ID")
    class GetById {

        @Test
        @DisplayName("Tồn tại -> trả về response")
        void exists_shouldReturnResponse() {
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));

            ScheduleExchangeResponse result = exchangeService.getExchangeById(1);

            assertThat(result.getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("Không tồn tại -> throw ResourceNotFoundException")
        void notFound_shouldThrow() {
            when(exchangeRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> exchangeService.getExchangeById(999))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== createExchange ====================
    @Nested
    @DisplayName("createExchange - Tạo yêu cầu đổi ca")
    class CreateExchange {

        @Test
        @DisplayName("Hợp lệ với L01 -> tạo thành công")
        void validL01Exchange_shouldCreate() {
            ScheduleExchangeDTO dto = ScheduleExchangeDTO.builder()
                    .periodId(1)
                    .requesterScheduleId(10)
                    .targetScheduleId(20)
                    .reason("Muốn đổi ngày trực")
                    .build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(staffA));
            when(scheduleRepository.findById(10)).thenReturn(Optional.of(scheduleA));
            when(scheduleRepository.findById(20)).thenReturn(Optional.of(scheduleB));
            when(exchangeRepository.save(any(ScheduleExchange.class)))
                    .thenAnswer(inv -> {
                        ScheduleExchange e = inv.getArgument(0);
                        e.setId(5);
                        e.setCreatedAt(LocalDateTime.now());
                        e.setUpdatedAt(LocalDateTime.now());
                        return e;
                    });

            ScheduleExchangeResponse result = exchangeService.createExchange(1, dto);

            assertThat(result.getId()).isEqualTo(5);
            assertThat(result.getStatus()).isEqualTo(ScheduleExchangeResponse.ExchangeStatus.PENDING);
            verify(auditHistoryService).logAction(
                    eq("schedule_exchange"), eq(5), eq(AuditHistory.ActionType.INSERT), isNull(), any(), eq(1));
        }

        @Test
        @DisplayName("Lịch yêu cầu không thuộc người gửi -> throw BadRequestException")
        void scheduleNotOwnedByRequester_shouldThrow() {
            // staffB (id=2) owns scheduleB (id=20), but tries to create exchange with scheduleA (id=10, owned by staffA)
            ScheduleExchangeDTO dto = ScheduleExchangeDTO.builder()
                    .periodId(1).requesterScheduleId(10).targetScheduleId(20).build();
            when(staffRepository.findById(2)).thenReturn(Optional.of(staffB)); // staffB requests
            when(scheduleRepository.findById(10)).thenReturn(Optional.of(scheduleA)); // scheduleA belongs to staffA
            when(scheduleRepository.findById(20)).thenReturn(Optional.of(scheduleB));

            assertThatThrownBy(() -> exchangeService.createExchange(2, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không thuộc về người gửi");
        }

        @Test
        @DisplayName("Đổi trực với chính mình -> throw BadRequestException")
        void swapWithSelf_shouldThrow() {
            ScheduleExchangeDTO dto = ScheduleExchangeDTO.builder()
                    .periodId(1).requesterScheduleId(10).targetScheduleId(10).build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(staffA));
            when(scheduleRepository.findById(10)).thenReturn(Optional.of(scheduleA));

            assertThatThrownBy(() -> exchangeService.createExchange(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("chính mình");
        }

        @Test
        @DisplayName("Kỳ lịch chưa công bố -> throw BadRequestException")
        void periodNotPublished_shouldThrow() {
            testPeriod.setStatus(SchedulePeriod.PeriodStatus.DRAFT);
            ScheduleExchangeDTO dto = ScheduleExchangeDTO.builder()
                    .periodId(1).requesterScheduleId(10).targetScheduleId(20).build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(staffA));
            when(scheduleRepository.findById(10)).thenReturn(Optional.of(scheduleA));
            when(scheduleRepository.findById(20)).thenReturn(Optional.of(scheduleB));

            assertThatThrownBy(() -> exchangeService.createExchange(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("chưa được công bố");
        }

        @Test
        @DisplayName("Cả hai không phải L01 -> throw BadRequestException")
        void neitherIsL01_shouldThrow() {
            testPeriod.setStatus(SchedulePeriod.PeriodStatus.PUBLISHED);
            ShiftType shiftL02 = ShiftType.builder()
                    .id("L02").name("Lịch thông tầm").isOvernight(false).build();
            Schedule nonL01ScheduleA = Schedule.builder()
                    .id(10).period(testPeriod).staff(staffA).shiftType(shiftL02)
                    .workDate(LocalDate.of(2026, 6, 5)).build();
            Schedule nonL01ScheduleB = Schedule.builder()
                    .id(20).period(testPeriod).staff(staffB).shiftType(shiftL02)
                    .workDate(LocalDate.of(2026, 6, 10)).build();

            ScheduleExchangeDTO dto = ScheduleExchangeDTO.builder()
                    .periodId(1).requesterScheduleId(10).targetScheduleId(20).build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(staffA));
            when(scheduleRepository.findById(10)).thenReturn(Optional.of(nonL01ScheduleA));
            when(scheduleRepository.findById(20)).thenReturn(Optional.of(nonL01ScheduleB));

            assertThatThrownBy(() -> exchangeService.createExchange(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("L01");
        }

        @Test
        @DisplayName("Một là L01 một không -> hợp lệ (đổi với ca L01)")
        void oneIsL01_shouldSucceed() {
            ShiftType shiftL02 = ShiftType.builder()
                    .id("L02").name("Lịch thông tầm").isOvernight(false).build();
            Schedule nonL01ScheduleA = Schedule.builder()
                    .id(10).period(testPeriod).staff(staffA).shiftType(shiftL02)
                    .workDate(LocalDate.of(2026, 6, 5)).build();

            ScheduleExchangeDTO dto = ScheduleExchangeDTO.builder()
                    .periodId(1).requesterScheduleId(10).targetScheduleId(20).build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(staffA));
            when(scheduleRepository.findById(10)).thenReturn(Optional.of(nonL01ScheduleA));
            when(scheduleRepository.findById(20)).thenReturn(Optional.of(scheduleB));
            when(exchangeRepository.save(any(ScheduleExchange.class)))
                    .thenAnswer(inv -> {
                        ScheduleExchange e = inv.getArgument(0);
                        e.setId(5);
                        e.setCreatedAt(LocalDateTime.now());
                        e.setUpdatedAt(LocalDateTime.now());
                        return e;
                    });

            ScheduleExchangeResponse result = exchangeService.createExchange(1, dto);

            assertThat(result.getId()).isEqualTo(5);
        }
    }

    // ==================== approveExchange ====================
    @Nested
    @DisplayName("approveExchange - Duyệt đổi ca")
    class ApproveExchange {

        @Test
        @DisplayName("PENDING, không conflict -> APPROVED, staff đổi chỗ nhau")
        void validApproval_shouldApproveAndSwapStaff() {
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(2), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(2), any()))
                    .thenReturn(Optional.empty());
            when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(1), any()))
                    .thenReturn(Optional.empty());
            when(conflictDetectionService.detectAllConflicts(eq(1), any(), anyString(), any()))
                    .thenReturn(Collections.emptyList());
            when(conflictDetectionService.detectAllConflicts(eq(2), any(), anyString(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.save(any(CompensationDay.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(scheduleRepository.save(any(Schedule.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(exchangeRepository.save(any(ScheduleExchange.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ScheduleExchangeResponse result = exchangeService.approveExchange(1, 3, "Đồng ý đổi");

            assertThat(result.getStatus()).isEqualTo(ScheduleExchangeResponse.ExchangeStatus.APPROVED);

            // Verify staff swapped
            ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
            verify(scheduleRepository, times(2)).save(captor.capture());
            List<Schedule> savedSchedules = captor.getAllValues();
            assertThat(savedSchedules).extracting(s -> s.getStaff().getId())
                    .containsExactlyInAnyOrder(2, 1); // staffA got scheduleB's date, staffB got scheduleA's date
        }

        @Test
        @DisplayName("Không phải PENDING -> throw BadRequestException")
        void notPending_shouldThrow() {
            testExchange.setStatus(ScheduleExchange.ExchangeStatus.APPROVED);
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));

            assertThatThrownBy(() -> exchangeService.approveExchange(1, 3, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("đang chờ");
        }

        @Test
        @DisplayName("Target có nghỉ phép đã duyệt vào ngày trực -> throw BadRequestException")
        void targetHasApprovedLeave_shouldThrow() {
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            LeaveRequest approvedLeave = LeaveRequest.builder()
                    .id(1).staff(staffB)
                    .startDate(LocalDate.of(2026, 6, 10))
                    .endDate(LocalDate.of(2026, 6, 10))
                    .status(LeaveRequest.LeaveStatus.APPROVED)
                    .build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(2), any(), any()))
                    .thenReturn(List.of(approvedLeave));

            assertThatThrownBy(() -> exchangeService.approveExchange(1, 3, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("nghỉ phép được duyệt");
        }

        @Test
        @DisplayName("Requester có ngày nghỉ bù vào ngày trực của target -> throw BadRequestException")
        void requesterHasCompensationDay_shouldThrow() {
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            CompensationDay compDay = CompensationDay.builder()
                    .id(1).staff(staffA).compensationDate(LocalDate.of(2026, 6, 10))
                    .build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(2), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(2), any()))
                    .thenReturn(Optional.empty());
            when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(1), any()))
                    .thenReturn(Optional.of(compDay));

            assertThatThrownBy(() -> exchangeService.approveExchange(1, 3, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ngày nghỉ bù");
        }

        @Test
        @DisplayName("Đổi xong requester bị conflict -> throw BadRequestException")
        void requesterHasConflictAfterSwap_shouldThrow() {
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(2), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(leaveRequestRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(2), any()))
                    .thenReturn(Optional.empty());
            when(compensationDayRepository.findByStaffIdAndCompensationDate(eq(1), any()))
                    .thenReturn(Optional.empty());
            when(conflictDetectionService.detectAllConflicts(eq(1), any(), anyString(), any()))
                    .thenReturn(List.of("Trùng với lịch thông tầm")); // requester has conflict after swap

            assertThatThrownBy(() -> exchangeService.approveExchange(1, 3, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("bị xung đột sau khi đổi");
        }

        @Test
        @DisplayName("Kỳ lịch quay về DRAFT -> throw BadRequestException")
        void periodBackToDraft_shouldThrow() {
            testPeriod.setStatus(SchedulePeriod.PeriodStatus.DRAFT);
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));

            assertThatThrownBy(() -> exchangeService.approveExchange(1, 3, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("chưa được công bố");
        }
    }

    // ==================== rejectExchange ====================
    @Nested
    @DisplayName("rejectExchange - Từ chối đổi ca")
    class RejectExchange {

        @Test
        @DisplayName("PENDING -> REJECTED")
        void pending_shouldReject() {
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));
            when(exchangeRepository.save(any(ScheduleExchange.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ScheduleExchangeResponse result = exchangeService.rejectExchange(1, 3, "Từ chối vì lý do nhân sự");

            assertThat(result.getStatus()).isEqualTo(ScheduleExchangeResponse.ExchangeStatus.REJECTED);
            assertThat(result.getReviewNote()).isEqualTo("Từ chối vì lý do nhân sự");
        }

        @Test
        @DisplayName("Đã duyệt rồi -> throw BadRequestException")
        void alreadyApproved_shouldThrow() {
            testExchange.setStatus(ScheduleExchange.ExchangeStatus.APPROVED);
            Staff reviewer = Staff.builder().id(3).username("manager").fullName("Manager").build();
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(staffRepository.findById(3)).thenReturn(Optional.of(reviewer));

            assertThatThrownBy(() -> exchangeService.rejectExchange(1, 3, null))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ==================== cancelExchange ====================
    @Nested
    @DisplayName("cancelExchange - Hủy yêu cầu đổi ca")
    class CancelExchange {

        @Test
        @DisplayName("Người yêu cầu hủy -> CANCELLED")
        void requesterCancels_shouldCancel() {
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(exchangeRepository.save(any(ScheduleExchange.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ScheduleExchangeResponse result = exchangeService.cancelExchange(1, staffA);

            assertThat(result.getStatus()).isEqualTo(ScheduleExchangeResponse.ExchangeStatus.CANCELLED);
        }

        @Test
        @DisplayName("Người được đổi hủy -> CANCELLED")
        void targetCancels_shouldCancel() {
            when(exchangeRepository.findById(1)).thenReturn(Optional.of(testExchange));
            when(exchangeRepository.save(any(ScheduleExchange.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ScheduleExchangeResponse result = exchangeService.cancelExchange(1, staffB);

            assertThat(result.getStatus()).isEqualTo(ScheduleExchangeResponse.ExchangeStatus.CANCELLED);
        }
    }
}
