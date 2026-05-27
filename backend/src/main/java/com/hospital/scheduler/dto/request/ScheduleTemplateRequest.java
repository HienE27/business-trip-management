package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplateRequest {

    @NotBlank(message = "Tên mẫu lịch không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Thứ trong tuần không được để trống")
    @Min(value = 1, message = "Thứ phải từ 1 (Thứ 2) đến 7 (Chủ Nhật)")
    @Max(value = 7, message = "Thứ phải từ 1 (Thứ 2) đến 7 (Chủ Nhật)")
    private Integer dayOfWeek;

    @NotBlank(message = "Mã loại ca không được để trống")
    private String shiftTypeId;

    private Integer specialtyId;

    @Min(value = 1, message = "Số nhân sự tối thiểu là 1")
    private Integer requiredStaffCount = 1;
}
