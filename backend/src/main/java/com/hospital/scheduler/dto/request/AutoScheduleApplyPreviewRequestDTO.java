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

    @Builder.Default
    private List<RemovedScheduleItem> removedSchedules = List.of();

    /**
     * BUGFIX (coverage drift): opt-in destructive flag. When false (default),
     * apply throws BadRequestException if the period already has schedules —
     * protecting manual assignments from being silently deleted by auto-schedule.
     * The Manager must explicitly confirm overwrite via UI prompt.
     */
    @Builder.Default
    private Boolean overwriteExisting = false;

    /**
     * Hard upper bound on rows that may be inserted in this apply call. Used as
     * a safety net against runaway previews that exceed historical capacity and
     * would otherwise inflate the "Tỷ lệ phủ" KPI on the dashboard.
     */
    @Builder.Default
    private Integer maxSchedulesToInsert = 2000;

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

        /**
         * BUGFIX (was M07 #8): L04 (Phòng khám chuyên gia) can have multiple
         * requirements on the same {@code workDate} + {@code shiftTypeId} when
         * multiple specialties serve the slot. Without an explicit id, the
         * resolver had to use {@code findFirst()} which would silently link
         * the wrong specialty. The frontend embeds the requirement id when
         * the preview is built so this mapping is deterministic.
         *
         * Optional for backwards compatibility — when null, the resolver
         * still falls back to (workDate, shiftTypeId) only if that
         * combination is unique within the period. Ambiguous cases (L04 with
         * multiple specialties) require this id to be set.
         */
        private Integer requirementId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RemovedScheduleItem {
        @NotNull(message = "staffId không được để trống")
        private Integer staffId;

        @NotNull(message = "workDate không được để trống")
        private String workDate;

        @NotNull(message = "shiftTypeId không được để trống")
        private String shiftTypeId;
    }
}
