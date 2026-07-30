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
    private String action;
    private Integer userId;
    private String userName;
    private StaffSummary changedBy;
    private String oldData;
    private String newData;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;

    public enum ActionType {
        INSERT, UPDATE, DELETE,
        PUBLISH,
        APPROVE, REJECT, CANCEL,
        BULK_DELETE, BULK_UPDATE
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
        AuditHistory.ActionType entityAction = entity.getActionType();
        AuditHistoryResponse response = AuditHistoryResponse.builder()
                .id(entity.getId())
                .tableName(entity.getTableName())
                .recordId(entity.getRecordId())
                .actionType(toResponseActionType(entityAction))
                .action(switch (entityAction) {
                    case INSERT -> "CREATE";
                    case UPDATE -> "UPDATE";
                    case DELETE -> "DELETE";
                    case PUBLISH -> "PUBLISH";
                    case APPROVE -> "APPROVE";
                    case REJECT -> "REJECT";
                    case CANCEL -> "CANCEL";
                    case BULK_DELETE -> "BULK_DELETE";
                    case BULK_UPDATE -> "BULK_UPDATE";
                    case REQUIREMENT_MIGRATION_NULL_TO_ANY -> "REQUIREMENT_MIGRATION";
                })
                .oldData(entity.getOldData())
                .newData(entity.getNewData())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
        if (entity.getChangedBy() != null) {
            response.setChangedBy(StaffSummary.builder()
                    .id(entity.getChangedBy().getId())
                    .fullName(entity.getChangedBy().getFullName())
                    .build());
            response.setUserId(entity.getChangedBy().getId());
            response.setUserName(entity.getChangedBy().getFullName());
        }
        return response;
    }

    private static ActionType toResponseActionType(AuditHistory.ActionType entityAction) {
        return switch (entityAction) {
            case INSERT -> ActionType.INSERT;
            case UPDATE -> ActionType.UPDATE;
            case DELETE -> ActionType.DELETE;
            case PUBLISH -> ActionType.PUBLISH;
            case APPROVE -> ActionType.APPROVE;
            case REJECT -> ActionType.REJECT;
            case CANCEL -> ActionType.CANCEL;
            case BULK_DELETE -> ActionType.BULK_DELETE;
            case BULK_UPDATE -> ActionType.BULK_UPDATE;
            case REQUIREMENT_MIGRATION_NULL_TO_ANY -> ActionType.INSERT;
        };
    }
}
