package com.hospital.scheduler.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResponse {
    private Integer id;
    private Integer periodId;
    private PeriodSummary period;
    private LocalDate workDate;
    private StaffSummary staff;
    private ShiftTypeSummary shiftType;
    private Integer requirementId;
    private LocalDate compensationDate;
    private List<String> conflictReasons;
    private String notes;
    private Boolean hasConflict;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodSummary {
        private Integer id;
        private String periodName;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffSummary {
        private Integer id;
        private String username;
        private String fullName;
        private String specialtyName;
        private List<String> roles;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeSummary {
        private String id;
        private String name;
        private String description;
        private LocalTime startTime;
        private LocalTime endTime;
        private Boolean isOvernight;
        private Integer fatigueScore;
    }
}
