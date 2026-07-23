package com.hospital.scheduler.scheduling.domain;

import lombok.*;
import java.time.LocalDate;

/**
 * Schedule period domain.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulePeriod {
    private Integer id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
