package com.hospital.scheduler.scheduling.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable description of one slot-staff assignment pair.
 *
 * <p>Used as both the input to {@code WorkingSolution} (initial population) and
 * the output (final committed solution). At runtime, the search loop mutates
 * {@code MutableAssignment} instead to avoid allocation churn.
 *
 * @param slotId      database key of the {@code ShiftRequirement} row
 * @param staffId     database key of the assigned {@code Staff}, or -1 if unassigned
 * @param date        work date (date-only)
 * @param shiftTypeId L01..L04
 * @param hours       estimated duration (L01 = 24h, L02 = 4h, etc.)
 * @param isWeekend   cached so the search loop doesn't recompute day-of-week
 * @param isHoliday   cached holiday flag from {@code HolidayRepository}
 * @param startTime   wall-clock start (UTC-naive)
 * @param endTime     wall-clock end (UTC-naive)
 * @param weekNumber  ISO 8601 week-of-year (cached)
 */
public record AssignmentNode(
        int slotId,
        int staffId,
        LocalDate date,
        String shiftTypeId,
        int hours,
        boolean isWeekend,
        boolean isHoliday,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer weekNumber
) {

    /**
     * Convenience factory for unassigned slots — staffId = -1.
     */
    public static AssignmentNode unassigned(int slotId,
                                            LocalDate date,
                                            String shiftTypeId,
                                            int hours,
                                            boolean isWeekend,
                                            boolean isHoliday) {
        return new AssignmentNode(slotId, -1, date, shiftTypeId, hours,
                isWeekend, isHoliday, null, null, null);
    }

    /**
     * Return a copy with a different {@code staffId}.
     */
    public AssignmentNode withStaffId(int newStaffId) {
        return new AssignmentNode(slotId, newStaffId, date, shiftTypeId, hours,
                isWeekend, isHoliday, startTime, endTime, weekNumber);
    }

    public boolean isAssigned() {
        return staffId > 0;
    }
}