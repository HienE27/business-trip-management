package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.dto.response.AuditHistorySummaryResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.AuditHistoryRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
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

    /**
     * Summary KPIs for the entire audit_history table.
     * Counts grouped by action type so the dashboard tiles always reflect the DB,
     * not just the current page slice.
     */
    public AuditHistorySummaryResponse getActionCounts() {
        return buildSummary(auditHistoryRepository.countAllGroupedByAction());
    }

    /**
     * Summary KPIs for the date range filter (inclusive start, exclusive end).
     * Mirrors the date filter on the audit list page so KPI numbers stay in sync.
     */
    public AuditHistorySummaryResponse getActionCountsBetween(LocalDateTime start, LocalDateTime end) {
        return buildSummary(auditHistoryRepository.countGroupedByActionBetween(start, end));
    }

    /**
     * Filtered summary KPIs that mirror every client-side filter on the audit list page.
     * Both date range and the other filters (table, action, search) are applied at the
     * DB layer so the totals stay accurate as the user narrows the result set.
     */
    public AuditHistorySummaryResponse getActionCountsFiltered(
            LocalDateTime start,
            LocalDateTime end,
            String tableName,
            com.hospital.scheduler.entity.AuditHistory.ActionType action,
            String search) {
        String normalizedSearch = (search == null || search.trim().isEmpty()) ? null : search.trim();
        String normalizedTable = (tableName == null || tableName.trim().isEmpty()) ? null : tableName.trim();
        return buildSummary(auditHistoryRepository.countGroupedByActionFiltered(
                start, end, normalizedTable, action, normalizedSearch));
    }

    private AuditHistorySummaryResponse buildSummary(List<Object[]> rows) {
        EnumMap<AuditHistory.ActionType, Long> counts = new EnumMap<>(AuditHistory.ActionType.class);
        long total = 0L;
        for (Object[] row : rows) {
            AuditHistory.ActionType action = (AuditHistory.ActionType) row[0];
            long count = ((Number) row[1]).longValue();
            counts.merge(action, count, Long::sum);
            total += count;
        }
        long create = counts.getOrDefault(AuditHistory.ActionType.INSERT, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.PUBLISH, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.APPROVE, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.REJECT, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.CANCEL, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.BULK_UPDATE, 0L);
        long update = counts.getOrDefault(AuditHistory.ActionType.UPDATE, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.BULK_UPDATE, 0L);
        long delete = counts.getOrDefault(AuditHistory.ActionType.DELETE, 0L)
                    + counts.getOrDefault(AuditHistory.ActionType.BULK_DELETE, 0L);
        return AuditHistorySummaryResponse.builder()
                .total(total)
                .create(create)
                .update(update)
                .delete(delete)
                .build();
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

    /**
     * Serialize an object to JSON for audit storage.
     * The entity fields passed to this method are already sanitized by the
     * calling service layer — the JSON output is safe for non-React contexts.
     * Jackson's writeValueAsString produces valid JSON and does not introduce
     * additional XSS vectors when consumed through standard JSON parsing.
     */
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

    /**
     * Wipe every row in audit_history. Intended for "Xóa tất cả" admin action —
     * the controller requires ADMIN role and the UI requires a typed confirmation,
     * so this is safe to keep as a single bulk-delete call.
     */
    @Transactional
    public int deleteAll() {
        int count = (int) auditHistoryRepository.count();
        if (count == 0) return 0;
        auditHistoryRepository.deleteAllInBatch();
        return count;
    }
}
