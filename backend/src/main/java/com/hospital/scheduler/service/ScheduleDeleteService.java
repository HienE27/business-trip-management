package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleDeleteService {

    private final ScheduleRepository scheduleRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final ScheduleExchangeRepository scheduleExchangeRepository;
    private final AuditHistoryService auditHistoryService;
    private final NotificationService notificationService;
    private final AuthContextService authContextService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void deleteSchedule(Integer id) {
        log.info("Starting deleteSchedule for scheduleId={}", id);
        try {
            // Find the schedule
            Schedule schedule = scheduleRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + id));

            // Check period status
            SchedulePeriod period = schedule.getPeriod();
            log.info("Schedule found: periodId={}, periodStatus={}", period.getId(), period.getStatus());
            if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
                String currentStatus = switch (period.getStatus()) {
                    case PUBLISHED -> "đã công bố";
                    case ARCHIVED -> "đã lưu trữ";
                    default -> period.getStatus().name();
                };
                throw new BadRequestException("Không thể xóa lịch: Kỳ lịch \"" + period.getPeriodName() + "\" đang ở trạng thái " + currentStatus + ". Vui lòng chuyển kỳ lịch về trạng thái Nháp (DRAFT) trước khi xóa lịch.");
            }

            // If L01, delete related compensation days using native SQL
            if ("L01".equals(schedule.getShiftType().getId())) {
                List<CompensationDay> compDays = compensationDayRepository.findByScheduleId(id);
                log.info("Found {} compensation days to delete for scheduleId={}", compDays.size(), id);
                for (CompensationDay cd : compDays) {
                    auditHistoryService.logAction("compensation_day", cd.getId(), AuditHistory.ActionType.DELETE, cd, null, authContextService.getCurrentStaff().getId());
                }
                int compDeleted = jdbcTemplate.update("DELETE FROM compensation_day WHERE schedule_id = ?", id);
                log.info("Deleted {} compensation days using native SQL for scheduleId={}", compDeleted, id);
            }

            // CRITICAL: Clear schedule_id FK in compensation_day table before deleting schedule
            // This prevents FK constraint violation when deleting the parent schedule
            // Must do this regardless of shift type to handle orphaned records
            jdbcTemplate.update("UPDATE compensation_day SET schedule_id = NULL WHERE schedule_id = ?", id);

            // Delete ScheduleExchange records using native SQL
            List<ScheduleExchange> exchanges = scheduleExchangeRepository.findByRequesterScheduleIdOrTargetScheduleId(id, id);
            log.info("Found {} schedule exchanges to delete for scheduleId={}", exchanges.size(), id);
            int exchangesDeleted = jdbcTemplate.update("DELETE FROM schedule_exchange WHERE requester_schedule_id = ? OR target_schedule_id = ?", id, id);
            log.info("Deleted {} schedule exchanges using native SQL for scheduleId={}", exchangesDeleted, id);

            // Delete ScheduleConflict records using native SQL (JPA deleteAll doesn't flush immediately)
            // Note: schedule_conflict only has schedule_id (the schedule with the conflict), not a conflicting_schedule_id column
            List<ScheduleConflict> conflicts = scheduleConflictRepository.findByScheduleId(id);
            log.info("Found {} schedule conflicts to delete for scheduleId={}", conflicts.size(), id);
            int conflictsDeleted = jdbcTemplate.update("DELETE FROM schedule_conflict WHERE schedule_id = ?", id);
            log.info("Deleted {} schedule conflicts for scheduleId={}", conflictsDeleted, id);

            // Log audit
            log.info("About to log audit for scheduleId={}", id);
            auditHistoryService.logAction("schedule", id, AuditHistory.ActionType.DELETE, schedule, null, authContextService.getCurrentStaff().getId());
            log.info("Audit logged successfully for scheduleId={}", id);

            // Create notification
            log.info("About to create notification for scheduleId={}", id);
            notificationService.createNotification(schedule.getStaff().getId(),
                    new NotificationDTO("Xóa lịch trực",
                            "Lịch trực ngày " + schedule.getWorkDate() + " đã bị xóa."));
            log.info("Notification created successfully for scheduleId={}", id);

            // Delete the schedule using native SQL to avoid JPA persistence context issues
            log.info("Deleting scheduleId={} using native SQL", id);
            int rowsAffected = jdbcTemplate.update("DELETE FROM schedule WHERE id = ?", id);
            log.info("Native DELETE affected {} rows for scheduleId={}", rowsAffected, id);
            
            if (rowsAffected == 0) {
                log.warn("No rows deleted for scheduleId={} - schedule may not exist", id);
            }
            log.info("Successfully deleted scheduleId={}", id);
        } catch (Exception e) {
            log.error("Error deleting scheduleId={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }
}
