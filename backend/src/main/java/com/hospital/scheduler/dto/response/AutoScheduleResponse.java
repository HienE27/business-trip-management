package com.hospital.scheduler.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private Integer conflictCount;
    private Integer totalSchedulesCreated;
    private List<ScheduleSummary> schedules;
    private List<Integer> excludedStaffIds;
    private LocalDateTime executedAt;

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
    }
}
