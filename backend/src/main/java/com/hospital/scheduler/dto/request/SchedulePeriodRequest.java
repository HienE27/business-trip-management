package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulePeriodRequest {

    @NotBlank(message = "Tên kỳ lịch không được để trống")
    @Size(max = 50, message = "Tên kỳ lịch không quá 50 ký tự")
    private String periodName;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;
}
