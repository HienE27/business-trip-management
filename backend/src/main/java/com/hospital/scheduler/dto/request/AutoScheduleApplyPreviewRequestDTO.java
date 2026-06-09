package com.hospital.scheduler.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoScheduleApplyPreviewRequestDTO {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @Builder.Default
    private String algorithmType = "GREEDY";

    @Valid
    @NotEmpty(message = "Danh sách lịch xem trước không được để trống")
    private List<PreviewScheduleItem> schedules;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewScheduleItem {
        @NotNull(message = "staffId không được để trống")
        private Integer staffId;

        @NotNull(message = "workDate không được để trống")
        private String workDate;

        @NotNull(message = "shiftTypeId không được để trống")
        private String shiftTypeId;
    }
}
