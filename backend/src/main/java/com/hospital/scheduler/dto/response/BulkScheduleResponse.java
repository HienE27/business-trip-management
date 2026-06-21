package com.hospital.scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkScheduleResponse {

    private int totalRequested;
    private int successCount;
    private int failureCount;

    @Builder.Default
    private List<BulkResultEntry> results = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkResultEntry {
        private String workDate;
        private Integer staffId;
        private Integer scheduleId;
        private String error;
    }
}
