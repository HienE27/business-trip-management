package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.LeaveRequestDTO;
import com.hospital.scheduler.dto.response.LeaveRequestResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LeaveRequestService Tests - Quản lý yêu cầu nghỉ phép")
class LeaveRequestServiceTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private CompensationDayRepository compensationDayRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private Staff testStaff;
    private Staff adminStaff;
    private Staff managerStaff;
    private SchedulePeriod testPeriod;
    private ShiftType shiftL01;

    @BeforeEach
    void setUp() {
        AppRole adminRole = AppRole.builder().id(1).name(RoleName.ADMIN).build();
        AppRole managerRole = AppRole.builder().id(2).name(RoleName.MANAGER).build();
        AppRole staffRoleType = AppRole.builder().id(3).name(RoleName.STAFF).build();

        StaffRole adminSr = StaffRole.builder().staffId(2).roleId(1).build();
        adminSr.setRole(adminRole);
        StaffRole managerSr = StaffRole.builder().staffId(3).roleId(2).build();
        managerSr.setRole(managerRole);
        StaffRole staffSr = StaffRole.builder().staffId(1).roleId(3).build();
        staffSr.setRole(staffRoleType);

        testStaff = Staff.builder()
                .id(1).username("nurse1").fullName("Nguyen Van A").isActive(true)
                .build();
        testStaff.setStaffRoles(java.util.Set.of(staffSr));

        adminStaff = Staff.builder()
                .id(2).username("admin").fullName("Admin User").isActive(true)
                .build();
        adminStaff.setStaffRoles(java.util.Set.of(adminSr));

        managerStaff = Staff.builder()
                .id(3).username("manager").fullName("Manager User").isActive(true)
                .build();
        managerStaff.setStaffRoles(java.util.Set.of(managerSr));

        testPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        shiftL01 = ShiftType.builder()
                .id("L01").name("Lịch trực 24/24").isOvernight(true).build();
    }

    // ==================== getAllLeaveRequests ====================
    @Nested
    @DisplayName("getAllLeaveRequests - Lấy tất cả yêu cầu nghỉ phép")
    class GetAllLeaveRequests {

        @Test
        @DisplayName("Có dữ liệu -> trả về danh sách")
        void hasData_shouldReturnList() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .startDate(LocalDate.of(2026, 6, 10))
                    .endDate(LocalDate.of(2026, 6, 12))
                    .reason("Nghỉ phép cá nhân")
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findAll()).thenReturn(List.of(leave));

            List<LeaveRequestResponse> result = leaveRequestService.getAllLeaveRequests();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStaff().getFullName()).isEqualTo("Nguyen Van A");
        }

        @Test
        @DisplayName("Không có dữ liệu -> trả về danh sách rỗng")
        void noData_shouldReturnEmptyList() {
            when(leaveRequestRepository.findAll()).thenReturn(Collections.emptyList());

            List<LeaveRequestResponse> result = leaveRequestService.getAllLeaveRequests();

            assertThat(result).isEmpty();
        }
    }

    // ==================== getLeaveRequestsByStaff ====================
    @Nested
    @DisplayName("getLeaveRequestsByStaff - Lấy theo nhân sự")
    class GetByStaff {

        @Test
        @DisplayName("Có yêu cầu của nhân sự -> trả về danh sách")
        void hasRequests_shouldReturnList() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .startDate(LocalDate.of(2026, 6, 10))
                    .endDate(LocalDate.of(2026, 6, 12))
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findByStaffId(1)).thenReturn(List.of(leave));

            List<LeaveRequestResponse> result = leaveRequestService.getLeaveRequestsByStaff(1);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Không có yêu cầu -> trả về danh sách rỗng")
        void noRequests_shouldReturnEmptyList() {
            when(leaveRequestRepository.findByStaffId(999)).thenReturn(Collections.emptyList());

            List<LeaveRequestResponse> result = leaveRequestService.getLeaveRequestsByStaff(999);

            assertThat(result).isEmpty();
        }
    }

    // ==================== getPendingRequests ====================
    @Nested
    @DisplayName("getPendingRequests - Lấy yêu cầu đang chờ")
    class GetPending {

        @Test
        @DisplayName("Có yêu cầu PENDING -> trả về danh sách")
        void hasPending_shouldReturnList() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .startDate(LocalDate.of(2026, 6, 10))
                    .endDate(LocalDate.of(2026, 6, 12))
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findPendingRequests()).thenReturn(List.of(leave));

            List<LeaveRequestResponse> result = leaveRequestService.getPendingRequests();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus())
                    .isEqualTo(LeaveRequestResponse.LeaveStatus.PENDING);
        }
    }

    // ==================== getLeaveRequestById ====================
    @Nested
    @DisplayName("getLeaveRequestById - Lấy theo ID")
    class GetById {

        @Test
        @DisplayName("Tồn tại -> trả về response")
        void exists_shouldReturnResponse() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(5).staff(testStaff)
                    .startDate(LocalDate.of(2026, 6, 10))
                    .endDate(LocalDate.of(2026, 6, 12))
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findById(5)).thenReturn(Optional.of(leave));

            LeaveRequestResponse result = leaveRequestService.getLeaveRequestById(5);

            assertThat(result.getId()).isEqualTo(5);
            assertThat(result.getStaff().getFullName()).isEqualTo("Nguyen Van A");
        }

        @Test
        @DisplayName("Không tồn tại -> throw ResourceNotFoundException")
        void notFound_shouldThrow() {
            when(leaveRequestRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.getLeaveRequestById(999))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy yêu cầu nghỉ phép với ID: 999");
        }
    }

    // ==================== createLeaveRequest ====================
    @Nested
    @DisplayName("createLeaveRequest - Tạo yêu cầu nghỉ phép")
    class Create {

        @Test
        @DisplayName("Hợp lệ -> tạo thành công với status PENDING")
        void validRequest_shouldCreateWithPendingStatus() {
            LeaveRequestDTO dto = LeaveRequestDTO.builder()
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(7))
                    .reason("Nghỉ phép cá nhân")
                    .build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(scheduleRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(leaveRequestRepository.save(any(LeaveRequest.class)))
                    .thenAnswer(inv -> {
                        LeaveRequest lr = inv.getArgument(0);
                        lr.setId(10);
                        lr.setCreatedAt(LocalDateTime.now());
                        lr.setUpdatedAt(LocalDateTime.now());
                        return lr;
                    });
            when(staffRepository.findAll()).thenReturn(List.of(adminStaff, managerStaff));

            LeaveRequestResponse result = leaveRequestService.createLeaveRequest(1, dto);

            assertThat(result.getId()).isEqualTo(10);
            assertThat(result.getStatus()).isEqualTo(LeaveRequestResponse.LeaveStatus.PENDING);
            verify(auditHistoryService).logAction(
                    eq("leave_request"), eq(10), eq(AuditHistory.ActionType.INSERT), isNull(), any(), isNull());
        }

        @Test
        @DisplayName("Nhân sự không tồn tại -> throw ResourceNotFoundException")
        void staffNotFound_shouldThrow() {
            when(staffRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.createLeaveRequest(999,
                    LeaveRequestDTO.builder()
                            .startDate(LocalDate.now().plusDays(5))
                            .endDate(LocalDate.now().plusDays(7))
                            .build()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy nhân sự");
        }

        @Test
        @DisplayName("startDate > endDate -> throw BadRequestException")
        void startDateAfterEndDate_shouldThrow() {
            LeaveRequestDTO dto = LeaveRequestDTO.builder()
                    .startDate(LocalDate.now().plusDays(10))
                    .endDate(LocalDate.now().plusDays(5))
                    .build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));

            assertThatThrownBy(() -> leaveRequestService.createLeaveRequest(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ngày bắt đầu phải trước ngày kết thúc");
        }

        @Test
        @DisplayName("startDate trong quá khứ -> throw BadRequestException")
        void startDateInPast_shouldThrow() {
            LeaveRequestDTO dto = LeaveRequestDTO.builder()
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now().plusDays(1))
                    .build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));

            assertThatThrownBy(() -> leaveRequestService.createLeaveRequest(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ngày bắt đầu không được trong quá khứ");
        }

        @Test
        @DisplayName("Trùng với lịch trực -> throw BadRequestException")
        void conflictsWithSchedule_shouldThrow() {
            LeaveRequestDTO dto = LeaveRequestDTO.builder()
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(7))
                    .build();
            Schedule conflict = Schedule.builder()
                    .id(1).staff(testStaff).period(testPeriod).shiftType(shiftL01)
                    .workDate(LocalDate.now().plusDays(6))
                    .build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(scheduleRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(List.of(conflict));

            assertThatThrownBy(() -> leaveRequestService.createLeaveRequest(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("có lịch trực");
        }

        @Test
        @DisplayName("Trùng với ngày nghỉ bù -> throw BadRequestException")
        void conflictsWithCompensationDay_shouldThrow() {
            LeaveRequestDTO dto = LeaveRequestDTO.builder()
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(7))
                    .build();
            CompensationDay compDay = CompensationDay.builder()
                    .id(1).staff(testStaff).compensationDate(LocalDate.now().plusDays(6))
                    .build();
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(scheduleRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(compensationDayRepository.findByStaffIdAndDateRange(eq(1), any(), any()))
                    .thenReturn(List.of(compDay));

            assertThatThrownBy(() -> leaveRequestService.createLeaveRequest(1, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ngày nghỉ bù");
        }
    }

    // ==================== approveLeaveRequest ====================
    @Nested
    @DisplayName("approveLeaveRequest - Duyệt yêu cầu nghỉ phép")
    class Approve {

        @Test
        @DisplayName("PENDING -> APPROVED")
        void pending_shouldApprove() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(7))
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(staffRepository.findById(2)).thenReturn(Optional.of(adminStaff));
            when(leaveRequestRepository.save(any(LeaveRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LeaveRequestResponse result = leaveRequestService.approveLeaveRequest(1, 2, "Đồng ý");

            assertThat(result.getStatus()).isEqualTo(LeaveRequestResponse.LeaveStatus.APPROVED);
            assertThat(result.getReviewNote()).isEqualTo("Đồng ý");
            verify(auditHistoryService).logAction(
                    eq("leave_request"), eq(1), eq(AuditHistory.ActionType.UPDATE), any(), any(), eq(2));
        }

        @Test
        @DisplayName("Đã duyệt rồi -> throw BadRequestException")
        void alreadyApproved_shouldThrow() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.APPROVED)
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(staffRepository.findById(2)).thenReturn(Optional.of(adminStaff));

            assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(1, 2, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("đang chờ");
        }

        @Test
        @DisplayName("Đã từ chối rồi -> throw BadRequestException")
        void alreadyRejected_shouldThrow() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.REJECTED)
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(staffRepository.findById(2)).thenReturn(Optional.of(adminStaff));

            assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(1, 2, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Không tìm thấy người duyệt -> throw ResourceNotFoundException")
        void reviewerNotFound_shouldThrow() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(staffRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(1, 999, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy người duyệt");
        }
    }

    // ==================== rejectLeaveRequest ====================
    @Nested
    @DisplayName("rejectLeaveRequest - Từ chối yêu cầu nghỉ phép")
    class Reject {

        @Test
        @DisplayName("PENDING -> REJECTED")
        void pending_shouldReject() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(7))
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(staffRepository.findById(3)).thenReturn(Optional.of(managerStaff));
            when(leaveRequestRepository.save(any(LeaveRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LeaveRequestResponse result = leaveRequestService.rejectLeaveRequest(1, 3, "Từ chối vì lý do nhân sự");

            assertThat(result.getStatus()).isEqualTo(LeaveRequestResponse.LeaveStatus.REJECTED);
            assertThat(result.getReviewNote()).isEqualTo("Từ chối vì lý do nhân sự");
        }

        @Test
        @DisplayName("Đã duyệt rồi -> throw BadRequestException")
        void alreadyApproved_shouldThrow() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.APPROVED)
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(staffRepository.findById(2)).thenReturn(Optional.of(adminStaff));

            assertThatThrownBy(() -> leaveRequestService.rejectLeaveRequest(1, 2, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("đang chờ");
        }
    }

    // ==================== cancelLeaveRequest ====================
    @Nested
    @DisplayName("cancelLeaveRequest - Hủy yêu cầu nghỉ phép")
    class Cancel {

        @Test
        @DisplayName("Người tạo hủy PENDING -> CANCELLED")
        void requesterCancelsPending_shouldCancel() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(leaveRequestRepository.save(any(LeaveRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LeaveRequestResponse result = leaveRequestService.cancelLeaveRequest(1, testStaff);

            assertThat(result.getStatus()).isEqualTo(LeaveRequestResponse.LeaveStatus.CANCELLED);
        }

        @Test
        @DisplayName("ADMIN hủy PENDING của người khác -> CANCELLED")
        void adminCancelsOthersPending_shouldCancel() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(leaveRequestRepository.save(any(LeaveRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LeaveRequestResponse result = leaveRequestService.cancelLeaveRequest(1, adminStaff);

            assertThat(result.getStatus()).isEqualTo(LeaveRequestResponse.LeaveStatus.CANCELLED);
        }

        @Test
        @DisplayName("MANAGER hủy PENDING của người khác -> CANCELLED")
        void managerCancelsOthersPending_shouldCancel() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));
            when(leaveRequestRepository.save(any(LeaveRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LeaveRequestResponse result = leaveRequestService.cancelLeaveRequest(1, managerStaff);

            assertThat(result.getStatus()).isEqualTo(LeaveRequestResponse.LeaveStatus.CANCELLED);
        }

        @Test
        @DisplayName("STAFF thường hủy của người khác -> throw BadRequestException")
        void staffCannotCancelOthers_shouldThrow() {
            AppRole staffRoleType = AppRole.builder().id(3).name(RoleName.STAFF).build();
            StaffRole staffSr = StaffRole.builder().staffId(5).roleId(3).build();
            staffSr.setRole(staffRoleType);

            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.PENDING)
                    .build();
            Staff otherStaff = Staff.builder()
                    .id(5).username("other").fullName("Other Person").isActive(true)
                    .build();
            otherStaff.setStaffRoles(java.util.Set.of(staffSr));
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(1, otherStaff))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không có quyền hủy");
        }

        @Test
        @DisplayName("Đã duyệt rồi -> throw BadRequestException")
        void alreadyApproved_shouldThrow() {
            LeaveRequest leave = LeaveRequest.builder()
                    .id(1).staff(testStaff)
                    .status(LeaveRequest.LeaveStatus.APPROVED)
                    .build();
            when(leaveRequestRepository.findById(1)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(1, testStaff))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("đang chờ");
        }

        @Test
        @DisplayName("Không tìm thấy yêu cầu -> throw ResourceNotFoundException")
        void notFound_shouldThrow() {
            when(leaveRequestRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(999, testStaff))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
