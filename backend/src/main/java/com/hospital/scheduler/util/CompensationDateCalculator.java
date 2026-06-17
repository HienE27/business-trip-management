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
        return adjustForHolidays(raw, isFriOrSatDuty);
    }

    private boolean isFriOrSatDuty(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY;
    }

    /**
     * Calculate compensation date without holiday adjustment.
     * Useful when holiday handling is managed separately.
     */
    public LocalDate calculateWithoutHolidays(LocalDate shiftDate) {
        return computeBase(shiftDate);
    }

    private LocalDate computeBase(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return switch (dow) {
            case MONDAY    -> shiftDate.plusDays(1);   // T2 → T3
            case TUESDAY   -> shiftDate.plusDays(1);   // T3 → T4
            case WEDNESDAY -> shiftDate.plusDays(1);   // T4 → T5
            case THURSDAY  -> shiftDate.plusDays(1);   // T5 → T6
            case FRIDAY    -> shiftDate.plusDays(4);   // T6 → T3 tuần sau (bỏ T7, CN, T2)
            case SATURDAY  -> shiftDate.plusDays(3);   // T7 → T3 tuần sau (bỏ CN, T2)
            case SUNDAY    -> shiftDate.plusDays(1);   // CN → T2
        };
    }

    private LocalDate adjustForHolidays(LocalDate date, boolean isFriOrSatDuty) {
        Set<LocalDate> holidays = holidayRepository
                .findActiveHolidaysBetween(date, date.plusYears(1))
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        LocalDate current = date;
        int maxIterations = 30;
        int count = 0;

        while (holidays.contains(current) && count < maxIterations) {
            current = advanceToNextValidDay(current, holidays, isFriOrSatDuty);
            count++;
        }

        return current;
    }

    /**
     * Advance to the next valid day.
     * - If original duty was Fri or Sat: skip Mon, Fri, Sat, Sun (plus holidays)
     * - If original duty was Mon–Thu or Sun: skip Sat, Sun only (plus holidays)
     *   BUT Mon compensation day lands on Tue (by base rule), not Mon,
     *   and Fri compensation day lands on Tue next week (by base rule), so
     *   for Mon–Thu duty, the compensation lands on a weekday (T+1).
     *   Only if that weekday is a holiday do we skip to the next non-holiday weekday.
     */
    private LocalDate advanceToNextValidDay(LocalDate date, Set<LocalDate> holidays, boolean isFriOrSatDuty) {
        LocalDate next = date.plusDays(1);
        int maxIterations = 15;
        int count = 0;

        while (count < maxIterations) {
            DayOfWeek dow = next.getDayOfWeek();

            // Always skip weekends
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                next = next.plusDays(1);
                count++;
                continue;
            }

            // If duty was Fri/Sat, also skip Mon and Fri per spec rule
            if (isFriOrSatDuty && (dow == DayOfWeek.MONDAY || dow == DayOfWeek.FRIDAY)) {
                next = next.plusDays(1);
                count++;
                continue;
            }

            // If holiday, advance
            if (holidays.contains(next)) {
                next = next.plusDays(1);
                count++;
                continue;
            }

            // Found a valid day
            return next;
        }
        return next;
    }
}
