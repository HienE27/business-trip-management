package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoScheduleRequestDTO {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @Builder.Default
    private String algorithmType = "GREEDY";

    @Builder.Default
    private Integer maxIterations = 1000;

    @Builder.Default
    private Boolean autoAssign = true;

    @Builder.Default
    private List<Integer> excludedStaffIds = List.of();

    /**
     * Holiday handling mode for this run.
     * SKIP = skip all shifts on holidays (default from DB config).
     * PARTIAL = reduce L03 intensity on holidays.
     * When null, uses the value from algorithm_config DB.
     */
    private String holidayMode;

    /**
     * When true, the algorithm auto-generates ShiftRequirement records
     * for every day in the period before assigning staff.
     * Requires auto_gen_enabled=true in algorithm_config.
     */
    @Builder.Default
    private Boolean autoGenerateRequirements = false;
}
