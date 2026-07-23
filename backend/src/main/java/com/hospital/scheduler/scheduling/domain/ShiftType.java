package com.hospital.scheduler.scheduling.domain;

import lombok.*;
import java.time.LocalDate;

/**
 * Shift type domain.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftType {
    private Integer id;
    private String code;
    private String name;
    private boolean requiresOvernight;
    private Integer duration;
    private String color;
}
