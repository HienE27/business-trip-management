package com.hospital.scheduler.algorithm;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Kết quả xếp lịch từ thuật toán.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulingResult {

    /**
     * Danh sách phân công: key = "staffId_workDate", value = shiftTypeId
     */
    @Builder.Default
    private Map<String, String> assignments = new HashMap<>();

    /**
     * Tập hợp các ngày nghỉ bù: "staffId_date"
     */
    @Builder.Default
    private Set<String> compensationDays = new HashSet<>();

    /**
     * Danh sách lỗi/xung đột (nếu có)
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Trạng thái hợp lệ
     */
    private boolean valid;

    /**
     * True when {@link #valid} is true only because the search returned a
     * partial assignment under timeout pressure (not a complete coverage of
     * every required slot). The orchestrator can use this to decide whether
     * to top up the plan with a different algorithm (e.g. Greedy) instead
     * of treating the partial plan as a finished schedule.
     */
    private boolean partial;

    /**
     * Score tổng (0-100)
     */
    private BigDecimal totalScore;

    /**
     * Score công bằng (fairness)
     */
    private BigDecimal fairnessScore;

    /**
     * Score mệt mỏi (fatigue) - giảm khi nhân viên trực quá nhiều
     */
    private BigDecimal fatigueScore;

    /**
     * Score độ phủ (coverage) - % yêu cầu được đáp ứng
     */
    private BigDecimal coverageScore;

    /**
     * Thời gian chạy (ms)
     */
    private long executionTimeMs;

    /**
     * Số lượng schedule được tạo
     */
    private int scheduleCount;

    // ==================== M07-F06: Report Unassigned Days ====================

    /**
     * M07-F06: Danh sách các ngày chưa phân công đủ nhân sự.
     * Mỗi entry chứa: date, shiftType, required, assigned, shortfall
     */
    @Builder.Default
    private List<Map<String, Object>> unassignedDays = new ArrayList<>();

    // ==================== M07-F07: Preview Before Confirm ====================

    /**
     * M07-F07: Preview data - lịch trước khi xác nhận
     * Chứa lịch đầy đủ để hiển thị cho quản lý duyệt
     */
    @Builder.Default
    private PreviewData previewData = null;

    /**
     * Preview data structure
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewData {
        /**
         * Danh sách tất cả assignments dạng chi tiết
         */
        @Builder.Default
        private List<Map<String, Object>> assignments = new ArrayList<>();

        /**
         * Thống kê theo nhân viên
         */
        @Builder.Default
        private List<Map<String, Object>> staffStats = new ArrayList<>();

        /**
         * Thống kê theo ngày
         */
        @Builder.Default
        private List<Map<String, Object>> dayStats = new ArrayList<>();

        /**
         * Cảnh báo/ conflicts nếu có
         */
        @Builder.Default
        private List<String> warnings = new ArrayList<>();

        /**
         * Trạng thái: PENDING (chờ duyệt), APPROVED, REJECTED
         */
        private String status;

        /**
         * Thời gian preview được tạo
         */
        private LocalDateTime createdAt;
    }

    /**
     * Thêm một assignment.
     */
    public void addAssignment(Integer staffId, LocalDate workDate, String shiftTypeId) {
        String key = staffId + "_" + workDate.toString();
        assignments.put(key, shiftTypeId);
    }

    /**
     * Lấy assignment.
     */
    public String getAssignment(Integer staffId, LocalDate workDate) {
        String key = staffId + "_" + workDate.toString();
        return assignments.get(key);
    }

    /**
     * Thêm ngày nghỉ bù.
     */
    public void addCompensationDay(Integer staffId, LocalDate compensationDate) {
        String key = staffId + "_" + compensationDate.toString();
        compensationDays.add(key);
    }

    /**
     * Kiểm tra có phải ngày nghỉ bù không.
     */
    public boolean isCompensationDay(Integer staffId, LocalDate date) {
        String key = staffId + "_" + date.toString();
        return compensationDays.contains(key);
    }
}
