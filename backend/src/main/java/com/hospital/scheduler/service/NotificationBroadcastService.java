package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Broadcasts notification events via WebSocket STOMP to /topic/notifications.
 * 
 * Follows the same pattern as ConflictBroadcastService for consistency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBroadcastService {

    private static final String NOTIFICATION_TOPIC = "/topic/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast a new notification to:
     * 1. Global topic /topic/notifications (for admins/managers monitoring all)
     * 2. Staff-specific topic /topic/notifications/{staffId}
     */
    public void broadcastNewNotification(Notification notification, Integer staffId) {
        Map<String, Object> payload = buildNewNotificationPayload(notification, staffId);
        doSend(payload);
        
        // Also send to staff-specific topic
        doSendToStaff(notification, staffId);
        
        log.info("Broadcasted notification id={} to /topic/notifications and /topic/notifications/{}", 
                notification.getId(), staffId);
    }

    /**
     * Broadcast a batch of new notifications (e.g., from createNotificationForAllStaff).
     */
    public void broadcastBulkNotifications(Iterable<Notification> notifications, Integer staffId) {
        for (Notification notification : notifications) {
            broadcastNewNotification(notification, staffId);
        }
    }

    /**
     * Broadcast when a notification is marked as read.
     */
    public void broadcastNotificationRead(Integer notificationId, Integer staffId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "NOTIFICATION_READ");
        payload.put("notificationId", notificationId);
        payload.put("staffId", staffId);
        payload.put("timestamp", LocalDateTime.now().toString());
        
        doSendToStaff(staffId, payload);
        log.info("Broadcasted notification read: notificationId={}, staffId={}", notificationId, staffId);
    }

    /**
     * Broadcast when all notifications are marked as read for a staff.
     */
    public void broadcastBulkRead(Integer staffId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "ALL_NOTIFICATIONS_READ");
        payload.put("staffId", staffId);
        payload.put("timestamp", LocalDateTime.now().toString());
        
        doSendToStaff(staffId, payload);
        log.info("Broadcasted all notifications read for staffId={}", staffId);
    }

    /**
     * Broadcast when a notification is deleted.
     */
    public void broadcastNotificationDeleted(Integer notificationId, Integer staffId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "NOTIFICATION_DELETED");
        payload.put("notificationId", notificationId);
        payload.put("staffId", staffId);
        payload.put("timestamp", LocalDateTime.now().toString());
        
        doSendToStaff(staffId, payload);
        log.info("Broadcasted notification deleted: notificationId={}, staffId={}", notificationId, staffId);
    }

    private Map<String, Object> buildNewNotificationPayload(Notification notification, Integer staffId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "NEW_NOTIFICATION");
        payload.put("notificationId", notification.getId());
        payload.put("staffId", staffId);
        payload.put("title", notification.getTitle());
        payload.put("message", notification.getMessage());
        payload.put("isRead", notification.getIsRead());
        payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null);
        payload.put("timestamp", LocalDateTime.now().toString());
        return payload;
    }

    private void doSend(Map<String, Object> payload) {
        messagingTemplate.convertAndSend(NOTIFICATION_TOPIC, payload, new HashMap<>());
    }

    private void doSendToStaff(Notification notification, Integer staffId) {
        Map<String, Object> payload = buildNewNotificationPayload(notification, staffId);
        doSendToStaff(staffId, payload);
    }

    private void doSendToStaff(Integer staffId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend(NOTIFICATION_TOPIC + "/" + staffId, payload, new HashMap<>());
    }
}
