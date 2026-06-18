package com.hospital.scheduler.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated schedule view for a custom date range. Used by the dashboard's
 * week and month calendar views that aren't tied to a specific
 * {@code SchedulePeriod}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleAggregationResponse {

    /** Inclusive start of the queried range. */
    private LocalDate rangeStart;

    /** Inclusive end of the queried range. */
    private LocalDate rangeEnd;

    /** Number of days in the range. */
    private int daysInRange;

    /** Total schedules in the range. */
    private long totalSchedules;

    /** Per-day breakdown of shift-type counts. */
    private Map<LocalDate, Map<String, Long>> dailyCounts;

    /** Per-shift-type totals across the range. */
    private Map<String, Long> shiftTypeTotals;

    /** Optional staff-filtered per-staff counts. */
    private List<StaffDailyCount> perStaff;
}
