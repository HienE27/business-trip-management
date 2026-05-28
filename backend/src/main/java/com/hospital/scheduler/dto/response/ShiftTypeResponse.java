package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftTypeResponse {
    private String id;
    private String name;
    private String description;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isOvernight;
    private Integer fatigueScore;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
