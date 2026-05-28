package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftRequirementDTO {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @NotNull(message = "Ngày làm việc không được để trống")
    private LocalDate workDate;

    @NotNull(message = "ID loại ca không được để trống")
    private String shiftTypeId;

    @NotNull(message = "ID chuyên môn không được để trống")
    private Integer specialtyId;

    @NotNull(message = "Số nhân sự yêu cầu không được để trống")
    @Min(value = 1, message = "Số nhân sự yêu cầu phải lớn hơn 0")
    private Integer requiredStaffCount;

    @Size(max = 500, message = "Ghi chú không quá 500 ký tự")
    private String note;
}
