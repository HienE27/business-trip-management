package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.AlgorithmConfigAudit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmConfigAuditDTO {
    private Long id;
    private String paramKey;
    private String oldValue;
    private String newValue;
    private String action;
    private String changedByUsername;
    private LocalDateTime createdAt;

    public static AlgorithmConfigAuditDTO from(AlgorithmConfigAudit a) {
        return AlgorithmConfigAuditDTO.builder()
                .id(a.getId())
                .paramKey(a.getParamKey())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .action(a.getAction().name())
                .changedByUsername(a.getChangedByUsername())
                .createdAt(a.getCreatedAt())
                .build();
    }
}