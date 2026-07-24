package com.hospital.scheduler.dto.request;

import com.hospital.scheduler.algorithm.AutoGenConfig;
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
    private List<Integer> excludedStaffIds = List.of();

    /** Skip runtime auto-adjust for an explicitly applied recommendation. */
    @Builder.Default
    private Boolean useRecommendedConfig = false;

    /**
     * Whether to overwrite existing schedules in the period before generating new ones.
     *
     * - false (default): throw BadRequestException if the period already has schedules,
     *   protecting manual assignments from being silently deleted by auto-schedule.
     * - true: clear all existing schedules (manual + auto) for the period first, then
     *   generate fresh. Manager must explicitly confirm overwrite via UI prompt.
     *
     * Preview (/preview) is always non-destructive — it does NOT delete schedules
     * regardless of this flag.
     */
    @Builder.Default
    private Boolean overwriteExisting = false;

    /**
     * Commit B (Workflow M07): Inline AutoGenConfig override for preview.
     * When non-null, the preview scheduler uses this config instead of the saved DB config.
     * This allows "Apply Recommendation" to preview without persisting the config first.
     * The config is used IN-MEMORY ONLY — never written to DB.
     */
    @Builder.Default
    private AutoGenConfig recommendedConfig = null;

}
