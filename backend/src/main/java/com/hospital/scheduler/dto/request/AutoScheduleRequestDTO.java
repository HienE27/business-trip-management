package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoScheduleRequestDTO {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @Builder.Default
    private String algorithmType = "GREEDY";

    @Builder.Default
    private Integer maxIterations = 1000;

    @Builder.Default
    private Boolean autoAssign = true;

    @Builder.Default
    private Boolean autoGenerateRequirements = false;

    @Builder.Default
    private List<Integer> excludedStaffIds = List.of();
}
