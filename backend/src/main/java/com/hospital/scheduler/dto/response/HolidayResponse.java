package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayResponse {
    private Integer id;
    private String name;
    private LocalDate holidayDate;
    private Integer year;
    private Boolean isNationalHoliday;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
