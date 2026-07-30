package com.hospital.scheduler.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private DashboardSummary summary;
    private ShiftStatistics shiftStatistics;
    private LeaveRequestStatistics leaveRequestStatistics;
    private StaffWorkloadStatistics staffWorkloadStatistics;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DashboardSummary {
        private long totalStaff;
        private long activeStaff;
        private long totalSchedules;
        private long totalPeriods;
        private long pendingLeaveRequests;
        private long pendingScheduleExchanges;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftStatistics {
        @JsonProperty("L01Count")
        private long L01Count;
        @JsonProperty("L02Count")
        private long L02Count;
        @JsonProperty("L03Count")
        private long L03Count;
        @JsonProperty("L04Count")
        private long L04Count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaveRequestStatistics {
        private long total;
        private long pending;
        private long approved;
        private long rejected;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffWorkloadStatistics {
        private Integer staffId;
        private String staffName;
        private long scheduleCount;
        @JsonProperty("L01Count")
        private long L01Count;
        @JsonProperty("L02Count")
        private long L02Count;
        @JsonProperty("L03Count")
        private long L03Count;
        @JsonProperty("L04Count")
        private long L04Count;
        private long leaveDays;
        /** Monthly cap from staff record; null if legacy record without the field. */
        private Integer maxShiftsPerMonth;
        /** true when this staff still has zero or partial schedule data for the period. */
        private boolean underCap;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodSummary {
        private Integer periodId;
        private String periodName;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;
        private long scheduleCount;
        private long staffCount;
    }

    /**
     * Phân tích chi tiết theo loại ca (L03/L04) theo tuần hoặc tháng.
     * Phục vụ M04-F05 và M05-F05.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeDetailStatistics {
        /** Chỉ L03 hoặc L04 */
        private String shiftTypeId;
        private String shiftTypeName;
        private long totalDays;
        /** Các nhóm: key = "W01", "W02"... hoặc "Month X" */
        private Map<String, Long> byGroup;
        /** Thống kê theo nhân sự */
        private List<StaffShiftDetail> byStaff;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class StaffShiftDetail {
            private Integer staffId;
            private String staffName;
            private long totalDays;
        }
    }
}
