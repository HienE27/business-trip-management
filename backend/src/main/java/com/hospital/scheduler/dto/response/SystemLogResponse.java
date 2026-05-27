package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.SystemLog;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLogResponse {

    private Integer id;
    private StaffSummary staff;
    private String actionType;
    private String description;
    private String ipAddress;
    private String userAgent;
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

    public static SystemLogResponse fromEntity(SystemLog entity) {
        return SystemLogResponse.builder()
                .id(entity.getId())
                .staff(entity.getStaff() != null ? StaffSummary.builder()
                        .id(entity.getStaff().getId())
                        .fullName(entity.getStaff().getFullName())
                        .build() : null)
                .actionType(entity.getActionType())
                .description(entity.getDescription())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
