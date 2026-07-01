/**
 * STOMP-over-WebSocket client for the real-time notification stream.
 *
 * Backend publishes notification events to the `/topic/notifications`
 * destination through `NotificationBroadcastService` (see
 * `backend/.../service/NotificationBroadcastService.java`).
 * We connect to `/ws/notifications` (configured in
 * `WebSocketConfig.java`) and subscribe to that topic.
 *
 * The wrapper exposes a minimal, testable surface so the React
 * layer can mock it cleanly:
 *
 *   const client = createNotificationClient({ url, token });
 *   client.onEvent((event) => store.dispatch(event));
 *   client.connect();
 *   client.disconnect();
 *
 * Uses @stomp/stompjs v7+ for STOMP protocol handling with
 * automatic reconnection.
 */
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

export type NotificationEventType =
  | 'NEW_NOTIFICATION'
  | 'NOTIFICATION_READ'
  | 'ALL_NOTIFICATIONS_READ'
  | 'NOTIFICATION_DELETED';

export type NotificationEvent = {
  eventType: NotificationEventType;
  notificationId?: number;
  staffId?: number;
  title?: string;
  message?: string;
  isRead?: boolean;
  createdAt?: string;
  timestamp: string;
};

export type NotificationListener = (event: NotificationEvent) => void;

export type NotificationClientOptions = {
  /** ws:// or wss:// URL of the STOMP endpoint. */
  url: string;
  /** JWT bearer token, sent in the CONNECT frame header. */
  token?: string;
  /** Staff ID to subscribe to personal notifications (optional). */
  staffId?: number;
  /**
   * Override the underlying WebSocket factory — useful in tests
   * to inject a mock transport. Defaults to the global WebSocket.
   */
  webSocketFactory?: () => WebSocket;
};

export type NotificationClient = {
  connect(): void;
  disconnect(): void;
  onEvent(listener: NotificationListener): () => void;
  isConnected(): boolean;
};

const RECONNECT_DELAY_MS = 5_000;
const HEARTBEAT_INCOMING_MS = 10_000;
const HEARTBEAT_OUTGOING_MS = 10_000;
const GLOBAL_TOPIC = '/topic/notifications';

/**
 * Resolve the WebSocket URL the client should connect to.
 * Falls back to localhost:8080 when no env var is set.
 */
export function resolveNotificationWsUrl(): string {
  const explicit = process.env.NEXT_PUBLIC_WS_URL;
  if (explicit) return explicit;
  const apiBase = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1';
  const wsBase = apiBase.replace(/^http/i, 'ws').replace(/\/api\/.*$/, '');
  return `${wsBase}/ws/notifications`;
}

export function createNotificationClient(options: NotificationClientOptions): NotificationClient {
  const listeners = new Set<NotificationListener>();
  let subscription: StompSubscription | null = null;

  const client = new Client({
    brokerURL: options.url,
    webSocketFactory: options.webSocketFactory,
    reconnectDelay: RECONNECT_DELAY_MS,
    heartbeatIncoming: HEARTBEAT_INCOMING_MS,
    heartbeatOutgoing: HEARTBEAT_OUTGOING_MS,
    connectHeaders: options.token
      ? { Authorization: `Bearer ${options.token}` }
      : {},
    debug: () => {
      // Intentionally silent in production.
    },
    onConnect: () => {
      // Subscribe to global topic for all notifications
      subscription = client.subscribe(GLOBAL_TOPIC, (message: IMessage) => {
        try {
          const parsed = JSON.parse(message.body) as NotificationEvent;
          if (parsed && typeof parsed === 'object' && 'eventType' in parsed) {
            // Filter: if we have a staffId, only show notifications for that staff or global ones
            if (options.staffId !== undefined) {
              const staffId = parsed.staffId;
              // Include if: no staffId specified (global), or staffId matches, or it's a system event
              const isGlobalEvent = !staffId || staffId === options.staffId;
              const isSystemEvent = parsed.eventType === 'ALL_NOTIFICATIONS_READ' || 
                                     parsed.eventType === 'NOTIFICATION_DELETED';
              if (isGlobalEvent || isSystemEvent) {
                listeners.forEach((listener) => listener(parsed));
              }
            } else {
              // No staffId filter, show all
              listeners.forEach((listener) => listener(parsed));
            }
          }
        } catch {
          // Ignore malformed payloads
        }
      });

      // Also subscribe to staff-specific topic if staffId is provided
      if (options.staffId !== undefined) {
        const staffTopic = `${GLOBAL_TOPIC}/${options.staffId}`;
        client.subscribe(staffTopic, (message: IMessage) => {
          try {
            const parsed = JSON.parse(message.body) as NotificationEvent;
            if (parsed && typeof parsed === 'object' && 'eventType' in parsed) {
              listeners.forEach((listener) => listener(parsed));
            }
          } catch {
            // Ignore malformed payloads
          }
        });
      }
    },
    onStompError: () => {
      // Server-side STOMP error frame. Don't crash; the next
      // reconnect attempt will recover.
    },
    onWebSocketClose: () => {
      subscription = null;
    },
  });

  return {
    connect() {
      if (!client.active) {
        client.activate();
      }
    },
    disconnect() {
      subscription?.unsubscribe();
      subscription = null;
      if (client.active) {
        client.deactivate();
      }
    },
    onEvent(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    isConnected() {
      return client.connected;
    },
  };
}
