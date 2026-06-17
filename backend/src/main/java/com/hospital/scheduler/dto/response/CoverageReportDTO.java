package com.hospital.scheduler.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverageReportDTO {

    private Integer periodId;
    private String periodName;
    private LocalDateTime generatedAt;
    private int totalDays;
    private int fullyCoveredDays;
    private int understaffedDays;
    private int overstaffedDays;
    private BigDecimal overallCoverageRate;
    private Map<String, Map<String, DayCoverage>> dailyCoverage;
    private Map<String, ShiftTypeSummary> shiftTypeSummary;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayCoverage {
        private LocalDate date;
        private String dayOfWeek;
        private String shiftTypeId;
        private String shiftTypeName;
        private CoverageStatus status;
        private int requiredStaff;
        private int assignedStaff;
        private int difference;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeSummary {
        private String shiftTypeId;
        private String shiftTypeName;
        private int totalRequired;
        private int totalAssigned;
        private BigDecimal coverageRate;
        private int understaffedDays;
    }

    public enum CoverageStatus {
        SUFFICIENT,
        UNDERSTAFFED,
        OVERSTAFFED,
        NO_REQUIREMENT
    }
}
