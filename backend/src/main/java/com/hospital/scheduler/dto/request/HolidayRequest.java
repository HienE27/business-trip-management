package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayRequest {

    @NotBlank(message = "Tên ngày lễ không được để trống")
    @Size(max = 255, message = "Tên ngày lễ không quá 255 ký tự")
    private String name;

    @NotNull(message = "Ngày lễ không được để trống")
    private LocalDate holidayDate;

    private Boolean isNationalHoliday;

    private String description;
}
