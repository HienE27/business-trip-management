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
 * If the computed compensation date falls on a holiday, it is shifted
 * forward to the next non-holiday, non-Mon, non-Fri day.
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
        return adjustForHolidays(raw);
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
            case FRIDAY    -> shiftDate.plusDays(11);  // T6 → T3 tuần sau (bỏ T7, CN, T2, T3, T4, T5, T6)
            case SATURDAY  -> shiftDate.plusDays(10);  // T7 → T3 tuần sau (bỏ CN, T2, T3, T4, T5, T6)
            case SUNDAY    -> shiftDate.plusDays(1);   // CN → T2
        };
    }

    private LocalDate adjustForHolidays(LocalDate date) {
        Set<LocalDate> holidays = holidayRepository
                .findActiveHolidaysBetween(date, date.plusYears(1))
                .stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());

        LocalDate current = date;
        int maxIterations = 30;
        int count = 0;

        while (holidays.contains(current) && count < maxIterations) {
            current = advanceToNextBusinessDay(current, holidays);
            count++;
        }

        return current;
    }

    private LocalDate advanceToNextBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        LocalDate next = date.plusDays(1);
        int maxIterations = 10;
        int count = 0;
        while (count < maxIterations) {
            DayOfWeek dow = next.getDayOfWeek();
            if (dow == DayOfWeek.MONDAY || dow == DayOfWeek.FRIDAY
                    || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                next = next.plusDays(1);
                count++;
            } else if (holidays.contains(next)) {
                next = next.plusDays(1);
                count++;
            } else {
                return next;
            }
        }
        return next;
    }
}
