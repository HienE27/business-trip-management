package com.hospital.scheduler.security;

import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.ForbiddenOperationException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.NotificationRepository;
import com.hospital.scheduler.repository.ScheduleExchangeRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthContextService {

    // BUGFIX (was SILENT-SWALLOW): every fall-through check below caught
    // Exception silently and returned false. From a security POV, fail-closed
    // is correct — but ops had no way to see why an AUTH predicate returned
    // false (DB blip? JWT expired? user purged?). Now every swallowed
    // exception goes through this logger at DEBUG so a probe on a stuck
    // endpoint surfaces the root cause without leaking it to the client.
    private static final Logger log = LoggerFactory.getLogger(AuthContextService.class);

    private final StaffRepository staffRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleExchangeRepository scheduleExchangeRepository;
    private final NotificationRepository notificationRepository;

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
            log.debug("isCurrentStaff({}) returned false", staffId, e);
            return false;
        }
    }

    /**
     * Trả về staffId của user hiện tại, hoặc null nếu không xác định được.
     * Dùng cho các endpoint "lấy của tôi" — không throw để caller quyết định
     * trả lỗi 404 vs 403.
     */
    public Integer getCurrentStaffId() {
        try {
            return getCurrentStaff().getId();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isCurrentStaffOwnerOfLeaveRequest(Integer leaveRequestId) {
        try {
            Staff current = getCurrentStaff();
            return leaveRequestRepository.findById(leaveRequestId)
                    .map(lr -> lr.getStaff().getId().equals(current.getId()))
                    .orElse(false);
        } catch (Exception e) {
            log.debug("isCurrentStaffOwnerOfLeaveRequest({}) returned false", leaveRequestId, e);
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
            log.debug("isCurrentStaffOwnerOfExchange({}) returned false", exchangeId, e);
            return false;
        }
    }

    public boolean isCurrentStaffOwner(Integer notificationId) {
        try {
            Staff current = getCurrentStaff();
            return notificationRepository.findById(notificationId)
                    .map(n -> n.getStaff().getId().equals(current.getId()))
                    .orElse(false);
        } catch (Exception e) {
            log.debug("isCurrentStaffOwner({}) returned false", notificationId, e);
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

    /**
     * Check whether the current authentication carries the given permission
     * authority. Used by controllers to branch behavior (e.g. staff-scoped
     * vs org-wide listing) without leaking via @PreAuthorize.
     */
    public boolean hasAuthority(String permission) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) return false;
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority() != null && a.getAuthority().equals(permission));
        } catch (Exception e) {
            log.debug("hasAuthority({}) returned false", permission, e);
            return false;
        }
    }

    /**
     * Convenience: true if the current user is a STAFF (not manager/admin).
     * Used by controllers to decide whether to apply a staff-scoped filter
     * instead of returning org-wide data.
     */
    public boolean isCurrentStaff() {
        try {
            return !isManagerLike(getCurrentStaff());
        } catch (Exception e) {
            log.debug("isCurrentStaff() returned false", e);
            return false;
        }
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
