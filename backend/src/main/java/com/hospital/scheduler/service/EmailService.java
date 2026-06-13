package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppConfigService appConfigService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Async
    public void sendConflictAlert(Schedule schedule, String conflictDescription) {
        if (!appConfigService.isEmailEnabled() || !appConfigService.isConflictEmailEnabled()) {
            log.debug("Email notification disabled, skipping conflict alert");
            return;
        }

        if (!appConfigService.isEmailEnabled() || !appConfigService.isConflictEmailEnabled()) {
            log.debug("Email notification disabled, skipping conflict alert");
            return;
        }

        Staff staff = schedule.getStaff();
        if (staff == null || staff.getEmail() == null || staff.getEmail().isBlank()) {
            log.warn("Cannot send conflict email: staff {} has no email", staff != null ? staff.getId() : "null");
            return;
        }

        String subject = String.format("[%s] Cảnh báo xung đột lịch ngày %s",
                schedule.getShiftType().getName(),
                schedule.getWorkDate().format(DATE_FORMATTER));

        String body = buildConflictEmailBody(schedule, conflictDescription);

        sendEmail(staff.getEmail(), subject, body);
    }

    @Async
    public void sendConflictAlertToStaff(Staff staff, Schedule schedule, String conflictDescription) {
        if (!appConfigService.isEmailEnabled() || !appConfigService.isConflictEmailEnabled()) {
            return;
        }

        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            log.warn("Cannot send conflict email: staff {} has no email", staff.getId());
            return;
        }

        String subject = String.format("[%s] Cảnh báo xung đột lịch ngày %s",
                schedule.getShiftType().getName(),
                schedule.getWorkDate().format(DATE_FORMATTER));

        String body = buildConflictEmailBody(schedule, conflictDescription);

        sendEmail(staff.getEmail(), subject, body);
    }

    private String buildConflictEmailBody(Schedule schedule, String conflictDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("Kính gửi ").append(schedule.getStaff().getFullName()).append(",\n\n");
        sb.append("Hệ thống Quản lý Lịch Công Tác phát hiện XUNG ĐỘT lịch trực liên quan đến bạn.\n\n");
        sb.append("=== THÔNG TIN XUNG ĐỘT ===\n");
        sb.append("Ngày làm việc: ").append(schedule.getWorkDate().format(DATE_FORMATTER)).append("\n");
        sb.append("Loại lịch: ").append(schedule.getShiftType().getName()).append("\n");
        sb.append("Mã ca: ").append(schedule.getShiftType().getId()).append("\n");
        sb.append("Kỳ lịch: ").append(schedule.getPeriod().getPeriodName()).append("\n\n");
        sb.append("=== MÔ TẢ XUNG ĐỘT ===\n");
        sb.append(conflictDescription).append("\n\n");
        sb.append("Vui lòng kiểm tra lịch làm việc của bạn và liên hệ với quản lý để được giải quyết.\n\n");
        sb.append("Trân trọng,\n");
        sb.append("Hệ thống Quản lý Lịch Công Tác - Bệnh viện\n");
        return sb.toString();
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@hospital-scheduler.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Conflict alert email sent to {}: {}", to, subject);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
