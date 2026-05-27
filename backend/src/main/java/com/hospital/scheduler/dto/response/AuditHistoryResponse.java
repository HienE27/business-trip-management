package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.AuditHistory;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditHistoryResponse {

    private Integer id;
    private String tableName;
    private Integer recordId;
    private ActionType actionType;
    private StaffSummary changedBy;
    private String oldData;
    private String newData;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;

    public enum ActionType {
        INSERT, UPDATE, DELETE
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

    public static AuditHistoryResponse fromEntity(AuditHistory entity) {
        return AuditHistoryResponse.builder()
                .id(entity.getId())
                .tableName(entity.getTableName())
                .recordId(entity.getRecordId())
                .actionType(ActionType.valueOf(entity.getActionType().name()))
                .changedBy(entity.getChangedBy() != null ? StaffSummary.builder()
                        .id(entity.getChangedBy().getId())
                        .fullName(entity.getChangedBy().getFullName())
                        .build() : null)
                .oldData(entity.getOldData())
                .newData(entity.getNewData())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
