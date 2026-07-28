package com.hospital.scheduler.util;

import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
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
 * - Friday duty     → Tuesday/Wednesday/Thursday NEXT WEEK (skip Mon, skip Fri)
 * - Saturday duty   → Tuesday/Wednesday/Thursday NEXT WEEK (skip Mon, skip Fri)
 * - Sunday duty     → Monday (next day)
 *
 * {@link #calculate(LocalDate)} returns the default day (Tuesday for Fri/Sat).
 * {@link #calculateAll(LocalDate)} returns ALL valid options for flexibility.
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
     * Calculate the default compensation date for a given shift date.
     * For Fri/Sat duty this returns Tuesday of next week (the most common choice).
     * Handles holiday avoidance per spec.
     */
    public LocalDate calculate(LocalDate shiftDate) {
        LocalDate raw = computeBase(shiftDate);
        boolean isFriOrSatDuty = isFriOrSatDuty(shiftDate);
        return advancePastHolidayOrInvalidDay(raw, isFriOrSatDuty, null);
    }

    /**
     * Calculate ALL valid compensation dates for a given shift date.
     * For Fri/Sat duty this returns {Tue, Wed, Thu} of next week.
     * For Mon–Thu/Sun duty this returns a single-element set (same as
     * {@link #calculate}).
     *
     * <p>Use by scheduling algorithms that want to pick the best option
     * based on load (e.g. least-conflicted day).
     */
    public Set<LocalDate> calculateAll(LocalDate shiftDate) {
        Set<LocalDate> bases = computeBases(shiftDate);
        if (bases.isEmpty()) return Collections.emptySet();
        boolean isFriOrSatDuty = isFriOrSatDuty(shiftDate);

        Set<LocalDate> holidays = holidayRepository
                .findActiveHolidaysBetween(
                        bases.iterator().next(),
                        bases.iterator().next().plusYears(1))
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        Set<LocalDate> result = new LinkedHashSet<>();
        for (LocalDate raw : bases) {
            LocalDate adjusted = advancePastHolidayOrInvalidDay(raw, isFriOrSatDuty, holidays);
            result.add(adjusted);
        }
        return result;
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
     * Returns the single default date.
     */
    private LocalDate computeBase(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return switch (dow) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY -> shiftDate.plusDays(1);
            case FRIDAY -> findNextDayOfWeek(shiftDate.plusDays(4), DayOfWeek.TUESDAY);
            case SATURDAY -> findNextDayOfWeek(shiftDate.plusDays(3), DayOfWeek.TUESDAY);
            case SUNDAY -> findNextDayOfWeek(shiftDate.plusDays(1), DayOfWeek.MONDAY);
        };
    }

    /**
     * Return ALL valid base compensation dates (no holiday awareness).
     * For Fri/Sat duty: Tue, Wed, Thu of next week (Mon and Fri excluded).
     * For others: same as {@link #computeBase} (single option).
     */
    private Set<LocalDate> computeBases(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        Set<LocalDate> result = new LinkedHashSet<>();
        switch (dow) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SUNDAY -> result.add(computeBase(shiftDate));
            case FRIDAY -> {
                // Next-week Monday, then add Tue, Wed, Thu (skip Mon is excluded anyway)
                LocalDate nextMonday = shiftDate.plusDays(3); // Sat → Sun → Mon
                // Actually Fri + 3 = Mon next week
                LocalDate monday = shiftDate.plusDays(3);
                for (int i = 1; i <= 3; i++) { // Tue(1), Wed(2), Thu(3)
                    result.add(monday.plusDays(i));
                }
            }
            case SATURDAY -> {
                // Sat + 2 = Mon next week
                LocalDate monday = shiftDate.plusDays(2);
                for (int i = 1; i <= 3; i++) { // Tue(1), Wed(2), Thu(3)
                    result.add(monday.plusDays(i));
                }
            }
        }
        return result;
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
    private LocalDate advancePastHolidayOrInvalidDay(LocalDate raw, boolean isFriOrSatDuty,
                                                     Set<LocalDate> holidays) {
        if (holidays == null) {
            holidays = holidayRepository
                    .findActiveHolidaysBetween(raw, raw.plusYears(1))
                    .stream()
                    .map(Holiday::getHolidayDate)
                    .collect(Collectors.toSet());
        }

        LocalDate current = raw;
        for (int outer = 0; outer < 365; outer++) {
            LocalDate next = advanceOneStep(current, holidays, isFriOrSatDuty);
            if (next.equals(current)) return current;
            current = next;
        }
        return current;
    }

    private LocalDate advanceOneStep(LocalDate date, Set<LocalDate> holidays, boolean isFriOrSatDuty) {
        if (!isInvalidDay(date, holidays, isFriOrSatDuty)) return date;

        LocalDate cursor = date.plusDays(1);
        for (int i = 0; i < 60; i++) {
            if (!isInvalidDay(cursor, holidays, isFriOrSatDuty)) return cursor;
            cursor = cursor.plusDays(1);
        }
        throw new IllegalStateException(
            "CompensationDateCalculator: No valid compensation day found within 60 days of " + date +
            ". Check holiday configuration and weekday rules.");
    }

    private boolean isInvalidDay(LocalDate date, Set<LocalDate> holidays, boolean isFriOrSatDuty) {
        if (holidays.contains(date)) return true;
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return true;
        if (isFriOrSatDuty && (dow == DayOfWeek.MONDAY || dow == DayOfWeek.FRIDAY)) return true;
        return false;
    }
}
