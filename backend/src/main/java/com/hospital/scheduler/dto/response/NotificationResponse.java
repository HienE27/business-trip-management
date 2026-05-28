package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.Notification;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Integer id;
    private StaffSummary staff;
    private String title;
    private String message;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffSummary {
        private Integer id;
        private String fullName;
    }

    public static NotificationResponse fromEntity(Notification entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .staff(entity.getStaff() != null ? StaffSummary.builder()
                        .id(entity.getStaff().getId())
                        .fullName(entity.getStaff().getFullName())
                        .build() : null)
                .title(entity.getTitle())
                .message(entity.getMessage())
                .isRead(entity.getIsRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
