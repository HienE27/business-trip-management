"use client";

import { useNotificationStream } from '@/hooks/useNotificationStream';
import { useNotifications } from '@/components/ui/NotificationContext';
import { useAuth } from '@/components/auth/AuthProvider';
import { useToast } from '@/components/ui/ToastProvider';
import type { NotificationEvent } from '@/lib/realtime/notificationClient';

/**
 * Client-side bridge that:
 *  1. Opens a WebSocket subscription to /topic/notifications.
 *  2. Refreshes the notification count in the header bell.
 *  3. Shows toast notifications for new notifications.
 *
 * Mounted as a sibling of the actual app content so the
 * subscription stays alive across page navigations.
 */
export function NotificationStreamBridge() {
  const { user } = useAuth();
  const userId = user?.userId;
  const { refreshCount } = useNotifications();
  const { info } = useToast();

  // Track processed notification IDs to avoid duplicate toasts on reconnect
  const processedIdsRef = { current: new Set<number>() };

  const handleEvent = (event: NotificationEvent) => {
    const notificationId = event.notificationId;

    if (event.eventType === 'NEW_NOTIFICATION') {
      // Avoid duplicate processing on reconnect
      if (notificationId && processedIdsRef.current.has(notificationId)) {
        return;
      }
      if (notificationId) {
        processedIdsRef.current.add(notificationId);
        if (processedIdsRef.current.size > 100) {
          const entries = Array.from(processedIdsRef.current);
          processedIdsRef.current = new Set(entries.slice(-50));
        }
      }

      // Refresh the notification list and count
      void refreshCount();

      // Show toast with notification details
      const title = event.title ?? 'Thông báo mới';
      const message = event.message ?? '';
      if (message) {
        info(`${title}: ${message}`);
      } else {
        info(title);
      }
    } else if (
      event.eventType === 'NOTIFICATION_READ' ||
      event.eventType === 'ALL_NOTIFICATIONS_READ' ||
      event.eventType === 'NOTIFICATION_DELETED'
    ) {
      // Refresh the count when notifications are read/deleted
      void refreshCount();
    }
  };

  useNotificationStream({
    enabled: !!userId,
    staffId: userId,
    onEvent: handleEvent,
  });

  return null;
}
