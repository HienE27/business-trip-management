package com.hospital.scheduler.algorithm;

import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ISSUE 3 FIX: Delta changes cho incremental re-solve.
 *
 * Thay vì chạy lại full CSP mỗi khi có thay đổi,
 * chỉ cập nhật phần bị ảnh hưởng và re-solve locally.
 *
 * Use cases:
 * 1. User đổi 1 assignment → Chỉ re-check conflicts liên quan
 * 2. User thêm staff mới → Update domain của affected vars
 * 3. User thêm leave request → Mark domain = empty cho affected
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleChange {

    // ==================== Assignment Changes ====================

    /**
     * Assignments bị xÓA (unassigned)
     */
    @Builder.Default
    private List<AssignmentDelta> removed = new ArrayList<>();

    /**
     * Assignments mới được THÊM
     */
    @Builder.Default
    private List<AssignmentDelta> added = new ArrayList<>();

    /**
     * Assignments bị THAY ĐỔI (đổi staff)
     */
    @Builder.Default
    private List<AssignmentDelta> modified = new ArrayList<>();

    // ==================== Constraint Changes ====================

    /**
     * Leave requests mới được THÊM
     */
    @Builder.Default
    private List<LeaveDelta> addedLeaves = new ArrayList<>();

    /**
     * Leave requests bị XÓA
     */
    @Builder.Default
    private List<LeaveDelta> removedLeaves = new ArrayList<>();

    /**
     * Staff mới được THÊM vào hệ thống
     */
    @Builder.Default
    private List<Integer> addedStaffIds = new ArrayList<>();

    /**
     * Staff bị XÓA khỏi hệ thống
     */
    @Builder.Default
    private List<Integer> removedStaffIds = new ArrayList<>();

    // ==================== Utility Methods ====================

    /**
     * Kiểm tra xem có thay đổi nào không
     */
    public boolean hasChanges() {
        return !removed.isEmpty() ||
               !added.isEmpty() ||
               !modified.isEmpty() ||
               !addedLeaves.isEmpty() ||
               !removedLeaves.isEmpty() ||
               !addedStaffIds.isEmpty() ||
               !removedStaffIds.isEmpty();
    }

    /**
     * Kiểm tra xem có cần full re-solve không
     * Full re-solve cần khi:
     * - Quá nhiều changes (> 50%)
     * - Staff changes
     * - Major requirement changes
     */
    public boolean requiresFullReSolve() {
        // Nếu có staff changes → cần full re-solve
        if (!addedStaffIds.isEmpty() || !removedStaffIds.isEmpty()) {
            return true;
        }

        // Nếu > 50% assignments thay đổi → full re-solve
        int totalChanges = removed.size() + added.size() + modified.size();
        if (totalChanges > 50) {
            return true;
        }

        return false;
    }

    /**
     * Tạo ScheduleChange cho một single assignment change
     */
    public static ScheduleChange singleAssignmentChange(
            Integer staffId,
            LocalDate date,
            String shiftType,
            ChangeType changeType) {

        AssignmentDelta delta = AssignmentDelta.builder()
                .staffId(staffId)
                .date(date)
                .shiftType(shiftType)
                .build();

        ScheduleChange change = new ScheduleChange();

        switch (changeType) {
            case ADD -> change.getAdded().add(delta);
            case REMOVE -> change.getRemoved().add(delta);
            case MODIFY -> change.getModified().add(delta);
        }

        return change;
    }

    /**
     * Tạo ScheduleChange cho leave request
     */
    public static ScheduleChange leaveChange(
            Integer staffId,
            LocalDate startDate,
            LocalDate endDate,
            boolean isAdded) {

        LeaveDelta delta = LeaveDelta.builder()
                .staffId(staffId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        ScheduleChange change = new ScheduleChange();
        if (isAdded) {
            change.getAddedLeaves().add(delta);
        } else {
            change.getRemovedLeaves().add(delta);
        }

        return change;
    }

    // ==================== Inner Classes ====================

    /**
     * Delta cho một assignment
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentDelta {
        /**
         * Staff ID (-1 nếu unassigned)
         */
        private Integer staffId;

        /**
         * Ngày làm việc
         */
        private LocalDate date;

        /**
         * Loại ca trực (L01/L02/L03/L04)
         */
        private String shiftType;

        /**
         * Staff ID cũ (cho MODIFY type)
         */
        private Integer oldStaffId;
    }

    /**
     * Delta cho một leave request
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaveDelta {
        private Integer staffId;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    /**
     * Loại thay đổi
     */
    public enum ChangeType {
        ADD,    // Thêm assignment mới
        REMOVE, // Xóa assignment
        MODIFY  // Thay đổi staff trong assignment
    }
}
