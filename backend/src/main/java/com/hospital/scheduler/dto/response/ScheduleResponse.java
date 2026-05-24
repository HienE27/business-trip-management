package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResponse {
    private Integer id;
    private Integer periodId;
    private LocalDate workDate;
    private StaffSummary staff;
    private ShiftTypeSummary shiftType;
    private Integer requirementId;
    private Boolean hasConflict;
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeSummary {
        private String id;
        private String name;
        private Boolean isOvernight;
    }
}
