package com.hospital.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HospitalSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalSchedulerApplication.class, args);
    }
}
