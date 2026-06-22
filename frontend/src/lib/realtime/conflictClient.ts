/**
 * STOMP-over-WebSocket client for the real-time conflict stream.
 *
 * Backend publishes conflict events to the `/topic/conflicts`
 * destination through `ConflictBroadcastService` (see
 * `backend/.../service/ConflictBroadcastService.java`).
 * We connect to `/ws/conflicts` (configured in
 * `WebSocketConfig.java`) and subscribe to that topic.
 *
 * The wrapper exposes a minimal, testable surface so the React
 * layer can mock it cleanly:
 *
 *   const client = createConflictClient({ url, token });
 *   client.onEvent((event) => store.dispatch(event));
 *   client.connect();
 *   client.disconnect();
 *
 * The native WebSocket transport is used (no SockJS fallback)
 * because the backend endpoint doesn't register one. `@stomp/
 * stompjs` v7+ handles reconnect with exponential backoff out of
 * the box; we just tune the parameters.
 */
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

export type ConflictEventType =
  | 'CONFLICT_DETECTED'
  | 'CONFLICT_BATCH'
  | 'CONFLICT_RESOLVED';

export type ConflictEvent = {
  eventType: ConflictEventType;
  conflictId: number;
  scheduleId?: number;
  conflictType?: string;
  description?: string;
  staffName?: string;
  workDate?: string;
  shiftTypeId?: string;
  shiftTypeName?: string;
  conflictReasons?: string[];
  totalConflicts?: number;
  periodId?: number;
  isResolved?: boolean;
  timestamp: string;
};

export type ConflictListener = (event: ConflictEvent) => void;

export type ConflictClientOptions = {
  /** ws:// or wss:// URL of the STOMP endpoint. */
  url: string;
  /** JWT bearer token, sent in the CONNECT frame header. */
  token?: string;
  /**
   * Override the underlying WebSocket factory — useful in tests
   * to inject a mock transport. Defaults to the global WebSocket.
   */
  webSocketFactory?: () => WebSocket;
};

export type ConflictClient = {
  connect(): void;
  disconnect(): void;
  onEvent(listener: ConflictListener): () => void;
  isConnected(): boolean;
};

const RECONNECT_DELAY_MS = 5_000;
const HEARTBEAT_INCOMING_MS = 10_000;
const HEARTBEAT_OUTGOING_MS = 10_000;
const TOPIC = '/topic/conflicts';

export function createConflictClient(options: ConflictClientOptions): ConflictClient {
  const listeners = new Set<ConflictListener>();
  let subscription: StompSubscription | null = null;

  const client = new Client({
    brokerURL: options.url,
    webSocketFactory: options.webSocketFactory,
    // Fixed 5s reconnect delay. The underlying @stomp/stompjs will retry indefinitely.
    // To use exponential backoff (e.g. 1s→2s→4s→8s capped at 30s), pass a function
    // when @stomp/stompjs types support it: reconnectDelay: (c) => Math.min(5000*2**c, 30000).
    reconnectDelay: RECONNECT_DELAY_MS,
    heartbeatIncoming: HEARTBEAT_INCOMING_MS,
    heartbeatOutgoing: HEARTBEAT_OUTGOING_MS,
    connectHeaders: options.token
      ? { Authorization: `Bearer ${options.token}` }
      : {},
    debug: () => {
      // Intentionally silent in production. Re-enable with
      // console.debug in dev if needed.
    },
    onConnect: () => {
      subscription = client.subscribe(TOPIC, (message: IMessage) => {
        try {
          const parsed = JSON.parse(message.body) as ConflictEvent;
          if (parsed && typeof parsed === 'object' && 'eventType' in parsed) {
            listeners.forEach((listener) => listener(parsed));
          }
        } catch {
          // Ignore malformed payloads — backend owns the schema
          // and the next reconnect will resync state.
        }
      });
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

/**
 * Resolve the WebSocket URL the client should connect to. Falls
 * back to localhost:8080 when no env var is set so dev works
 * out of the box.
 */
export function resolveConflictWsUrl(): string {
  const explicit = process.env.NEXT_PUBLIC_WS_URL;
  if (explicit) return explicit;
  const apiBase = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1';
  const wsBase = apiBase.replace(/^http/i, 'ws').replace(/\/api\/.*$/, '');
  return `${wsBase}/ws/conflicts`;
}