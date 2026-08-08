package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.dto.response.AuditHistorySummaryResponse;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
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

    /**
     * Filtered listing of audit history. All filter args are optional.
     * Delegates to {@link AuditHistoryRepository#findAllFiltered} so the DB owns
     * the WHERE clause — pagination + totals reflect the filtered set, not just
     * the slice currently visible to the client.
     */
    public Page<AuditHistoryResponse> getAllAuditHistoryFiltered(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String tableName,
            String actionRaw,
            String search,
            int page,
            int size) {
        String normalizedTable = (tableName == null || tableName.trim().isEmpty()) ? null : tableName.trim();
        String normalizedSearch = (search == null || search.trim().isEmpty()) ? null : search.trim();
        AuditHistory.ActionType actionEnum = null;
        if (actionRaw != null && !actionRaw.isBlank()) {
            // Mirror the summary endpoint's CREATE→INSERT mapping so the FE
            // can keep using the user-facing labels (Tạo mới/Cập nhật/Xóa).
            String normalized = actionRaw.trim().toUpperCase();
            if (normalized.equals("CREATE")) normalized = "INSERT";
            try {
                actionEnum = AuditHistory.ActionType.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                // Unknown action → ignore filter rather than 400.
            }
        }
        Pageable pageable = PageRequest.of(page, size);
        return auditHistoryRepository.findAllFiltered(
                startDate, endDate, normalizedTable, actionEnum, normalizedSearch, pageable)
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
     * Distinct tableName values across the entire audit_history table.
     * BUGFIX (was BE#C): feed the modules filter dropdown with the real set
     * of modules so users can filter by table that may not appear on the
     * current page slice.
     */
    /**
     * Returns the union of the canonical module list (so users can filter on
     * tables that have not yet accumulated audit history) and any additional
     * modules that already have audit history in the database.
     *
     * <p>Without the canonical list, the dropdown would only show tables that
     * have been touched by an audit action — modules like {@code staff},
     * {@code role}, {@code permissions} are absent until someone modifies
     * them, leaving the user unable to filter on them.
     */
    public List<String> getDistinctTableNames() {
        // Canonical list — keep alphabetically sorted so the FE renders a stable order.
        List<String> canonical = List.of(
                "algorithm_config",
                "algorithm_metrics",
                "audit_history",
                "compensation_day",
                "file_attachment",
                "holiday",
                "leave_request",
                "notification",
                "permissions",
                "role",
                "role_permission",
                "schedule",
                "schedule_conflict",
                "schedule_period",
                "schedule_requirement",
                "shift_requirement",
                "shift_type",
                "specialty",
                "staff",
                "staff_role",
                "user_account"
        );
        List<String> fromDb = auditHistoryRepository.findDistinctTableNames();
        // Union, preserving canonical order first, then any dynamic extras.
        LinkedHashSet<String> merged = new LinkedHashSet<>(canonical);
        if (fromDb != null) {
            for (String t : fromDb) {
                if (t != null && !t.isBlank()) merged.add(t);
            }
        }
        return new ArrayList<>(merged);
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
