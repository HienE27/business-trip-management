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
     * Runtime override for max_shifts_per_month cap during auto-schedule.
     *
     * - null (default): use each staff's existing max_shifts_per_month from DB.
     * - positive integer: force every staff's monthly cap to this value for
     *   THIS run only. DB is NOT modified — original caps restored after run.
     * - 0: disable the cap entirely (assign unlimited shifts per staff).
     *
     * Use cases:
     *   - Cap=40 simulates "relaxed" scheduling to measure coverage ceiling.
     *   - Cap=8 simulates realistic hospital workload (1 ca/4 ngày).
     *   - Cap=0 stress-tests solver when fairness constraint is removed.
     *
     * Mirrors the same value used by both the CSP eligibility filter and the
     * period-level conflict counter, so changing it affects coverage, balance,
     * and fairness metrics consistently.
     */
    private Integer maxShiftsPerMonthOverride;

}
