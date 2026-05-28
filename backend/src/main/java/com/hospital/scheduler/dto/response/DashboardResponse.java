package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDate;

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
        private long L01Count;
        private long L02Count;
        private long L03Count;
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
        private long L01Count;
        private long L02Count;
        private long L03Count;
        private long L04Count;
        private long leaveDays;
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
}
