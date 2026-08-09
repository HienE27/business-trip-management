package com.hospital.scheduler;

import com.hospital.scheduler.scheduling.strategy.StrategyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(StrategyProperties.class)
public class HospitalSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalSchedulerApplication.class, args);
    }
}
