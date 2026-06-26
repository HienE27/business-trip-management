package com.hospital.scheduler.config;

import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time initialization for staff_code on existing records.
 * Safe to run repeatedly — only updates records where staff_code is null.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class StaffCodeInitializer implements ApplicationRunner {

    private final StaffRepository staffRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = staffRepository.backfillStaffCodes();
        if (updated > 0) {
            log.info("StaffCodeInitializer: backfilled {} existing staff_code(s)", updated);
        }
    }
}
