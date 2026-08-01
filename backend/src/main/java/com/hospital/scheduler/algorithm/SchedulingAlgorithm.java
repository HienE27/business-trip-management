package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface cho thuật toán xếp lịch tự động.
 * Sử dụng Strategy Pattern để hỗ trợ nhiều thuật toán.
 *
 * ISSUE 3 FIX: Thêm incremental solving support
 * - solve(): Full solve từ đầu (batch mode)
 * - reSolve(): Partial re-solve với delta changes (interactive mode)
 *
 * NOTE: ShiftRequirement entity has been replaced with ShiftRequirementInfo POJO.
 * Requirements are now derived from AutoGenConfig (not from database records).
 */
public interface SchedulingAlgorithm {

    /**
     * Chạy thuật toán xếp lịch - FULL SOLVE.
     *
     * Sử dụng khi:
     * - Lần đầu tiên xếp lịch
     * - Cần xếp lại toàn bộ từ đầu
     *
     * @param staffList Danh sách nhân sự hoạt động
     * @param startDate Ngày bắt đầu kỳ lịch
     * @param endDate Ngày kết thúc kỳ lịch
     * @param requirements Danh sách yêu cầu ca trực (derived from AutoGenConfig)
     * @param existingCompensationDays Ngày nghỉ bù đã có từ các kỳ trước
     * @param leaveRequests Danh sách đơn nghỉ phép đã duyệt
     * @param excludedStaffIds Nhân sự bị loại trừ
     * @return Kết quả xếp lịch
     */
    SchedulingResult solve(
        List<Staff> staffList,
        LocalDate startDate,
        LocalDate endDate,
        List<ShiftRequirementInfo> requirements,
        Set<String> existingCompensationDays,
        List<LeaveRequest> leaveRequests,
        Set<Integer> excludedStaffIds
    );

    // ==================== ISSUE 3 FIX: Incremental Solving ====================

    /**
     * ISSUE 3 FIX: Incremental re-solve với delta changes.
     *
     * Sử dụng khi:
     * - User thay đổi 1 assignment (đổi, xóa, thêm)
     * - Cần cập nhật lịch mà không chạy lại toàn bộ
     * - Production interactive system
     *
     * @param previousResult Lịch trước đó (để reuse partial work)
     * @param deltaChanges Thay đổi cần áp dụng
     * @param staffList Danh sách nhân sự
     * @param requirements Danh sách yêu cầu ca trực (derived from AutoGenConfig)
     * @param leaveRequests Danh sách đơn nghỉ phép (updated)
     * @return Kết quả xếp lịch mới với changes được áp dụng
     */
    SchedulingResult reSolve(
        SchedulingResult previousResult,
        ScheduleChange deltaChanges,
        List<Staff> staffList,
        List<ShiftRequirementInfo> requirements,
        List<LeaveRequest> leaveRequests
    );

    /**
     * ISSUE 3 FIX: Kiểm tra xem có thể incremental solve không.
     *
     * @param deltaChanges Thay đổi cần kiểm tra
     * @return true nếu có thể re-solve incrementally, false nếu cần full solve
     */
    boolean canReSolveIncrementally(ScheduleChange deltaChanges);

    /**
     * Tên thuật toán.
     */
    String getName();

    /**
     * Mô tả thuật toán.
     */
    String getDescription();
}
