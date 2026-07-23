package com.hospital.scheduler.scheduling.domain;

import lombok.*;
import java.time.LocalDate;

/**
 * Schedule assignment domain.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleAssignment {
    private Integer id;
    private Integer staffId;
    private String staffCode;
    private String staffName;
    private LocalDate date;
    private String shiftTypeCode;
    private Integer shiftTypeId;
    private boolean confirmed;
}
