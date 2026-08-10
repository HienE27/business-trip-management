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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
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
        // BUGFIX (was RBAC#1): defensive null check — even with the @NotNull
        // guard on NotificationDTO.recipientId, programmatic callers (e.g.
        // internal services, schedulers, tests) could still pass null and
        // trigger JPA's "The given id must not be null" IllegalArgumentException
        // deep in the stack → HTTP 500. Throwing BadRequestException here
        // returns a clean 400 with a Vietnamese message.
        if (staffId == null) {
            throw new com.hospital.scheduler.exception.BadRequestException(
                    "ID nhân sự nhận không được để trống");
        }
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

    /**
     * Bulk delete by id list. Returns the number of rows actually removed.
     * Silently skips unknown ids so it stays idempotent for the UI "Xóa nhiều"
     * flow where the user might have selected rows that have already been
     * deleted in another tab.
     *
     * <p>Owns the ownership check: each notification must belong to the
     * caller (or the caller must have {@code NOTIFICATION_VIEW}). Otherwise
     * we throw {@link ResourceNotFoundException} so the bulk delete never
     * leaks cross-staff deletions.
     */
    public int deleteNotificationsByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        Integer callerStaffId = authContextService.getCurrentStaff().getId();
        boolean isAdmin = authContextService.hasAuthority(com.hospital.scheduler.security.Permissions.NOTIFICATION_VIEW);

        // Look up owning staff in one query so we don't N+1 the audit log.
        List<Notification> existing = notificationRepository.findAllById(ids);
        for (Notification n : existing) {
            if (!isAdmin && !n.getStaff().getId().equals(callerStaffId)) {
                throw new ResourceNotFoundException("Không tìm thấy thông báo");
            }
        }

        int deleted = notificationRepository.deleteByIdInBatch(ids);
        auditHistoryService.logAction("notification", 0, AuditHistory.ActionType.DELETE, null, null, null);

        // Broadcast so any open subscription for these staff rows updates.
        for (Notification n : existing) {
            notificationBroadcastService.broadcastNotificationDeleted(n.getId(), n.getStaff().getId());
        }
        return deleted;
    }

    /**
     * Delete every notification whose {@code createdAt} falls inside
     * {@code [start, end)}. Admins wipe globally; everyone else wipes only
     * their own rows. The {@code staffId} parameter is intentional —
     * non-admin callers must pass their own id here, so we never expose a
     * cross-staff delete through this path.
     */
    public int deleteNotificationsByDateRange(LocalDateTime start, LocalDateTime end, Integer staffId) {
        boolean isAdmin = authContextService.hasAuthority(com.hospital.scheduler.security.Permissions.NOTIFICATION_VIEW);

        List<Notification> toDelete;
        if (isAdmin) {
            toDelete = notificationRepository.findAll();
        } else {
            toDelete = notificationRepository.findByStaffIdOrderByCreatedAtDesc(staffId);
        }
        List<Notification> inRange = toDelete.stream()
                .filter(n -> !n.getCreatedAt().isBefore(start) && n.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
        if (inRange.isEmpty()) return 0;
        List<Integer> ids = inRange.stream().map(Notification::getId).collect(Collectors.toList());
        int deleted = notificationRepository.deleteByIdInBatch(ids);
        auditHistoryService.logAction("notification", 0, AuditHistory.ActionType.DELETE, null, null, null);
        for (Notification n : inRange) {
            notificationBroadcastService.broadcastNotificationDeleted(n.getId(), staffId);
        }
        return deleted;
    }

    /**
     * Delete every notification belonging to the caller. Used by the
     * "Xóa tất cả" action on /notifications. Admins with NOTIFICATION_VIEW
     * wipe their own row set (not the whole table) — the broadcast channel
     * would otherwise trigger a stampede on every connected user.
     */
    public int deleteAllNotificationsForCaller() {
        Integer callerStaffId = authContextService.getCurrentStaff().getId();
        int deleted = notificationRepository.deleteAllByStaffId(callerStaffId);
        auditHistoryService.logAction("notification", 0, AuditHistory.ActionType.DELETE, null, null, null);
        return deleted;
    }

    /**
     * Batch create notifications for multiple staff members in a single DB operation.
     * This is an optimization for auto-scheduling apply which may need to notify
     * 250+ staff members — previously this caused ~250 individual INSERT queries.
     *
     * BUGFIX (apply-preview-slow): notifications were created one-by-one causing
     * ~15s overhead on large schedules. Batch insert reduces this to ~1-2s.
     *
     * Error handling: individual notification failures are logged but don't fail
     * the entire batch. Notifications are "nice-to-have" — schedule persistence
     * should not be blocked by notification failures.
     *
     * @param notifications List of (staffId, dto) pairs to create
     * @return count of successfully created notifications
     */
    @Transactional(noRollbackFor = Exception.class)
    public int createNotificationsBatch(List<NotificationBatchItem> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return 0;
        }
        
        log.info("Batch creating {} notifications", notifications.size());
        List<Notification> saved = new ArrayList<>();
        
        try {
            // Load all staff entities at once (single query)
            Map<Integer, Staff> staffMap = notifications.stream()
                    .map(item -> item.staffId)
                    .distinct()
                    .collect(Collectors.toMap(
                            sid -> sid,
                            sid -> staffRepository.findById(sid).orElse(null),
                            (a, b) -> a
                    ));
            
            // Build notification entities
            List<Notification> entities = new ArrayList<>();
            for (NotificationBatchItem item : notifications) {
                Staff staff = staffMap.get(item.staffId);
                if (staff == null) {
                    log.warn("Staff {} not found for batch notification, skipping", item.staffId);
                    continue;
                }
                entities.add(Notification.builder()
                        .staff(staff)
                        .title(item.dto.getTitle())
                        .message(item.dto.getMessage())
                        .isRead(false)
                        .build());
            }
            
            // Batch save (single INSERT with multiple VALUES)
            saved = notificationRepository.saveAll(entities);
            log.info("Batch saved {} notifications", saved.size());
            
        } catch (Exception e) {
            log.error("Batch notification creation failed: {} — continuing without notifications", e.getMessage(), e);
        }
        
        // Broadcast via WebSocket (still need individual broadcasts for real-time updates)
        // This is acceptable since WebSocket is fast compared to DB writes
        for (Notification notification : saved) {
            try {
                notificationBroadcastService.broadcastNewNotification(notification, notification.getStaff().getId());
            } catch (Exception e) {
                log.warn("Failed to broadcast notification {}: {}", notification.getId(), e.getMessage());
            }
        }
        
        return saved.size();
    }

    /**
     * Data class for batch notification creation.
     */
    public record NotificationBatchItem(Integer staffId, NotificationDTO dto) {}
}
