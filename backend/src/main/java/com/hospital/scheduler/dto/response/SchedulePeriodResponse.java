package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulePeriodResponse {
    private Integer id;
    private String periodName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private StaffSummary generatedBy;
    private LocalDateTime generatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffSummary {
        private Integer id;
        private String fullName;
    }
}
