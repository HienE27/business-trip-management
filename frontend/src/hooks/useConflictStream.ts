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
 *  - `applyEvent` is read through a ref so the subscription
 *    callback always sees the latest dispatcher without forcing
 *    the effect to re-run on every store update. This prevents
 *    the "Maximum update depth exceeded" loop where each
 *    inbound event triggers a re-render which would otherwise
 *    tear down + recreate the WebSocket.
 */
export function useConflictStream({ enabled }: { enabled: boolean }): void {
  const { applyEvent } = useConflictStore();
  // Keep the latest dispatcher in a ref so the WS callback can
  // stay bound to the same client instance across renders.
  const applyEventRef = useRef(applyEvent);
  applyEventRef.current = applyEvent;
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
      applyEventRef.current(event);
    });

    client.connect();
    clientRef.current = client;

    return () => {
      unsubscribe();
      client.disconnect();
      clientRef.current = null;
    };
  }, [enabled]);
}