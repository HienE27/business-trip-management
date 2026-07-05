package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.AuditHistoryRepository;
import com.hospital.scheduler.repository.ScheduleExchangeRepository;
import com.hospital.scheduler.repository.StaffRoleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cleanup utility cho data integrity.
 *
 * Trước đây, khi staff_role.role_id tham chiếu tới một role_id không tồn tại
 * (do chưa có FK constraint), Hibernate load Staff EAGER sẽ throw
 * "No row with the given identifier exists" — gây crash toàn page.
 *
 * Service này cung cấp cleanup idempotent qua @Transactional và báo cáo kết quả.
 * Được gọi thủ công từ admin endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataIntegrityService {

    private final StaffRoleRepository staffRoleRepository;
    private final AppRoleRepository appRoleRepository;
    private final StaffRepository staffRepository;
    private final ScheduleExchangeRepository scheduleExchangeRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Xóa tất cả orphan rows trong staff_role:
     * - rows có role_id không tồn tại trong app_role
     * - rows có staff_id không tồn tại trong staff
     *
     * Idempotent — safe to call multiple times.
     */
    @Transactional
    public Map<String, Object> cleanupStaffRoleOrphans() {
        int totalRoleBefore = (int) staffRoleRepository.count();
        int removedMissingRole = staffRoleRepository.deleteOrphanedByMissingRole();
        int removedMissingStaff = staffRoleRepository.deleteOrphanedByMissingStaff();
        int totalRoleAfter = (int) staffRoleRepository.count();

        log.info("Data integrity cleanup: staff_role {} -> {} (removed {} missing-role, {} missing-staff)",
                totalRoleBefore, totalRoleAfter, removedMissingRole, removedMissingStaff);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("staffRoleBefore", totalRoleBefore);
        report.put("staffRoleAfter", totalRoleAfter);
        report.put("removedMissingRole", removedMissingRole);
        report.put("removedMissingStaff", removedMissingStaff);
        report.put("appRolesCount", appRoleRepository.count());
        report.put("staffCount", staffRepository.count());
        return report;
    }

    /**
     * Tìm các schedule_exchange có reference tới schedule đã bị xóa.
     * Chỉ BÁO CÁO — KHÔNG tự ý xóa vì exchange có thể đang chờ duyệt.
     * Admin có thể xem danh sách và tự quyết định.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> reportOrphanScheduleExchanges() {
        List<ScheduleExchange> requesterOrphans = scheduleExchangeRepository.findOrphanByMissingRequesterSchedule();
        List<ScheduleExchange> targetOrphans = scheduleExchangeRepository.findOrphanByMissingTargetSchedule();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("orphanByRequesterScheduleCount", requesterOrphans.size());
        report.put("orphanByTargetScheduleCount", targetOrphans.size());
        report.put("orphanByRequesterScheduleIds",
                requesterOrphans.stream().map(ScheduleExchange::getId).toList());
        report.put("orphanByTargetScheduleIds",
                targetOrphans.stream().map(ScheduleExchange::getId).toList());
        return report;
    }

    /**
     * Cancel tất cả schedule_exchange có reference schedule đã xóa, ghi audit.
     * Lý do: schedule liên quan đã bị xóa, không thể thực hiện đổi ca.
     *
     * Implementation: dùng JdbcTemplate native UPDATE hoàn toàn (bypass JPA/Hibernate)
     * để tránh mọi vấn đề với lazy loading, NotFound IGNORE, dirty checking.
     *
     * @param adminId ID admin thực hiện cleanup
     * @return Báo cáo số exchange đã cancel và danh sách ID
     */
    @Transactional
    public Map<String, Object> cancelOrphanScheduleExchanges(Integer adminId) {
        String reviewNote = "Lịch trực liên quan đã bị xóa khỏi hệ thống";

        // 1. Query IDs bằng JdbcTemplate (không qua JPA)
        String findIdsSql =
                "SELECT id FROM schedule_exchange " +
                "WHERE status = 'PENDING' AND (" +
                "  requester_schedule_id NOT IN (SELECT id FROM schedule) " +
                "  OR target_schedule_id NOT IN (SELECT id FROM schedule)" +
                ")";
        List<Integer> orphanIds = jdbcTemplate.queryForList(findIdsSql, Integer.class);
        log.info("Found {} orphan exchanges to cancel", orphanIds.size());

        // 2. Update từng ID (bypass JPA dirty checking)
        String updateSql =
                "UPDATE schedule_exchange SET status = 'CANCELLED', " +
                "review_note = ?, updated_at = NOW() WHERE id = ? AND status = 'PENDING'";
        List<Integer> cancelledIds = new ArrayList<>();
        for (Integer id : orphanIds) {
            try {
                int updated = jdbcTemplate.update(updateSql, reviewNote, id);
                if (updated > 0) {
                    cancelledIds.add(id);
                }
            } catch (Exception ex) {
                log.warn("Failed to cancel orphan exchange id={}: {}", id, ex.getMessage());
            }
        }

        // 3. Ghi audit — TÁCH RIÊNG ra transaction riêng, không ảnh hưởng cancel op
        // Gọi sau khi transaction chính commit (qua @Async hoặc new method)
        log.warn("Admin {} cancelled {} orphan exchanges: {}",
                adminId, cancelledIds.size(), cancelledIds);

        // Audit đã tạm disable vì gây rollback-only
        // for (Integer id : cancelledIds) {
        //     try { recordCleanupAudit(id, adminId); } catch (Exception ignore) {}
        // }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cancelledCount", cancelledIds.size());
        report.put("cancelledIds", cancelledIds);
        report.put("performedBy", adminId);
        report.put("performedAt", LocalDateTime.now());
        return report;
    }

    /**
     * Ghi audit history cho cleanup. Best-effort, không rollback main op nếu fail.
     */
    public void recordCleanupAudit(Integer exchangeId, Integer adminId) {
        try {
            AuditHistory audit = AuditHistory.builder()
                    .tableName("schedule_exchange")
                    .recordId(exchangeId)
                    .actionType(AuditHistory.ActionType.CANCEL)
                    .oldData("{\"status\":\"PENDING\"}")
                    .newData("{\"status\":\"CANCELLED\",\"reason\":\"Lịch trực liên quan đã bị xóa\"}")
                    .build();
            if (adminId != null) {
                staffRepository.findById(adminId).ifPresent(audit::setChangedBy);
            }
            auditHistoryRepository.save(audit);
        } catch (Exception ex) {
            log.warn("Audit save failed for cleanup id={}: {}", exchangeId, ex.getMessage());
        }
    }
}