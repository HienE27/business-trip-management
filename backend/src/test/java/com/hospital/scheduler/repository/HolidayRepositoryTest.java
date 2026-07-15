package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Holiday;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class HolidayRepositoryTest {

    @Autowired
    private HolidayRepository holidayRepository;

    @Test
    void shouldCheckExistingHolidayDate() {
        assertThat(holidayRepository.existsByHolidayDateAndIsActiveTrue(LocalDate.of(2026, 1, 1))).isTrue();
    }

    @Test
    void shouldCheckNonHolidayDate() {
        assertThat(holidayRepository.existsByHolidayDateAndIsActiveTrue(LocalDate.of(2026, 7, 15))).isFalse();
    }

    @Test
    void shouldFindActiveHolidaysBetweenDates() {
        assertThat(holidayRepository.findActiveHolidaysBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .isNotEmpty();
    }

    @Test
    void shouldReturnEmptyForDateRangeWithNoHolidays() {
        assertThat(holidayRepository.findActiveHolidaysBetween(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
                .isEmpty();
    }

    @Test
    void shouldFindActiveHolidaysByYear() {
        assertThat(holidayRepository.findByYear(2026)).isNotEmpty();
    }
}
