package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleExchangeDTO {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @NotNull(message = "ID nhân sự được yêu cầu đổi không được để trống")
    private Integer requesterScheduleId;

    @NotNull(message = "ID nhân sự được đổi không được để trống")
    private Integer targetScheduleId;

    @Size(max = 500, message = "Lý do không quá 500 ký tự")
    private String reason;
}
