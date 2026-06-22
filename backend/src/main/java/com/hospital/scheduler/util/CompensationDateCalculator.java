package com.hospital.scheduler.util;

import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility to calculate compensation dates for L01 (24/24) shifts.
 *
 * Per spec rules:
 * - Monday duty     → Tuesday (next day)
 * - Tuesday duty    → Wednesday (next day)
 * - Wednesday duty  → Thursday (next day)
 * - Thursday duty   → Friday (next day)
 * - Friday duty     → Tuesday NEXT WEEK (skip Mon, skip Fri)
 * - Saturday duty   → Tuesday NEXT WEEK (skip Mon, skip Fri)
 * - Sunday duty     → Monday (next day)
 *
 * If the computed compensation date falls on a holiday:
 * - For Mon–Thu/Sun duty: shift to next non-holiday weekday (Mon/Fri are allowed)
 * - For Fri/Sat duty: skip Mon AND Fri (per spec rule), plus holidays
 */
@Component
@RequiredArgsConstructor
public class CompensationDateCalculator {

    private final HolidayRepository holidayRepository;

    /**
     * Calculate compensation date for a given shift date.
     * Handles holiday avoidance per spec.
     */
    public LocalDate calculate(LocalDate shiftDate) {
        LocalDate raw = computeBase(shiftDate);
        boolean isFriOrSatDuty = isFriOrSatDuty(shiftDate);
        return advancePastHolidayOrInvalidDay(raw, isFriOrSatDuty);
    }

    /**
     * Calculate compensation date without holiday adjustment.
     * Bypasses DB entirely; useful for pure date-arithmetic tests.
     */
    public LocalDate calculateWithoutHolidays(LocalDate shiftDate) {
        return computeBase(shiftDate);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isFriOrSatDuty(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY;
    }

    /**
     * Core base-date computation — no holiday awareness.
     */
    private LocalDate computeBase(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return switch (dow) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY -> shiftDate.plusDays(1);
            case FRIDAY, SATURDAY -> {
                // Find the next Tuesday on or after (shiftDate + 1 day).
                // For Fri Jun 5  → search from Sat Jun 6  → wraps to Tue Jun 9.
                // For Fri Jun 26 → search from Sat Jun 27 → wraps to Tue Jul 1.
                yield findNextDayOfWeek(shiftDate.plusDays(1), DayOfWeek.TUESDAY);
            }
            case SUNDAY -> findNextDayOfWeek(shiftDate.plusDays(1), DayOfWeek.MONDAY);
        };
    }

    /**
     * Find the next occurrence of target day-of-week, searching forward from start (inclusive).
     */
    private LocalDate findNextDayOfWeek(LocalDate start, DayOfWeek target) {
        for (int i = 0; i < 7; i++) {
            if (start.getDayOfWeek() == target) return start;
            start = start.plusDays(1);
        }
        return start;
    }

    /**
     * Advance from a raw compensation date until a stable, valid day is reached.
     * A day is valid if it is not a holiday and passes weekday rules:
     * - Always reject Sat/Sun.
     * - For Fri/Sat duty: also reject Mon and Fri.
     *
     * Guard: if the raw date is itself invalid, advance once and re-validate.
     * Outer loop handles chained holidays.
     */
    private LocalDate advancePastHolidayOrInvalidDay(LocalDate raw, boolean isFriOrSatDuty) {
        Set<LocalDate> holidays = holidayRepository
                .findActiveHolidaysBetween(raw, raw.plusYears(1))
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        LocalDate current = raw;
        for (int outer = 0; outer < 30; outer++) {
            LocalDate next = advanceOneStep(current, holidays, isFriOrSatDuty);
            if (next.equals(current)) return current; // stable
            current = next;
        }
        return current; // safety net
    }

    /**
     * Advance by exactly one step from the given date.
     * Returns the same date unchanged if it is already valid
     * (used as a sentinel so the outer loop can detect stability).
     */
    private LocalDate advanceOneStep(LocalDate date, Set<LocalDate> holidays, boolean isFriOrSatDuty) {
        if (!isInvalidDay(date, holidays, isFriOrSatDuty)) return date;

        LocalDate cursor = date.plusDays(1);
        for (int i = 0; i < 60; i++) {
            if (!isInvalidDay(cursor, holidays, isFriOrSatDuty)) return cursor;
            cursor = cursor.plusDays(1);
        }
        return date; // safety: return unchanged if no valid day found in 60 days
    }

    /**
     * Returns true if the given date must be skipped.
     */
    private boolean isInvalidDay(LocalDate date, Set<LocalDate> holidays, boolean isFriOrSatDuty) {
        if (holidays.contains(date)) return true;
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return true;
        if (isFriOrSatDuty && (dow == DayOfWeek.MONDAY || dow == DayOfWeek.FRIDAY)) return true;
        return false;
    }
}
