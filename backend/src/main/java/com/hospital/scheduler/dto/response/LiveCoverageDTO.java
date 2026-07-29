package com.hospital.scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * BUGFIX (coverage drift + UX): live coverage snapshot for a period.
 *
 * <p>Computed at request time from {@code schedule} and {@code shift_requirement}
 * tables. Use this instead of the cached {@code algorithm_metrics.coverage_rate}
 * column when the dashboard needs to reflect the actual persisted state.
 *
 * <p>The response now carries per-shift-type and per-day breakdowns so the
 * dashboard can answer "how well does each shift type cover its demand?" and
 * "how many distinct days have at least one assignment?" — which is what users
 * actually need to read instead of a misleading single percentage.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveCoverageDTO {
    private Integer periodId;
    private long totalSchedules;
    private long totalRequiredCapacity;
    /**
     * Coverage rate as percentage in range [0, 100]. Equals
     * {@code totalSchedules / totalRequiredCapacity * 100} when the period has
     * any requirements, else {@code 0}.
     */
    private BigDecimal coverageRate;

    /**
     * BUGFIX (UX): per-shift-type breakdown. Keyed by shift_type_id (e.g. L01,
     * L02, L03, L04). Each entry exposes the required vs assigned capacity and
     * the per-type coverage rate so the UI can render one card per shift type
     * rather than a single opaque percentage.
     */
    private Map<String, ShiftTypeCoverage> byShiftType;

    /**
     * BUGFIX (UX): per-day breakdown. Keyed by ISO date string (yyyy-MM-dd).
     * Lets the UI highlight which days have full / partial / no coverage.
     */
    private Map<String, DayCoverage> byDay;

    /**
     * Number of distinct days (in the period range) that have at least one
     * persisted schedule. Useful as a "ngày có lịch" KPI that is more
     * intuitive than {@link #coverageRate}.
     */
    private long distinctDaysWithSchedules;
    private long totalPeriodDays;
    private LocalDateTime computedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeCoverage {
        private String shiftTypeId;
        private String shiftTypeName;
        private long requiredCapacity;
        private long assignedCount;
        /** Required staff-slots minus assigned, floored at 0. */
        private long shortfall;
        private BigDecimal coverageRate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayCoverage {
        private String workDate;
        private long requiredCapacity;
        private long assignedCount;
        private long shortfall;
        private BigDecimal coverageRate;
    }
}