package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.LeaveRequestDTO;
import com.hospital.scheduler.dto.response.LeaveRequestResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.dto.request.NotificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final StaffRepository staffRepository;
    private final AuditHistoryService auditHistoryService;
    private final ScheduleRepository scheduleRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final NotificationService notificationService;

    public List<LeaveRequestResponse> getAllLeaveRequests() {
        return leaveRequestRepository.findAll().stream()
                .map(LeaveRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LeaveRequestResponse> getLeaveRequestsByStaff(Integer staffId) {
        return leaveRequestRepository.findByStaffId(staffId).stream()
                .map(LeaveRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LeaveRequestResponse> getPendingRequests() {
        return leaveRequestRepository.findPendingRequests().stream()
                .map(LeaveRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LeaveRequestResponse> getLeaveRequestsByStatus(LeaveRequest.LeaveStatus status) {
        return leaveRequestRepository.findByStatus(status).stream()
                .map(LeaveRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public LeaveRequestResponse getLeaveRequestById(Integer id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nghỉ phép với ID: " + id));
        return LeaveRequestResponse.fromEntity(leaveRequest);
    }

    public LeaveRequestResponse createLeaveRequest(Integer staffId, LeaveRequestDTO dto) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + staffId));

        validateLeaveRequest(staffId, dto);
        validateNoScheduleConflict(staff, dto.getStartDate(), dto.getEndDate());

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .staff(staff)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .reason(dto.getReason())
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditHistoryService.logAction("leave_request", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);

        // Notify managers about the new leave request
        List<Staff> managers = staffRepository.findAll().stream()
                .filter(s -> s.getStaffRoles().stream()
                        .anyMatch(sr -> sr.getRole() != null &&
                                ("ADMIN".equals(sr.getRole().getName()) || "MANAGER".equals(sr.getRole().getName()))))
                .collect(Collectors.toList());
        for (Staff manager : managers) {
            notificationService.createNotification(manager.getId(),
                    new NotificationDTO("Yêu cầu nghỉ phép mới",
                            "Nhân sự " + staff.getFullName() + " gửi yêu cầu nghỉ phép từ " + dto.getStartDate() + " đến " + dto.getEndDate()));
        }

        return LeaveRequestResponse.fromEntity(saved);
    }

    public LeaveRequestResponse approveLeaveRequest(Integer leaveRequestId, Integer reviewerId, String reviewNote) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nghỉ phép với ID: " + leaveRequestId));

        Staff reviewer = staffRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người duyệt với ID: " + reviewerId));

        if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt yêu cầu đang chờ");
        }

        LeaveRequest prev = leaveRequest;
        leaveRequest.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        leaveRequest.setReviewedBy(reviewer);
        leaveRequest.setReviewedAt(LocalDateTime.now());
        leaveRequest.setReviewNote(reviewNote);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditHistoryService.logAction("leave_request", leaveRequestId, AuditHistory.ActionType.UPDATE, prev, saved, reviewerId);

        // Notify the staff about approval
        notificationService.createNotification(leaveRequest.getStaff().getId(),
                new NotificationDTO("Yêu cầu nghỉ phép đã được duyệt",
                        "Yêu cầu nghỉ phép của bạn từ " + leaveRequest.getStartDate() + " đến " + leaveRequest.getEndDate() + " đã được duyệt bởi " + reviewer.getFullName() + "."));

        return LeaveRequestResponse.fromEntity(saved);
    }

    public LeaveRequestResponse rejectLeaveRequest(Integer leaveRequestId, Integer reviewerId, String reviewNote) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nghỉ phép với ID: " + leaveRequestId));

        Staff reviewer = staffRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người duyệt với ID: " + reviewerId));

        if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể từ chối yêu cầu đang chờ");
        }

        LeaveRequest prev = leaveRequest;
        leaveRequest.setStatus(LeaveRequest.LeaveStatus.REJECTED);
        leaveRequest.setReviewedBy(reviewer);
        leaveRequest.setReviewedAt(LocalDateTime.now());
        leaveRequest.setReviewNote(reviewNote);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditHistoryService.logAction("leave_request", leaveRequestId, AuditHistory.ActionType.UPDATE, prev, saved, reviewerId);

        // Notify the staff about rejection
        String rejectMsg = "Yêu cầu nghỉ phép của bạn từ " + leaveRequest.getStartDate() + " đến " + leaveRequest.getEndDate() + " đã bị từ chối bởi " + reviewer.getFullName()
                + (reviewNote != null && !reviewNote.isBlank() ? ". Lý do: " + reviewNote : "");
        notificationService.createNotification(leaveRequest.getStaff().getId(),
                new NotificationDTO("Yêu cầu nghỉ phép bị từ chối", rejectMsg));

        return LeaveRequestResponse.fromEntity(saved);
    }

    public LeaveRequestResponse cancelLeaveRequest(Integer leaveRequestId, Staff currentStaff) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nghỉ phép với ID: " + leaveRequestId));

        boolean canCancel = leaveRequest.getStaff().getId().equals(currentStaff.getId())
                || currentStaff.getStaffRoles().stream()
                .map(role -> role.getRole() != null ? role.getRole().getName() : null)
                .anyMatch(roleName -> "ADMIN".equals(roleName) || "MANAGER".equals(roleName));
        if (!canCancel) {
            throw new BadRequestException("Bạn không có quyền hủy yêu cầu nghỉ phép này");
        }

        if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy yêu cầu đang chờ");
        }

        LeaveRequest prev = leaveRequest;
        leaveRequest.setStatus(LeaveRequest.LeaveStatus.CANCELLED);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditHistoryService.logAction("leave_request", leaveRequestId, AuditHistory.ActionType.UPDATE, prev, saved, currentStaff.getId());
        return LeaveRequestResponse.fromEntity(saved);
    }

    private void validateLeaveRequest(Integer staffId, LeaveRequestDTO dto) {
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BadRequestException("Ngày bắt đầu phải trước ngày kết thúc");
        }

        if (dto.getStartDate().isBefore(java.time.LocalDate.now())) {
            throw new BadRequestException("Ngày bắt đầu không được trong quá khứ");
        }

        // Check for overlapping APPROVED leave requests
        List<LeaveRequest> overlappingLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                staffId, dto.getStartDate(), dto.getEndDate());
        boolean hasApprovedOverlap = overlappingLeaves.stream()
                .anyMatch(lr -> lr.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedOverlap) {
            throw new BadRequestException("Nhân sự đã có ngày nghỉ phép được duyệt trùng với khoảng thời gian này");
        }

        // Check for compensation days in the date range
        List<CompensationDay> compDaysInRange = compensationDayRepository.findByStaffIdAndDateRange(
                staffId, dto.getStartDate(), dto.getEndDate());
        if (!compDaysInRange.isEmpty()) {
            CompensationDay conflict = compDaysInRange.get(0);
            throw new BadRequestException(
                    "Ngày nghỉ phép trùng với ngày nghỉ bù ngày " + conflict.getCompensationDate());
        }
    }

    private void validateNoScheduleConflict(Staff staff, LocalDate startDate, LocalDate endDate) {
        List<Schedule> overlapping = scheduleRepository.findByStaffIdAndDateRange(
                staff.getId(), startDate, endDate);
        if (!overlapping.isEmpty()) {
            Schedule conflict = overlapping.get(0);
            throw new BadRequestException(
                    "Nhân sự " + staff.getFullName() + " có lịch trực vào ngày " +
                    conflict.getWorkDate() + " (" + conflict.getShiftType().getName() +
                    ") trùng với khoảng thời gian nghỉ phép");
        }

        List<CompensationDay> compDaysInRange = compensationDayRepository.findByStaffIdAndDateRange(
                staff.getId(), startDate, endDate);
        if (!compDaysInRange.isEmpty()) {
            CompensationDay conflict = compDaysInRange.get(0);
            throw new BadRequestException(
                    "Nhân sự " + staff.getFullName() + " có ngày nghỉ bù vào ngày " +
                    conflict.getCompensationDate() + " trùng với khoảng thời gian nghỉ phép");
        }
    }
}
