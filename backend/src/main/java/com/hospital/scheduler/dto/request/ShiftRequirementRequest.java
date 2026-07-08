package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftRequirementRequest {

    @NotNull(message = "Ngày làm việc không được để trống")
    private java.time.LocalDate workDate;

    @NotBlank(message = "Mã loại ca không được để trống")
    private String shiftTypeId;

    private Integer specialtyId;

    @NotNull(message = "Số nhân sự yêu cầu không được để trống")
    @Min(value = 0, message = "Số nhân sự yêu cầu phải >= 0")
    private Integer requiredStaffCount;

    private String note;
}