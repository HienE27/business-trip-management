package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.SchedulePeriod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class SchedulePeriodRepositoryTest {

    @Autowired
    private SchedulePeriodRepository schedulePeriodRepository;

    @Test
    void shouldFindPeriodById() {
        assertThat(schedulePeriodRepository.findById(1)).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        assertThat(schedulePeriodRepository.findById(99999)).isEmpty();
    }
}
