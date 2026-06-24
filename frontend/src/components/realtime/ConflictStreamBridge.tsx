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
  // Persist the seen-set across page reloads so the server's
  // post-reconnect CONFLICT_BATCH re-broadcast doesn't trigger a
  // duplicate "Có N xung đột mới" toast every time the user hits F5.
  // sessionStorage (not localStorage) so a fresh tab/session still
  // gets the announcement; a reload mid-session stays quiet.
  const seenIdsRef = useRef<Set<string>>(loadSeenIds());
  // Mirror the ref into sessionStorage whenever it grows past the cap
  // so we don't pay the JSON round-trip on every event.
  const seenDirtyRef = useRef(false);
  // Mount timestamp — used to suppress the very first CONFLICT_BATCH
  // that races in right after the WS connects. The backend re-broadcasts
  // the current state to every freshly-connected subscriber, so without
  // this guard the user gets a noisy toast on every F5 even when the
  // sessionStorage dedupe somehow misses.
  const mountedAtRef = useRef<number>(0);
  const BATCH_SUPPRESS_WINDOW_MS = 3_000;

  useEffect(() => {
    // Set mount timestamp once on mount (outside the early-return guard)
    if (mountedAtRef.current === 0) {
      mountedAtRef.current = Date.now();
    }

    if (state.recentEvents.length === 0) return;

    // Walk from oldest to newest, only firing for events we
    // haven't surfaced yet. The key is eventType + conflictId so
    // the same conflict broadcast twice doesn't double-toast. For
    // CONFLICT_BATCH the conflictId is server-assigned per batch
    // and may repeat, so we additionally dedupe by periodId +
    // totalConflicts inside the same window.
    const fresh: ConflictEvent[] = [];
    for (let i = state.recentEvents.length - 1; i >= 0; i -= 1) {
      const event = state.recentEvents[i];
      const key = dedupeKey(event);
      if (seenIdsRef.current.has(key)) continue;
      seenIdsRef.current.add(key);
      fresh.push(event);
    }

    // The most-recent CONFLICT_BATCH that arrived in the suppression
    // window after mount is almost certainly the initial state
    // re-broadcast — swallow it even when the dedupe key didn't catch
    // it (different conflictId on a fresh page load).
    if (Date.now() - mountedAtRef.current < BATCH_SUPPRESS_WINDOW_MS) {
      const idx = fresh.findIndex((e) => e.eventType === 'CONFLICT_BATCH');
      if (idx >= 0) {
        const [suppressed] = fresh.splice(idx, 1);
        // Still mark the key as seen so the next re-render doesn't
        // re-surface it once the window expires.
        seenIdsRef.current.add(dedupeKey(suppressed));
      }
    }

    if (fresh.length === 0) return;
    seenDirtyRef.current = true;

    // Group CONFLICT_DETECTED events into a single toast. Without this
    // coalescing a single conflict-check run that finds N violations would
    // surface N overlapping red toasts — overwhelming and easy to miss
    // individual conflicts. The store keeps the per-conflict detail so
    // the badge / sidebar count remains precise.
    const detectedEvents = fresh.filter((e) => e.eventType === 'CONFLICT_DETECTED');
    const batchEvents = fresh.filter((e) => e.eventType === 'CONFLICT_BATCH');
    const resolvedEvents = fresh.filter((e) => e.eventType === 'CONFLICT_RESOLVED');

    if (detectedEvents.length > 0) {
      if (detectedEvents.length === 1) {
        error(`Xung đột mới: ${formatConflictBody(detectedEvents[0])}`);
      } else {
        const firstBody = formatConflictBody(detectedEvents[0]);
        error(
          `Phát hiện ${detectedEvents.length} xung đột mới (gần nhất: ${firstBody}). Xem chi tiết tại Báo cáo xung đột.`
        );
      }
    }

    batchEvents.forEach((event) => {
      const total = event.totalConflicts ?? 0;
      if (total > 0) {
        info(`Có ${total} xung đột mới trong kỳ ${event.periodId ?? '?'}`);
      }
    });

    if (resolvedEvents.length > 0) {
      success('Đã giải quyết xung đột');
    }
  }, [state.recentEvents, error, info, success]);

  // Flush the seen-set to sessionStorage + trim it so it doesn't
  // grow unbounded in long sessions.
  useEffect(() => {
    if (!seenDirtyRef.current) return;
    seenDirtyRef.current = false;
    persistSeenIds(seenIdsRef.current);
  }, [state.recentEvents.length]);

  // Cap the seen set to prevent unbounded growth in long
  // sessions; the recentEvents cap already gives us a 20-event
  // sliding window so this is plenty.
  useEffect(() => {
    if (seenIdsRef.current.size > 200) {
      const trimmed = new Set(
        Array.from(seenIdsRef.current).slice(-200)
      );
      seenIdsRef.current = trimmed;
      persistSeenIds(trimmed);
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

/**
 * Dedupe key for the seen-set. CONFLICT_BATCH events reuse the same
 * `conflictId` (=0) across re-broadcasts, so we add periodId +
 * totalConflicts to disambiguate; for DETECTED/RESOLVED the conflictId
 * is already unique per real conflict.
 */
function dedupeKey(event: ConflictEvent): string {
  if (event.eventType === 'CONFLICT_BATCH') {
    return `CONFLICT_BATCH:${event.periodId ?? '?'}:${event.totalConflicts ?? 0}`;
  }
  return `${event.eventType}:${event.conflictId}`;
}

const SEEN_IDS_STORAGE_KEY = 'medschedule.conflict.seen';

function loadSeenIds(): Set<string> {
  if (typeof window === 'undefined') return new Set();
  try {
    const raw = window.sessionStorage.getItem(SEEN_IDS_STORAGE_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return new Set(parsed.filter((v): v is string => typeof v === 'string'));
  } catch {
    // Corrupt JSON or storage unavailable — fall through to empty set.
  }
  return new Set();
}

function persistSeenIds(ids: Set<string>): void {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.setItem(SEEN_IDS_STORAGE_KEY, JSON.stringify(Array.from(ids)));
  } catch {
    // Storage may be full or disabled — silent best-effort.
  }
}