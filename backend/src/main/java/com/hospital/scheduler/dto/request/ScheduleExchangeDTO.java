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

    @NotNull(message = "ID lịch của người yêu cầu đổi không được để trống")
    private Integer requesterScheduleId;

    @NotNull(message = "ID lịch của người được đổi không được để trống")
    private Integer targetScheduleId;

    @Size(max = 500, message = "Lý do không quá 500 ký tự")
    private String reason;
}
