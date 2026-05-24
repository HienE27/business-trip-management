package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftTypeRequest {

    @NotBlank(message = "Mã loại ca không được để trống")
    @Size(max = 10, message = "Mã loại ca không quá 10 ký tự")
    private String id;

    @NotBlank(message = "Tên loại ca không được để trống")
    @Size(max = 50, message = "Tên loại ca không quá 50 ký tự")
    private String name;

    private String description;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isOvernight = false;
    private Integer fatigueScore = 1;
}
