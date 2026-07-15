package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class StaffRepositoryTest {

    @Autowired
    private StaffRepository staffRepository;

    @Test
    void shouldFindStaffByUsernameWhenExists() {
        assertThat(staffRepository.findByUsername("admin")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenUsernameNotFound() {
        assertThat(staffRepository.findByUsername("nonexistent")).isEmpty();
    }

    @Test
    void shouldFindActiveStaff() {
        assertThat(staffRepository.findByIsActiveTrue()).isNotEmpty();
    }

    @Test
    void shouldCheckExistingStaffCode() {
        assertThat(staffRepository.existsByStaffCode("NV001")).isTrue();
    }

    @Test
    void shouldCheckNonExistingStaffCode() {
        assertThat(staffRepository.existsByStaffCode("ZZZZZ")).isFalse();
    }
}
