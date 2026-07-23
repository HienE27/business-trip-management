package com.hospital.scheduler.scheduling.domain;

import lombok.*;
import java.time.LocalDate;

/**
 * Staff domain.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {
    private Integer id;
    private String code;
    private String name;
    private String fullName;
    private Integer specialtyId;
    private boolean active;
    private Integer maxShiftsPerMonth;
}
