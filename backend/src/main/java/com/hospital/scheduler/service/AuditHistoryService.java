package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.AuditHistoryRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    public Page<AuditHistoryResponse> getAllAuditHistory(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditHistoryRepository.findAllWithChangedBy(pageable)
                .map(AuditHistoryResponse::fromEntity);
    }

    public List<AuditHistoryResponse> getAuditHistoryByTableAndRecord(String tableName, Object recordId) {
        Integer id = recordId instanceof Integer ? (Integer) recordId
                  : recordId instanceof String ? Integer.parseInt((String) recordId) : null;
        return auditHistoryRepository.findByTableNameAndRecordIdWithChangedBy(tableName, id).stream()
                .map(AuditHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AuditHistoryResponse> getAuditHistoryByUser(Integer changedById) {
        return auditHistoryRepository.findByChangedBy(changedById).stream()
                .map(AuditHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<AuditHistoryResponse> getAuditHistoryByTableAndRecord(String tableName, Object recordId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Integer id = recordId instanceof Integer ? (Integer) recordId
                  : recordId instanceof String ? Integer.parseInt((String) recordId) : null;
        return auditHistoryRepository.findByTableNameAndRecordId(tableName, id, pageable)
                .map(AuditHistoryResponse::fromEntity);
    }

    public Page<AuditHistoryResponse> getAuditHistoryByUser(Integer changedById, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditHistoryRepository.findByChangedBy(changedById, pageable)
                .map(AuditHistoryResponse::fromEntity);
    }

    public Page<AuditHistoryResponse> getAuditHistoryByDateRange(LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditHistoryRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate, pageable)
                .map(AuditHistoryResponse::fromEntity);
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

    @Transactional
    public void deleteById(Integer id) {
        if (!auditHistoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy bản ghi nhật ký với id: " + id);
        }
        auditHistoryRepository.deleteById(id);
    }

    @Transactional
    public int deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<AuditHistory> existing = auditHistoryRepository.findAllById(ids);
        if (existing.isEmpty()) return 0;
        auditHistoryRepository.deleteAll(existing);
        return existing.size();
    }

    @Transactional
    public int deleteByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và ngày kết thúc không được để trống");
        }
        List<AuditHistory> records = auditHistoryRepository.findAllByCreatedAtBetween(startDate, endDate);
        if (records.isEmpty()) return 0;
        auditHistoryRepository.deleteAll(records);
        return records.size();
    }
}
