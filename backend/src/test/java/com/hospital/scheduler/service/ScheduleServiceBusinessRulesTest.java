package com.hospital.scheduler.service;

import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScheduleServiceBusinessRulesTest {

    @Test
    void calculatesCompensationDateForEachDutyDayRule() {
        CompensationDateCalculator calculator = new CompensationDateCalculator(
                mock(HolidayRepository.class)
        );

        // Monday duty → Tuesday (next day)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 2));
        // Tuesday duty → Wednesday (next day)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 2)))
                .isEqualTo(LocalDate.of(2026, 6, 3));
        // Wednesday duty → Thursday (next day)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 3)))
                .isEqualTo(LocalDate.of(2026, 6, 4));
        // Thursday duty → Friday (next day)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 4)))
                .isEqualTo(LocalDate.of(2026, 6, 5));
        // Friday duty → Tuesday NEXT WEEK (skip T7, CN, T2)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 5)))
                .isEqualTo(LocalDate.of(2026, 6, 9));
        // Saturday duty → Tuesday NEXT WEEK (skip CN, T2)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 6)))
                .isEqualTo(LocalDate.of(2026, 6, 9));
        // Sunday duty → Monday (next day)
        assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 7)))
                .isEqualTo(LocalDate.of(2026, 6, 8));
    }
}
