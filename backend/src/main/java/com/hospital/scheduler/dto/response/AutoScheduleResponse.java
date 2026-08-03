package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.algorithm.scoring.ScheduleQualityReport;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoScheduleResponse {

    private boolean success;
    private String message;
    private Integer periodId;
    private String algorithmType;
    private Integer executionTimeMs;
    private BigDecimal coverageRate;
    private BigDecimal balanceScore;
    /** Độ đều theo TỪNG loại ca (L01/L02/L03/L04): 100 - CV(số ca/NS theo loại). */
    private Map<String, BigDecimal> balanceByShiftType;
    /** Tổng hợp: bình quân gia quyền theo số ca của balanceByShiftType. */
    private BigDecimal balanceByTypeScore;
    private Integer conflictCount;
    private Integer totalSchedulesCreated;
    /** Cap ca/NS thực tế dùng cho lần chạy: override request > auto-cap preview > runtime config max_shifts. */
    private Integer effectiveMaxShiftsPerStaff;
    private List<ScheduleSummary> schedules;
    private List<Integer> excludedStaffIds;
    private List<Map<String, Object>> unassignedDays;
    private LocalDateTime executedAt;

    /** Chi tiết phân bổ theo từng loại lịch (L01/L02/L03/L04) */
    private Map<String, ShiftTypeBreakdown> byShiftType;

    /**
     * Báo cáo chất lượng lịch: coverage, fairness (CV per type), constraint violations.
     * Được tính sau khi thuật toán hoàn thành, trước khi lưu.
     * Frontend dùng để hiển thị M07-F09 (Thống kê cân bằng tải).
     */
    private ScheduleQualityReport qualityReport;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScheduleSummary {
        private Integer scheduleId;
        private Integer staffId;
        private String staffName;
        private String workDate;
        private String shiftTypeId;
        private String shiftTypeName;
        private String staffSpecialtyName;
        private String requiredSpecialtyName;
        // BUGFIX (was M07 #8): propagate the requirement id so the frontend's
        // apply-preview call can echo it back and disambiguate L04 slots
        // (Phòng khám chuyên gia) that have multi-specialty requirements
        // on the same date.
        private Integer requirementId;
    }

    /** Chi tiết phân bổ cho từng loại lịch */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeBreakdown {
        private String shiftTypeId;
        private String shiftTypeName;
        private int totalAssigned;
        private int totalRequired;
        private double coverageRate;
        private List<String> unassignedDates;
        private int distinctStaffAssigned;
    }
}
