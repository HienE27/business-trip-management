package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.NotificationResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Notification;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.NotificationRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;

    public List<NotificationResponse> getNotificationsByStaff(Integer staffId) {
        return notificationRepository.findByStaffIdOrderByCreatedAtDesc(staffId).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<NotificationResponse> getNotificationsByStaffPaginated(Integer staffId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByStaffId(staffId, pageable)
                .map(NotificationResponse::fromEntity);
    }

    public List<NotificationResponse> getUnreadNotifications(Integer staffId) {
        return notificationRepository.findUnreadByStaffId(staffId).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public long countUnreadNotifications(Integer staffId) {
        return notificationRepository.countUnreadByStaffId(staffId);
    }

    public NotificationResponse markAsRead(Integer notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo với ID: " + notificationId));

        Notification prev = notification;
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        Integer currentId = null;
        try { currentId = authContextService.getCurrentStaff().getId(); } catch (Exception ignored) {}
        auditHistoryService.logAction("notification", notificationId, AuditHistory.ActionType.UPDATE, prev, saved, currentId);
        return NotificationResponse.fromEntity(saved);
    }

    public void markAllAsRead(Integer staffId) {
        List<Notification> unreadNotifications = notificationRepository.findUnreadByStaffId(staffId);
        for (Notification notification : unreadNotifications) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
        }
        notificationRepository.saveAll(unreadNotifications);
        Integer currentId = null;
        try { currentId = authContextService.getCurrentStaff().getId(); } catch (Exception ignored) {}
        auditHistoryService.logAction("notification", null, AuditHistory.ActionType.UPDATE,
                null, Map.of("markAllRead", true, "staffId", staffId, "count", unreadNotifications.size()), currentId);
    }

    public NotificationResponse createNotification(Integer staffId, NotificationDTO dto) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + staffId));

        Notification notification = Notification.builder()
                .staff(staff)
                .title(dto.getTitle())
                .message(dto.getMessage())
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        auditHistoryService.logAction("notification", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
        return NotificationResponse.fromEntity(saved);
    }

    public void createNotificationForAllStaff(String title, String message) {
        List<Staff> allStaff = staffRepository.findAll();
        List<Notification> notifications = allStaff.stream()
                .map(staff -> Notification.builder()
                        .staff(staff)
                        .title(title)
                        .message(message)
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());
        notificationRepository.saveAll(notifications);
        auditHistoryService.logAction("notification", null, AuditHistory.ActionType.INSERT,
                null, Map.of("broadcast", true, "title", title, "message", message, "recipientCount", allStaff.size()), null);
    }

    public void deleteNotification(Integer notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo với ID: " + notificationId);
        }
        notificationRepository.deleteById(notificationId);
        auditHistoryService.logAction("notification", notificationId, AuditHistory.ActionType.DELETE, null, null, null);
    }
}
