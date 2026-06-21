"use client";

import { useEffect, useRef } from 'react';
import {
  createConflictClient,
  resolveConflictWsUrl,
  type ConflictClient,
  type ConflictEvent,
} from '@/lib/realtime/conflictClient';
import { useConflictStore } from '@/contexts/ConflictContext';

const TOKEN_STORAGE_KEY = 'medschedule.token';

/**
 * Subscribe to the real-time conflict stream and dispatch every
 * received event into the ConflictContext store.
 *
 * Lifecycle:
 *  - Connects on mount, disconnects on unmount.
 *  - Re-runs only when `enabled` flips (so role changes can
 *    cleanly tear down + restart the connection).
 *  - Token is read from localStorage at the time the hook runs
 *    to keep it stable across renders.
 *
 * Returns nothing — the side effect is the subscription itself.
 * Consumers read the store through `useConflictStore()`.
 */
export function useConflictStream({ enabled }: { enabled: boolean }): void {
  const { applyEvent } = useConflictStore();
  const clientRef = useRef<ConflictClient | null>(null);

  useEffect(() => {
    if (!enabled) return;

    // Guard: only run in the browser (not during SSR)
    if (typeof window === 'undefined') return;

    const token = window.localStorage.getItem(TOKEN_STORAGE_KEY) ?? undefined;
    const url = resolveConflictWsUrl();

    // Build a WebSocket factory so @stomp/stompjs v7 can create the connection.
    // Attach the token in the URL query param; the backend reads it from the handshake.
    const wsUrl = token ? `${url}?token=${encodeURIComponent(token)}` : url;

    const client = createConflictClient({
      url: wsUrl,
      token,
      webSocketFactory: () => {
        const ws = new WebSocket(wsUrl);
        return ws;
      },
    });
    const unsubscribe = client.onEvent((event: ConflictEvent) => {
      applyEvent(event);
    });

    client.connect();
    clientRef.current = client;

    return () => {
      unsubscribe();
      client.disconnect();
      clientRef.current = null;
    };
  }, [enabled, applyEvent]);
}
