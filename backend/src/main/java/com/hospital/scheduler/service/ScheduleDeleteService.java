package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleDeleteService {

    private final ScheduleRepository scheduleRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final AuditHistoryService auditHistoryService;
    private final NotificationService notificationService;
    private final AuthContextService authContextService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void deleteSchedule(Integer id) {
        // Find the schedule
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + id));

        // Check period status
        SchedulePeriod period = schedule.getPeriod();
        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xóa lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        // If L01, delete related compensation days first
        if ("L01".equals(schedule.getShiftType().getId())) {
            List<CompensationDay> compDays = compensationDayRepository.findByScheduleId(id);
            for (CompensationDay cd : compDays) {
                auditHistoryService.logAction("compensation_day", cd.getId(), AuditHistory.ActionType.DELETE, cd, null, authContextService.getCurrentStaff().getId());
            }
            compensationDayRepository.deleteAll(compDays);
        }

        // Delete ScheduleConflict records
        List<ScheduleConflict> conflicts = scheduleConflictRepository.findByScheduleId(id);
        for (ScheduleConflict sc : conflicts) {
            scheduleConflictRepository.delete(sc);
        }

        // Log audit
        auditHistoryService.logAction("schedule", id, AuditHistory.ActionType.DELETE, schedule, null, authContextService.getCurrentStaff().getId());

        // Create notification
        notificationService.createNotification(schedule.getStaff().getId(),
                new NotificationDTO("Xóa lịch trực",
                        "Lịch trực ngày " + schedule.getWorkDate() + " đã bị xóa."));

        // Use JdbcTemplate for direct SQL delete to bypass any caching issues
        jdbcTemplate.update("DELETE FROM schedule WHERE id = ?", id);
    }
}
