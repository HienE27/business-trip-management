package com.hospital.scheduler.algorithm;

import java.time.LocalDate;

/**
 * Minimal request envelope for the algorithm-level scheduler variants.
 *
 * <p>Created here because the benchmark test requires a uniform input to feed
 * greedy / fair-greedy / backtracking without going through Spring. The
 * production scheduler builds a much richer request object inline; this type
 * captures only the algorithm-level concern: which period to schedule.
 */
public final class SchedulingRequest {

    private final Period period;

    public SchedulingRequest(Period period) {
        this.period = period;
    }

    public Period getPeriod() { return period; }

    public static final class Period {
        private final LocalDate startDate;
        private final LocalDate endDate;

        public Period(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
    }
}