'use client';

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useReducer,
  type ReactNode,
} from 'react';
import type { ConflictEvent } from '@/lib/realtime/conflictClient';

/**
 * In-memory store for real-time conflict events. The provider is
 * the single source of truth for "how many conflicts are open
 * right now" so the sidebar badge, dashboard widgets, and toast
 * surface all stay in sync without prop drilling.
 *
 * The shape is intentionally minimal:
 *  - `unresolvedCount` drives the badge.
 *  - `recentEvents` (capped) is what the toast layer reads from
 *    when broadcasting a transient notification.
 *
 * The reducer is exported separately so the test suite can
 * exercise state transitions without a React renderer.
 */
export type ConflictState = {
  /** Number of distinct unresolved conflict IDs we've seen. */
  unresolvedCount: number;
  /** Distinct IDs the badge should count. */
  unresolvedIds: Set<number>;
  /**
   * Newest events first, capped to prevent memory growth during
   * long sessions.
   */
  recentEvents: ConflictEvent[];
  /** Set true when the most recent event increased the count. */
  lastEventWasNew: boolean;
};

export const RECENT_EVENTS_CAP = 20;

export type ConflictAction =
  | { type: 'ADD_CONFLICT'; event: ConflictEvent }
  | { type: 'RESOLVE_CONFLICT'; conflictId: number }
  | { type: 'SEED_COUNT'; count: number; ids: number[] }
  | { type: 'RESET' };

export const initialConflictState: ConflictState = {
  unresolvedCount: 0,
  unresolvedIds: new Set<number>(),
  recentEvents: [],
  lastEventWasNew: false,
};

export function conflictReducer(
  state: ConflictState,
  action: ConflictAction,
): ConflictState {
  switch (action.type) {
    case 'ADD_CONFLICT': {
      const { event } = action;
      // CONFLICT_RESOLVED decrements instead of growing the set.
      if (event.eventType === 'CONFLICT_RESOLVED') {
        if (!state.unresolvedIds.has(event.conflictId)) return state;
        const nextIds = new Set(state.unresolvedIds);
        nextIds.delete(event.conflictId);
        return {
          ...state,
          unresolvedIds: nextIds,
          unresolvedCount: nextIds.size,
          recentEvents: [event, ...state.recentEvents].slice(0, RECENT_EVENTS_CAP),
          lastEventWasNew: false,
        };
      }
      // CONFLICT_BATCH doesn't carry individual IDs we can track,
      // so the count bump is best-effort: we bump the counter by
      // totalConflicts but we don't have IDs to dedupe later.
      if (event.eventType === 'CONFLICT_BATCH') {
        const bump = event.totalConflicts ?? 0;
        // Dedupe: if the most recent event in recentEvents is also a
        // CONFLICT_BATCH with the same periodId + totalConflicts, this
        // is almost certainly the server re-broadcasting the current
        // state (e.g. right after the client reconnects). Treat it as
        // a no-op so we don't re-surface the same notification twice
        // in a row.
        const previousBatch = state.recentEvents[0];
        if (
          previousBatch &&
          previousBatch.eventType === 'CONFLICT_BATCH' &&
          previousBatch.periodId === event.periodId &&
          previousBatch.totalConflicts === bump
        ) {
          return state;
        }
        return {
          ...state,
          // Approximate: the seed endpoint refreshes the real set
          // when the user opens the conflicts page.
          unresolvedCount: state.unresolvedCount + bump,
          recentEvents: [event, ...state.recentEvents].slice(0, RECENT_EVENTS_CAP),
          lastEventWasNew: bump > 0,
        };
      }
      // CONFLICT_DETECTED — dedupe by conflictId.
      if (state.unresolvedIds.has(event.conflictId)) return state;
      const nextIds = new Set(state.unresolvedIds);
      nextIds.add(event.conflictId);
      return {
        unresolvedIds: nextIds,
        unresolvedCount: nextIds.size,
        recentEvents: [event, ...state.recentEvents].slice(0, RECENT_EVENTS_CAP),
        lastEventWasNew: true,
      };
    }
    case 'RESOLVE_CONFLICT': {
      if (!state.unresolvedIds.has(action.conflictId)) return state;
      const nextIds = new Set(state.unresolvedIds);
      nextIds.delete(action.conflictId);
      return {
        ...state,
        unresolvedIds: nextIds,
        unresolvedCount: nextIds.size,
        lastEventWasNew: false,
      };
    }
    case 'SEED_COUNT': {
      return {
        unresolvedCount: action.count,
        unresolvedIds: new Set(action.ids),
        recentEvents: state.recentEvents,
        lastEventWasNew: false,
      };
    }
    case 'RESET': {
      return initialConflictState;
    }
  }
}

type ConflictContextValue = {
  state: ConflictState;
  applyEvent: (event: ConflictEvent) => void;
  resolveConflict: (conflictId: number) => void;
  seed: (count: number, ids: number[]) => void;
  reset: () => void;
};

const ConflictContext = createContext<ConflictContextValue | null>(null);

export function ConflictProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(conflictReducer, initialConflictState);

  const applyEvent = useCallback((event: ConflictEvent) => {
    dispatch({ type: 'ADD_CONFLICT', event });
  }, []);

  const resolveConflict = useCallback((conflictId: number) => {
    dispatch({ type: 'RESOLVE_CONFLICT', conflictId });
  }, []);

  const seed = useCallback((count: number, ids: number[]) => {
    dispatch({ type: 'SEED_COUNT', count, ids });
  }, []);

  const reset = useCallback(() => {
    dispatch({ type: 'RESET' });
  }, []);

  const value = useMemo<ConflictContextValue>(
    () => ({ state, applyEvent, resolveConflict, seed, reset }),
    [state, applyEvent, resolveConflict, seed, reset],
  );

  return (
    <ConflictContext.Provider value={value}>
      {children}
    </ConflictContext.Provider>
  );
}

export function useConflictStore(): ConflictContextValue {
  const ctx = useContext(ConflictContext);
  if (!ctx) {
    throw new Error('useConflictStore must be used inside <ConflictProvider>');
  }
  return ctx;
}

/**
 * Hook consumed by the sidebar / dashboard widgets. Returns just
 * the bits they actually need so re-renders stay narrow.
 */
export function useConflictCount(): number {
  return useConflictStore().state.unresolvedCount;
}