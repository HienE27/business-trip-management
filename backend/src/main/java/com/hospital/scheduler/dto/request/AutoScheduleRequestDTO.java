package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoScheduleRequestDTO {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    private String algorithmType = "GREEDY";

    private Integer maxIterations = 1000;

    private Boolean autoAssign = true;
}
