package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRequest {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @NotNull(message = "Ngày làm việc không được để trống")
    private LocalDate workDate;

    @NotNull(message = "ID nhân sự không được để trống")
    private Integer staffId;

    @NotNull(message = "Mã loại ca không được để trống")
    private String shiftTypeId;

    private Integer requirementId;
}
