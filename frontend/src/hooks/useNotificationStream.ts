"use client";

import { useEffect, useRef } from 'react';
import {
  createNotificationClient,
  resolveNotificationWsUrl,
  type NotificationClient,
  type NotificationEvent,
} from '@/lib/realtime/notificationClient';

const TOKEN_STORAGE_KEY = 'medschedule.token';

/**
 * Subscribe to the real-time notification stream.
 *
 * Lifecycle:
 *  - Connects on mount, disconnects on unmount.
 *  - Re-runs only when `enabled` or `staffId` changes.
 *  - Passes events to the optional onEvent callback.
 */
export function useNotificationStream({
  enabled,
  staffId,
  onEvent,
}: {
  enabled: boolean;
  staffId?: number;
  onEvent?: (event: NotificationEvent) => void;
}): void {
  const onEventRef = useRef(onEvent);
  const clientRef = useRef<NotificationClient | null>(null);

  // Keep the ref current without triggering re-renders during render
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!enabled) return;

    // Guard: only run in the browser (not during SSR)
    if (typeof window === 'undefined') return;

    const token = window.localStorage.getItem(TOKEN_STORAGE_KEY) ?? undefined;
    const url = resolveNotificationWsUrl();

    // Build a WebSocket factory so @stomp/stompjs v7 can create the connection.
    const wsUrl = token ? `${url}?token=${encodeURIComponent(token)}` : url;

    const client = createNotificationClient({
      url: wsUrl,
      token,
      staffId,
      webSocketFactory: () => {
        const ws = new WebSocket(wsUrl);
        return ws;
      },
    });

    const unsubscribe = client.onEvent((event: NotificationEvent) => {
      onEventRef.current?.(event);
    });

    client.connect();
    clientRef.current = client;

    return () => {
      unsubscribe();
      client.disconnect();
      clientRef.current = null;
    };
  }, [enabled, staffId]);
}
