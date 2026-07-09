package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Schedule;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponse {

    private Integer id;
    private StaffSummary staff;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LeaveStatus status;
    private StaffSummary reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private Integer periodId;
    private String periodName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * IDs of schedules that overlap with this leave request and were flagged
     * hasConflict=true on approval. Empty when approval had no overlap.
     * Populated by {@link com.hospital.scheduler.service.LeaveRequestService#approveLeaveRequest}.
     */
    @Builder.Default
    private List<Integer> affectedScheduleIds = Collections.emptyList();

    public enum LeaveStatus {
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

    public static LeaveRequestResponse fromEntity(LeaveRequest entity) {
        return fromEntity(entity, Collections.emptyList());
    }

    public static LeaveRequestResponse fromEntity(LeaveRequest entity, List<Schedule> affectedSchedules) {
        List<Integer> ids = affectedSchedules == null ? Collections.emptyList()
                : affectedSchedules.stream().map(Schedule::getId).collect(Collectors.toList());
        return LeaveRequestResponse.builder()
                .id(entity.getId())
                .staff(entity.getStaff() != null ? StaffSummary.builder()
                        .id(entity.getStaff().getId())
                        .fullName(entity.getStaff().getFullName())
                        .build() : null)
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .reason(entity.getReason())
                .status(LeaveStatus.valueOf(entity.getStatus().name()))
                .reviewedBy(entity.getReviewedBy() != null ? StaffSummary.builder()
                        .id(entity.getReviewedBy().getId())
                        .fullName(entity.getReviewedBy().getFullName())
                        .build() : null)
                .reviewedAt(entity.getReviewedAt())
                .reviewNote(entity.getReviewNote())
                .periodId(entity.getPeriod() != null ? entity.getPeriod().getId() : null)
                .periodName(entity.getPeriod() != null ? entity.getPeriod().getPeriodName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .affectedScheduleIds(ids)
                .build();
    }
}
