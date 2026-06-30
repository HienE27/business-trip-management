package com.hospital.scheduler.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflictCheckResponse {

    private Integer periodId;
    private boolean hasConflicts;
    private int totalConflicts;
    private List<ConflictDetail> conflicts;
    private List<String> coverageGaps;
    private boolean hasCoverageGaps;
    private int totalCoverageGaps;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConflictDetail {
        private Integer scheduleId;
        private String staffName;
        private LocalDate workDate;
        private String shiftTypeId;
        private String shiftTypeName;
        private List<String> conflictReasons;
        /** Period ID — required for replacement suggestion API */
        private Integer periodId;
        /** Original staff ID — used to exclude current assignee from replacement candidates */
        private Integer originalStaffId;
    }
}
