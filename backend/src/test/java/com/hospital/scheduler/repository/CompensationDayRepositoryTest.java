package com.hospital.scheduler.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class CompensationDayRepositoryTest {

    @Autowired
    private CompensationDayRepository compensationDayRepository;

    @Test
    void shouldFindCompensationDayByScheduleId() {
        assertThat(compensationDayRepository.findByScheduleId(99999)).isEmpty();
    }

    @Test
    void shouldCheckStaffCompensationDay() {
        assertThat(compensationDayRepository.existsByStaffIdAndCompensationDate(1, LocalDate.of(2026, 7, 8))).isFalse();
    }

    @Test
    void shouldFindCompensationDaysByScheduleIds() {
        assertThat(compensationDayRepository.findByScheduleIds(java.util.List.of(99999))).isEmpty();
    }
}
