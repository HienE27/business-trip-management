package com.hospital.scheduler.security;

import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.ForbiddenOperationException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleExchangeRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthContextService {

    private final StaffRepository staffRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleExchangeRepository scheduleExchangeRepository;

    public Staff getCurrentStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Không xác định được người dùng hiện tại");
        }

        return staffRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự hiện tại"));
    }

    public boolean isCurrentStaff(Integer staffId) {
        try {
            return getCurrentStaff().getId().equals(staffId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCurrentStaffOwnerOfLeaveRequest(Integer leaveRequestId) {
        try {
            Staff current = getCurrentStaff();
            return leaveRequestRepository.findById(leaveRequestId)
                    .map(lr -> lr.getStaff().getId().equals(current.getId()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCurrentStaffOwnerOfExchange(Integer exchangeId) {
        try {
            Staff current = getCurrentStaff();
            return scheduleExchangeRepository.findById(exchangeId)
                    .map(e -> e.getRequester().getId().equals(current.getId())
                            || e.getTarget().getId().equals(current.getId()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isManagerLike(Staff staff) {
        return staff.getStaffRoles().stream()
                .map(StaffRole::getRole)
                .filter(role -> role != null && role.getName() != null)
                .map(AppRole::getName)
                .anyMatch(roleName -> RoleName.ADMIN.equals(roleName) || RoleName.MANAGER.equals(roleName));
    }

    public void requireSelfOrManager(Integer targetStaffId) {
        Staff currentStaff = getCurrentStaff();
        if (isManagerLike(currentStaff) || currentStaff.getId().equals(targetStaffId)) {
            return;
        }

        throw new ForbiddenOperationException("Bạn không có quyền thao tác trên dữ liệu của nhân sự khác");
    }

    public void requireManagerLikeReviewer(Integer reviewerId) {
        Staff currentStaff = getCurrentStaff();
        if (!currentStaff.getId().equals(reviewerId)) {
            throw new ForbiddenOperationException("Reviewer không khớp với người dùng hiện tại");
        }
        if (!isManagerLike(currentStaff)) {
            throw new ForbiddenOperationException("Bạn không có quyền duyệt thao tác này");
        }
    }

    public void requireManagerOrSelfForUserData(Integer userId) {
        Staff currentStaff = getCurrentStaff();
        if (!isManagerLike(currentStaff) && !currentStaff.getId().equals(userId)) {
            throw new ForbiddenOperationException("Bạn không có quyền xem dữ liệu của nhân sự khác");
        }
    }
}
