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
    public void sendSchedulePublishedEmail(List<Staff> recipients, String periodName,
                                          java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        String subject = "[Lịch công tác] Kỳ lịch mới đã được công bố";
        StringBuilder body = new StringBuilder();
        body.append("Kính gửi quý nhân sự,\n\n");
        body.append("Kỳ lịch công tác mới đã được công bố.\n\n");
        body.append("=== THÔNG TIN KỲ LỊCH ===\n");
        body.append("Tên kỳ: ").append(periodName).append("\n");
        body.append("Thời gian: ").append(startDate.format(DATE_FORMATTER))
                .append(" - ").append(endDate.format(DATE_FORMATTER)).append("\n\n");
        body.append("Vui lòng đăng nhập hệ thống để kiểm tra lịch trực cá nhân của bạn.\n\n");
        body.append("Trân trọng,\n");
        body.append("Hệ thống Quản lý Lịch Công Tác\n");
        for (Staff staff : recipients) {
            if (staff.getEmail() != null && !staff.getEmail().isBlank()) {
                sendEmail(staff.getEmail(), subject, body.toString());
            }
        }
    }

    @Async
    public void sendSwapApprovedEmail(Staff staff, String requesterDate,
                                      String targetDate, String shiftType) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            return;
        }
        String subject = "[Đổi trực] Yêu cầu đổi ca đã được duyệt";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Yêu cầu đổi trực của bạn đã được duyệt.\n\n" +
                "Ca trực của bạn: %s (Loại: %s)\n" +
                "Ngày trực mới của bạn: %s\n\n" +
                "Vui lòng kiểm tra lịch làm việc mới trên hệ thống.\n\n" +
                "Trân trọng,\n" +
                "Hệ thống Quản lý Lịch Công Tác\n",
                staff.getFullName(), targetDate, shiftType, requesterDate);
        sendEmail(staff.getEmail(), subject, body);
    }

    @Async
    public void sendSwapRejectedEmail(Staff staff, String requesterDate,
                                      String targetDate, String reason) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            return;
        }
        String subject = "[Đổi trực] Yêu cầu đổi ca đã bị từ chối";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Yêu cầu đổi trực (%s <-> %s) đã bị từ chối.\n%s\n\n" +
                "Nếu cần thiết, bạn có thể gửi yêu cầu đổi ca mới.\n\n" +
                "Trân trọng,\n" +
                "Hệ thống Quản lý Lịch Công Tác\n",
                staff.getFullName(), requesterDate, targetDate,
                reason != null && !reason.isBlank() ? "Lý do: " + reason : "");
        sendEmail(staff.getEmail(), subject, body);
    }

    @Async
    public void sendLeaveApprovedEmail(Staff staff, java.time.LocalDate startDate,
                                      java.time.LocalDate endDate) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            return;
        }
        String subject = "[Nghỉ phép] Yêu cầu nghỉ phép đã được duyệt";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Yêu cầu nghỉ phép từ %s đến %s đã được duyệt.\n\n" +
                "Vui lòng kiểm tra lịch làm việc của bạn trên hệ thống.\n\n" +
                "Trân trọng,\n" +
                "Hệ thống Quản lý Lịch Công Tác\n",
                staff.getFullName(), startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));
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
