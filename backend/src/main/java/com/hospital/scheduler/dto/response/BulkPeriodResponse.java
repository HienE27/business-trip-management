package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkPeriodResponse {

    private int totalRequested;
    private int successCount;
    private int failureCount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodResult {
        private Integer id;
        private String periodName;
        private boolean success;
        private String message;
        /** Full conflict details — present when success=false and the failure is due to conflicts. */
        private List<ConflictDetail> conflicts;
        private SchedulePeriodResponse data;
        private LocalDateTime processedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConflictDetail {
        private Integer scheduleId;
        private String staffName;
        private String workDate;
        private String shiftTypeName;
        private List<String> conflictReasons;
    }

    private List<PeriodResult> results;

    public static BulkPeriodResponse of(List<PeriodResult> results) {
        int total = results.size();
        int success = (int) results.stream().filter(PeriodResult::isSuccess).count();
        return BulkPeriodResponse.builder()
                .totalRequested(total)
                .successCount(success)
                .failureCount(total - success)
                .results(results)
                .build();
    }
}
