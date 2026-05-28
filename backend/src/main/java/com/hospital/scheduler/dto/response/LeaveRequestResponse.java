package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.LeaveRequest;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
