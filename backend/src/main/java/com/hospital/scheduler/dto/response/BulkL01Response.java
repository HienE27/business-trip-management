package com.hospital.scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkL01Response {

    private int successCount;
    private int failureCount;
    private int totalCount;
    private List<String> errors;
    private List<BulkL01ScheduleResult> results;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkL01ScheduleResult {
        private Integer scheduleId;
        private Integer staffId;
        private String workDate;
        private String error;
    }
}
