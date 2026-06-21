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
public class PublishDryRunResponse {

    private Integer periodId;
    private String periodName;
    private boolean hasConflicts;
    private int conflictCount;

    @Builder.Default
    private List<ConflictCheckResponse.ConflictDetail> conflicts = new ArrayList<>();

    private boolean hasCoverageGaps;

    @Builder.Default
    private List<String> coverageGaps = new ArrayList<>();

    private CoverageReportDTO staffingCoverage;

    private boolean canPublish;
}
