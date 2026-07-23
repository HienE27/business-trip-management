package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppConfigService appConfigService;
    private final TemplateEngine templateEngine;
    private final Environment environment;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Async
    public void sendSchedulePublishedEmail(List<Staff> recipients, String periodName,
                                          LocalDate startDate, LocalDate endDate,
                                          List<Schedule> periodSchedules, List<CompensationDay> periodCompDays) {
        if (!appConfigService.isEmailEnabled()) return;
        if (recipients == null || recipients.isEmpty()) return;

        Map<Integer, List<Schedule>> schedulesByStaff = periodSchedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));
        Map<Integer, List<CompensationDay>> compDaysByStaff = periodCompDays.stream()
                .collect(Collectors.groupingBy(cd -> cd.getStaff().getId()));

        for (Staff staff : recipients) {
            if (staff.getEmail() == null || staff.getEmail().isBlank()) continue;

            List<Schedule> staffSchedules = schedulesByStaff.getOrDefault(staff.getId(), List.of());
            List<CompensationDay> staffCompDays = compDaysByStaff.getOrDefault(staff.getId(), List.of());

            Context ctx = new Context();
            ctx.setVariable("staffName", staff.getFullName());
            ctx.setVariable("periodName", periodName);
            ctx.setVariable("periodRange", startDate.format(DATE_FMT) + " - " + endDate.format(DATE_FMT));
            ctx.setVariable("publishedAt", LocalDate.now().format(DATE_FMT));

            List<Map<String, String>> scheduleItems = staffSchedules.stream()
                    .map(s -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("date", s.getWorkDate().format(DATE_FMT));
                        m.put("shiftType", s.getShiftType().getName());
                        return m;
                    })
                    .toList();
            ctx.setVariable("schedules", scheduleItems);

            List<String> compDates = staffCompDays.stream()
                    .map(cd -> cd.getCompensationDate().format(DATE_FMT))
                    .toList();
            ctx.setVariable("compDays", compDates);

            String subject = "[Lịch công tác] Kỳ lịch mới đã được công bố";
            String htmlBody = templateEngine.process("email/schedule-published", ctx);

            sendHtmlEmail(staff.getEmail(), subject, htmlBody);
        }
    }

    @Async
    public void sendSwapApprovedEmail(Staff staff, String ownNewDate, String shiftType) {
        if (!appConfigService.isEmailEnabled()) return;
        if (staff.getEmail() == null || staff.getEmail().isBlank()) return;

        Context ctx = new Context();
        ctx.setVariable("staffName", staff.getFullName());
        ctx.setVariable("newDate", ownNewDate);
        ctx.setVariable("shiftType", shiftType);

        String subject = "[Đổi trực] Yêu cầu đổi ca đã được duyệt";
        String htmlBody = templateEngine.process("email/swap-approved", ctx);
        sendHtmlEmail(staff.getEmail(), subject, htmlBody);
    }

    @Async
    public void sendSwapRejectedEmail(Staff staff, String requesterDate, String targetDate, String reason) {
        if (!appConfigService.isEmailEnabled()) return;
        if (staff.getEmail() == null || staff.getEmail().isBlank()) return;

        Context ctx = new Context();
        ctx.setVariable("staffName", staff.getFullName());
        ctx.setVariable("fromDate", requesterDate);
        ctx.setVariable("toDate", targetDate);
        ctx.setVariable("reason", reason != null ? reason : "");

        String subject = "[Đổi trực] Yêu cầu đổi ca đã bị từ chối";
        String htmlBody = templateEngine.process("email/swap-rejected", ctx);
        sendHtmlEmail(staff.getEmail(), subject, htmlBody);
    }

    @Async
    public void sendLeaveApprovedEmail(Staff staff, LocalDate startDate, LocalDate endDate) {
        if (!appConfigService.isEmailEnabled()) return;
        if (staff.getEmail() == null || staff.getEmail().isBlank()) return;

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        Context ctx = new Context();
        ctx.setVariable("staffName", staff.getFullName());
        ctx.setVariable("startDate", startDate.format(DATE_FMT));
        ctx.setVariable("endDate", endDate.format(DATE_FMT));
        ctx.setVariable("days", days + " ngày");

        String subject = "[Nghỉ phép] Yêu cầu nghỉ phép đã được duyệt";
        String htmlBody = templateEngine.process("email/leave-approved", ctx);
        sendHtmlEmail(staff.getEmail(), subject, htmlBody);
    }

    @Async
    public void sendLeaveRejectedEmail(Staff staff, LocalDate startDate, LocalDate endDate,
                                         String reviewerName, String reviewNote) {
        if (!appConfigService.isEmailEnabled()) return;
        if (staff.getEmail() == null || staff.getEmail().isBlank()) return;

        Context ctx = new Context();
        ctx.setVariable("staffName", staff.getFullName());
        ctx.setVariable("startDate", startDate.format(DATE_FMT));
        ctx.setVariable("endDate", endDate.format(DATE_FMT));
        ctx.setVariable("reviewerName", reviewerName != null ? reviewerName : "Quản lý");
        ctx.setVariable("reviewNote", reviewNote != null ? reviewNote : "");

        String subject = "[Nghỉ phép] Yêu cầu nghỉ phép đã bị từ chối";
        String htmlBody = templateEngine.process("email/leave-rejected", ctx);
        sendHtmlEmail(staff.getEmail(), subject, htmlBody);
    }

    @Async
    public void sendLeaveCancelledEmail(Staff staff, LocalDate startDate, LocalDate endDate) {
        if (!appConfigService.isEmailEnabled()) return;
        if (staff.getEmail() == null || staff.getEmail().isBlank()) return;

        Context ctx = new Context();
        ctx.setVariable("staffName", staff.getFullName());
        ctx.setVariable("startDate", startDate.format(DATE_FMT));
        ctx.setVariable("endDate", endDate.format(DATE_FMT));

        String subject = "[Nghỉ phép] Yêu cầu nghỉ phép đã bị hủy";
        String htmlBody = templateEngine.process("email/leave-rejected", ctx);
        sendHtmlEmail(staff.getEmail(), subject, htmlBody);
    }

    @Async
    public void sendConflictAlertToStaff(Staff staff, Schedule schedule, String conflictDescription) {
        if (!appConfigService.isEmailEnabled() || !appConfigService.isConflictEmailEnabled()) return;
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            log.warn("Cannot send conflict email: staff {} has no email", staff.getId());
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("staffName", staff.getFullName());
        ctx.setVariable("workDate", schedule.getWorkDate().format(DATE_FMT));
        ctx.setVariable("shiftTypeName", schedule.getShiftType().getName());
        ctx.setVariable("shiftTypeId", schedule.getShiftType().getId());
        ctx.setVariable("periodName", schedule.getPeriod().getPeriodName());
        ctx.setVariable("conflictDescription", conflictDescription);

        String subject = String.format("[%s] Cảnh báo xung đột lịch ngày %s",
                schedule.getShiftType().getName(),
                schedule.getWorkDate().format(DATE_FMT));
        String htmlBody = templateEngine.process("email/conflict-alert", ctx);
        sendHtmlEmail(staff.getEmail(), subject, htmlBody);
    }

    // ── HTML email ──────────────────────────────────────────────────────────

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("HTML email sent from {} to {}: {}", resolveFromAddress(), to, subject);
        } catch (MailException e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage());
        }
    }

    private String resolveFromAddress() {
        String fromEnv = environment.getProperty("app.email.from");
        return (fromEnv != null && !fromEnv.isBlank()) ? fromEnv : "noreply@hospital-scheduler.com";
    }
}
