package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppConfigService appConfigService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Async
    public void sendSchedulePublishedEmail(List<Staff> recipients, String periodName,
                                          java.time.LocalDate startDate, java.time.LocalDate endDate,
                                          List<Schedule> periodSchedules, List<CompensationDay> periodCompDays) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        String subject = "[Lịch công tác] Kỳ lịch mới đã được công bố";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        Map<Integer, List<Schedule>> schedulesByStaff = periodSchedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));
        Map<Integer, List<CompensationDay>> compDaysByStaff = periodCompDays.stream()
                .collect(Collectors.groupingBy(cd -> cd.getStaff().getId()));

        for (Staff staff : recipients) {
            if (staff.getEmail() == null || staff.getEmail().isBlank()) continue;

            List<Schedule> staffSchedules = schedulesByStaff.getOrDefault(staff.getId(), List.of());
            List<CompensationDay> staffCompDays = compDaysByStaff.getOrDefault(staff.getId(), List.of());

            String dutyList = staffSchedules.stream()
                    .map(s -> s.getWorkDate().format(fmt) + " - " + s.getShiftType().getName())
                    .collect(Collectors.joining("\n  - "));
            String compList = staffCompDays.stream()
                    .map(cd -> cd.getCompensationDate().format(fmt))
                    .collect(Collectors.joining(", "));

            StringBuilder body = new StringBuilder();
            body.append("Kính gửi ").append(staff.getFullName()).append(",\n\n");
            body.append("Kỳ lịch công tác mới đã được công bố.\n\n");
            body.append("=== THÔNG TIN KỲ LỊCH ===\n");
            body.append("Tên kỳ: ").append(periodName).append("\n");
            body.append("Thời gian: ").append(startDate.format(fmt))
                    .append(" - ").append(endDate.format(fmt)).append("\n\n");
            body.append("=== LỊCH TRỰC CỦA BẠN ===\n");
            if (dutyList.isEmpty()) {
                body.append("  Không có ca trực nào được phân công.\n");
            } else {
                body.append("  - ").append(dutyList).append("\n");
            }
            body.append("\n=== NGÀY NGHỈ BÙ ===\n");
            if (compList.isEmpty()) {
                body.append("  Không có ngày nghỉ bù.\n");
            } else {
                body.append("  ").append(compList).append("\n");
            }
            body.append("\nTrân trọng,\n");
            body.append("Hệ thống Quản lý Lịch Công Tác\n");

            sendEmail(staff.getEmail(), subject, body.toString());
        }
    }

    @Async
    public void sendSwapApprovedEmail(Staff staff, String ownNewDate,
                                      String shiftType) {
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
                "Ngày trực mới: %s\n" +
                "Loại ca: %s\n\n" +
                "Vui lòng kiểm tra lịch làm việc mới trên hệ thống.\n\n" +
                "Trân trọng,\n" +
                "Hệ thống Quản lý Lịch Công Tác\n",
                staff.getFullName(), ownNewDate, shiftType);
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
    public void sendLeaveRejectedEmail(Staff staff, java.time.LocalDate startDate,
                                      java.time.LocalDate endDate, String reviewerName, String reviewNote) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            return;
        }
        String subject = "[Nghỉ phép] Yêu cầu nghỉ phép đã bị từ chối";
        String note = reviewNote != null && !reviewNote.isBlank() ? "\nLý do: " + reviewNote + "\n" : "\n";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Yêu cầu nghỉ phép từ %s đến %s đã bị từ chối bởi %s.%s" +
                "Nếu cần thiết, bạn có thể gửi yêu cầu nghỉ phép mới.\n\n" +
                "Trân trọng,\n" +
                "Hệ thống Quản lý Lịch Công Tác\n",
                staff.getFullName(), startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER),
                reviewerName, note);
        sendEmail(staff.getEmail(), subject, body);
    }

    @Async
    public void sendLeaveCancelledEmail(Staff staff, java.time.LocalDate startDate,
                                       java.time.LocalDate endDate) {
        if (!appConfigService.isEmailEnabled()) {
            return;
        }
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            return;
        }
        String subject = "[Nghỉ phép] Yêu cầu nghỉ phép đã bị hủy";
        String body = String.format(
                "Kính gửi %s,\n\n" +
                "Yêu cầu nghỉ phép từ %s đến %s đã bị hủy.\n\n" +
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
