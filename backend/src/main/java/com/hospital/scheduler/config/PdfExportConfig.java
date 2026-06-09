package com.hospital.scheduler.config;

import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.service.SchedulePdfExportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PdfExportConfig {

    @Bean
    public SchedulePdfExportService schedulePdfExportService(ScheduleRepository scheduleRepository) {
        return new SchedulePdfExportService(scheduleRepository);
    }
}
