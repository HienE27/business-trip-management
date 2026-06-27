package com.hospital.scheduler.config;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        List<Staff> withoutCode = staffRepository.findByStaffCodeIsNull();
        if (withoutCode.isEmpty()) {
            return;
        }

        // Find current max code number
        List<Staff> allStaff = staffRepository.findAll();
        int maxNum = allStaff.stream()
                .filter(s -> s.getStaffCode() != null && s.getStaffCode().length() > 2)
                .mapToInt(s -> {
                    try {
                        return Integer.parseInt(s.getStaffCode().substring(2));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);

        // Assign codes to null records
        for (int i = 0; i < withoutCode.size(); i++) {
            Staff s = withoutCode.get(i);
            s.setStaffCode(String.format("NV%03d", maxNum + i + 1));
            staffRepository.save(s);
        }

        log.info("StaffCodeInitializer: assigned staff_code to {} existing record(s)", withoutCode.size());
    }
}
