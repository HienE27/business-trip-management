package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.AuditHistoryRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditHistoryService {

    private final AuditHistoryRepository auditHistoryRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper;

    public List<AuditHistoryResponse> getAllAuditHistory() {
        return auditHistoryRepository.findAll().stream()
                .map(AuditHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AuditHistoryResponse> getAuditHistoryByTableAndRecord(String tableName, Object recordId) {
        Integer id = recordId instanceof Integer ? (Integer) recordId
                  : recordId instanceof String ? Integer.parseInt((String) recordId) : null;
        return auditHistoryRepository.findByTableNameAndRecordId(tableName, id).stream()
                .map(AuditHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AuditHistoryResponse> getAuditHistoryByUser(Integer changedById) {
        return auditHistoryRepository.findByChangedBy(changedById).stream()
                .map(AuditHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AuditHistoryResponse> getAuditHistoryByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return auditHistoryRepository.findByDateRange(startDate, endDate).stream()
                .map(AuditHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public AuditHistory logAction(String tableName, Object recordId, AuditHistory.ActionType actionType,
                                  Object oldData, Object newData, Integer changedById) {
        Staff changedBy = null;
        if (changedById != null) {
            changedBy = staffRepository.findById(changedById).orElse(null);
        }

        AuditHistory auditHistory = AuditHistory.builder()
                .tableName(tableName)
                .recordId(recordId instanceof Integer ? (Integer) recordId
                        : recordId instanceof String ? Integer.parseInt((String) recordId) : 0)
                .actionType(actionType)
                .changedBy(changedBy)
                .oldData(safeToJson(oldData))
                .newData(safeToJson(newData))
                .build();

        return auditHistoryRepository.save(auditHistory);
    }

    private String safeToJson(Object data) {
        if (data == null) return null;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            String type = (data instanceof Class) ? ((Class<?>) data).getSimpleName() : data.getClass().getSimpleName();
            return "\"[" + type + "]: " + e.getMessage().replace("\"", "'") + "\"";
        }
    }
}
