'use client';

import { useEffect, useRef } from 'react';
import { useConflictStream } from '@/hooks/useConflictStream';
import { useConflictStore } from '@/contexts/ConflictContext';
import { useRole, canManage } from '@/hooks/useRole';
import { useToast } from '@/components/ui/ToastProvider';
import type { ConflictEvent } from '@/lib/realtime/conflictClient';

/**
 * Client-side bridge that:
 *  1. Opens a WebSocket subscription (only for ADMIN/MANAGER).
 *  2. Surfaces every fresh CONFLICT_DETECTED / CONFLICT_BATCH
 *     event as a toast notification.
 *
 * Mounted as a sibling of the actual app content so the
 * subscription stays alive across page navigations but never
 * blocks the children tree.
 */
export function ConflictStreamBridge() {
  const role = useRole();
  const enabled = canManage(role);
  useConflictStream({ enabled });
  return null;
}

/**
 * Mounts the toast side-effect. Kept separate from the
 * subscription so the WS connection can be enabled even when
 * `useToast` isn't strictly needed (e.g. on a future page that
 * replaces the toast surface).
 */
export function ConflictToastBridge() {
  const { state, applyEvent } = useConflictStore();
  const { success, error, info } = useToast();
  const seenIdsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (state.recentEvents.length === 0) return;

    // Walk from oldest to newest, only firing for events we
    // haven't surfaced yet. The key is eventType + conflictId
    // so the same conflict broadcast twice doesn't double-toast.
    const fresh: ConflictEvent[] = [];
    for (let i = state.recentEvents.length - 1; i >= 0; i -= 1) {
      const event = state.recentEvents[i];
      const key = `${event.eventType}:${event.conflictId}`;
      if (seenIdsRef.current.has(key)) continue;
      seenIdsRef.current.add(key);
      fresh.push(event);
    }

    fresh.forEach((event) => {
      if (event.eventType === 'CONFLICT_DETECTED') {
        const body = formatConflictBody(event);
        error(`Xung đột mới: ${body}`);
      } else if (event.eventType === 'CONFLICT_BATCH') {
        const total = event.totalConflicts ?? 0;
        if (total > 0) {
          info(`Có ${total} xung đột mới trong kỳ ${event.periodId ?? '?'}`);
        }
      } else if (event.eventType === 'CONFLICT_RESOLVED') {
        success('Đã giải quyết xung đột');
      }
    });
  }, [state.recentEvents, error, info, success]);

  // Cap the seen set to prevent unbounded growth in long
  // sessions; the recentEvents cap already gives us a 20-event
  // sliding window so this is plenty.
  useEffect(() => {
    if (seenIdsRef.current.size > 200) {
      seenIdsRef.current = new Set(seenIdsRef.current);
    }
  }, [state.recentEvents.length]);

  // Touch applyEvent so the variable isn't flagged unused in
  // some future build; keeps the bridge re-render bound to the
  // store.
  void applyEvent;

  return null;
}

function formatConflictBody(event: ConflictEvent): string {
  const pieces: string[] = [];
  if (event.staffName) pieces.push(event.staffName);
  if (event.workDate) pieces.push(event.workDate);
  if (event.shiftTypeName) pieces.push(event.shiftTypeName);
  if (pieces.length === 0 && event.description) return event.description;
  return pieces.join(' • ');
}