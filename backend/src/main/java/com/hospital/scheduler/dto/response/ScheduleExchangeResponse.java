package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.ScheduleExchange;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleExchangeResponse {

    private Integer id;
    private Integer periodId;
    private StaffSummary requester;
    private StaffSummary target;
    private ScheduleSummary requesterSchedule;
    private ScheduleSummary targetSchedule;
    private String reason;
    private ExchangeStatus status;
    private StaffSummary reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ExchangeStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffSummary {
        private Integer id;
        private String fullName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScheduleSummary {
        private Integer id;
        private LocalDate workDate;
        private ShiftTypeSummary shiftType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeSummary {
        private String id;
        private String name;
    }

    public static ScheduleExchangeResponse fromEntity(ScheduleExchange entity) {
        return ScheduleExchangeResponse.builder()
                .id(entity.getId())
                .periodId(entity.getPeriod().getId())
                .requester(entity.getRequester() != null ? StaffSummary.builder()
                        .id(entity.getRequester().getId())
                        .fullName(entity.getRequester().getFullName())
                        .build() : null)
                .target(entity.getTarget() != null ? StaffSummary.builder()
                        .id(entity.getTarget().getId())
                        .fullName(entity.getTarget().getFullName())
                        .build() : null)
                .requesterSchedule(entity.getRequesterSchedule() != null ? ScheduleSummary.builder()
                        .id(entity.getRequesterSchedule().getId())
                        .workDate(entity.getRequesterSchedule().getWorkDate())
                        .shiftType(entity.getRequesterSchedule().getShiftType() != null ? ShiftTypeSummary.builder()
                                .id(entity.getRequesterSchedule().getShiftType().getId())
                                .name(entity.getRequesterSchedule().getShiftType().getName())
                                .build() : null)
                        .build() : null)
                .targetSchedule(entity.getTargetSchedule() != null ? ScheduleSummary.builder()
                        .id(entity.getTargetSchedule().getId())
                        .workDate(entity.getTargetSchedule().getWorkDate())
                        .shiftType(entity.getTargetSchedule().getShiftType() != null ? ShiftTypeSummary.builder()
                                .id(entity.getTargetSchedule().getShiftType().getId())
                                .name(entity.getTargetSchedule().getShiftType().getName())
                                .build() : null)
                        .build() : null)
                .reason(entity.getReason())
                .status(ExchangeStatus.valueOf(entity.getStatus().name()))
                .reviewedBy(entity.getReviewedBy() != null ? StaffSummary.builder()
                        .id(entity.getReviewedBy().getId())
                        .fullName(entity.getReviewedBy().getFullName())
                        .build() : null)
                .reviewedAt(entity.getReviewedAt())
                .reviewNote(entity.getReviewNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
