'use client';

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

    if (typeof window === 'undefined') return;

    const token = window.localStorage.getItem(TOKEN_STORAGE_KEY) ?? undefined;
    const url = resolveConflictWsUrl();

    const client = createConflictClient({ url, token });
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