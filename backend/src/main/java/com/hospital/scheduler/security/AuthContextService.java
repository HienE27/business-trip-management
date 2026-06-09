package com.hospital.scheduler.security;

import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.ForbiddenOperationException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthContextService {

    private final StaffRepository staffRepository;

    public Staff getCurrentStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Không xác định được người dùng hiện tại");
        }

        return staffRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự hiện tại"));
    }

    public boolean isManagerLike(Staff staff) {
        return staff.getStaffRoles().stream()
                .map(StaffRole::getRole)
                .filter(role -> role != null && role.getName() != null)
                .map(AppRole::getName)
                .anyMatch(roleName -> "ADMIN".equals(roleName) || "MANAGER".equals(roleName));
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
}
