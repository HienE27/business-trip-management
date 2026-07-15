package com.hospital.scheduler.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ScheduleRepositoryTest {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Test
    void shouldFindSchedulesByPeriodId() {
        assertThat(scheduleRepository.findByPeriodId(4)).isNotEmpty();
    }

    @Test
    void shouldReturnEmptyForPeriodWithNoSchedules() {
        assertThat(scheduleRepository.findByPeriodId(99999)).isEmpty();
    }

    @Test
    void shouldFindSchedulesByStaffId() {
        assertThat(scheduleRepository.findByStaffId(1)).isNotNull();
    }

    @Test
    void shouldFindSchedulesByPeriodAndShiftType() {
        assertThat(scheduleRepository.findByPeriodIdAndShiftTypeId(4, "L01")).isNotEmpty();
    }
}
