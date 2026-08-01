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
    private final NotificationBroadcastService notificationBroadcastService;

    public List<NotificationResponse> getNotificationsByStaff(Integer staffId) {
        return notificationRepository.findByStaffIdOrderByCreatedAtDesc(staffId).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getNotificationsByStaff(Integer staffId, Integer page, Integer size) {
        Pageable pageable = (page != null && size != null)
                ? PageRequest.of(page - 1, size) // page is 1-based for API
                : Pageable.unpaged();

        if (pageable.isUnpaged()) {
            return notificationRepository.findByStaffIdOrderByCreatedAtDesc(staffId).stream()
                    .map(NotificationResponse::fromEntity)
                    .collect(Collectors.toList());
        }

        return notificationRepository.findByStaffId(staffId, pageable).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public long countNotificationsByStaff(Integer staffId) {
        return notificationRepository.countByStaffId(staffId);
    }

    public Page<NotificationResponse> getNotificationsByStaffPaginated(Integer staffId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByStaffId(staffId, pageable)
                .map(NotificationResponse::fromEntity);
    }

    /**
     * Paginated query with optional tab filter (all|unread|conflict|exchange|published|system).
     * BUGFIX #6: previously the frontend fetched ONE page and filtered client-side,
     * losing matches on other pages. Filtering now happens server-side in SQL.
     */
    public Page<NotificationResponse> getNotificationsByStaffPaginated(
            Integer staffId, int page, int size, String tab) {
        Pageable pageable = PageRequest.of(page, size);
        if (tab == null || tab.isBlank() || "all".equals(tab)) {
            return notificationRepository.findByStaffId(staffId, pageable)
                    .map(NotificationResponse::fromEntity);
        }
        return notificationRepository.findPageWithFilters(staffId, tab, pageable)
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

    /**
     * Unread count for the currently authenticated staff. Resolves the staff
     * id from the security context so the frontend does not need to know it.
     */
    public long getMyUnreadCount() {
        Integer staffId = authContextService.getCurrentStaff().getId();
        return notificationRepository.countUnreadByStaffId(staffId);
    }

    public Notification findById(Integer notificationId) {
        return notificationRepository.findById(notificationId).orElse(null);
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
        
        // Broadcast via WebSocket
        Integer staffId = saved.getStaff().getId();
        notificationBroadcastService.broadcastNotificationRead(notificationId, staffId);
        
        return NotificationResponse.fromEntity(saved);
    }

    public void markAllAsRead(Integer staffId) {
        int count = notificationRepository.markAllAsReadBulk(staffId);
        Integer currentId = null;
        try { currentId = authContextService.getCurrentStaff().getId(); } catch (Exception ignored) {}
        auditHistoryService.logAction("notification", null, AuditHistory.ActionType.UPDATE,
                null, Map.of("markAllRead", true, "staffId", staffId, "count", count), currentId);
        
        // Broadcast via WebSocket
        notificationBroadcastService.broadcastBulkRead(staffId);
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
        
        // Broadcast via WebSocket for real-time push
        notificationBroadcastService.broadcastNewNotification(saved, staffId);
        
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
        
        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);
        
        auditHistoryService.logAction("notification", null, AuditHistory.ActionType.INSERT,
                null, Map.of("broadcast", true, "title", title, "message", message, "recipientCount", allStaff.size()), null);
        
        // Broadcast each notification via WebSocket
        for (Notification notification : savedNotifications) {
            notificationBroadcastService.broadcastNewNotification(notification, notification.getStaff().getId());
        }
    }

    public void deleteNotification(Integer notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo với ID: " + notificationId);
        }
        
        // Get staff ID before deleting for broadcast
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        Integer staffId = notification != null ? notification.getStaff().getId() : null;
        
        notificationRepository.deleteById(notificationId);
        auditHistoryService.logAction("notification", notificationId, AuditHistory.ActionType.DELETE, null, null, null);
        
        // Broadcast via WebSocket
        if (staffId != null) {
            notificationBroadcastService.broadcastNotificationDeleted(notificationId, staffId);
        }
    }
}
