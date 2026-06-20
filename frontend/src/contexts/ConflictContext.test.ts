import { describe, it, expect } from 'vitest';
import {
  conflictReducer,
  initialConflictState,
  RECENT_EVENTS_CAP,
  type ConflictState,
} from './ConflictContext';
import type { ConflictEvent } from '@/lib/realtime/conflictClient';

const baseEvent: ConflictEvent = {
  eventType: 'CONFLICT_DETECTED',
  conflictId: 100,
  staffName: 'BS. A',
  workDate: '2026-06-15',
  shiftTypeName: 'Trực 24/24',
  timestamp: '2026-06-15T10:00:00Z',
};

const baseBatchEvent: ConflictEvent = {
  eventType: 'CONFLICT_BATCH',
  conflictId: 0,
  totalConflicts: 5,
  periodId: 7,
  timestamp: '2026-06-15T10:00:00Z',
};

const baseResolveEvent: ConflictEvent = {
  eventType: 'CONFLICT_RESOLVED',
  conflictId: 100,
  scheduleId: 200,
  timestamp: '2026-06-15T10:05:00Z',
};

describe('conflictReducer', () => {
  describe('CONFLICT_DETECTED', () => {
    it('adds a new conflict to the unresolved set', () => {
      const next = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: baseEvent,
      });
      expect(next.unresolvedCount).toBe(1);
      expect(next.unresolvedIds.has(100)).toBe(true);
      expect(next.lastEventWasNew).toBe(true);
    });

    it('deduplicates by conflictId', () => {
      const once = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: baseEvent,
      });
      const twice = conflictReducer(once, {
        type: 'ADD_CONFLICT',
        event: baseEvent,
      });
      // Both the unresolved set AND the recent-event history
      // stay flat — a re-broadcast is treated as a no-op.
      expect(twice.unresolvedCount).toBe(1);
      expect(twice.recentEvents).toHaveLength(1);
    });

    it('caps the recent events list to RECENT_EVENTS_CAP', () => {
      let state = initialConflictState;
      for (let i = 0; i < RECENT_EVENTS_CAP + 5; i += 1) {
        state = conflictReducer(state, {
          type: 'ADD_CONFLICT',
          event: { ...baseEvent, conflictId: i + 1 },
        });
      }
      expect(state.recentEvents).toHaveLength(RECENT_EVENTS_CAP);
    });
  });

  describe('CONFLICT_BATCH', () => {
    it('bumps the unresolved count by totalConflicts', () => {
      const next = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: baseBatchEvent,
      });
      expect(next.unresolvedCount).toBe(5);
      expect(next.lastEventWasNew).toBe(true);
    });

    it('ignores batches with totalConflicts = 0', () => {
      const next = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: { ...baseBatchEvent, totalConflicts: 0 },
      });
      expect(next.unresolvedCount).toBe(0);
      expect(next.lastEventWasNew).toBe(false);
    });

    it('dedupes a back-to-back CONFLICT_BATCH with the same periodId + totalConflicts (server re-broadcast)', () => {
      const once = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: baseBatchEvent,
      });
      const twice = conflictReducer(once, {
        type: 'ADD_CONFLICT',
        event: {
          ...baseBatchEvent,
          // The server typically reuses conflictId=0 for every batch
          // and only changes the timestamp.
          timestamp: '2026-06-15T10:05:00Z',
        },
      });
      // A duplicate snapshot must not grow the count or recentEvents.
      expect(twice.unresolvedCount).toBe(once.unresolvedCount);
      expect(twice.recentEvents).toHaveLength(once.recentEvents.length);
    });

    it('treats a different periodId + totalConflicts as a new batch', () => {
      const once = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: baseBatchEvent,
      });
      const next = conflictReducer(once, {
        type: 'ADD_CONFLICT',
        event: { ...baseBatchEvent, periodId: 8, totalConflicts: 2 },
      });
      expect(next.unresolvedCount).toBe(7);
      expect(next.recentEvents).toHaveLength(2);
    });
  });

  describe('CONFLICT_RESOLVED', () => {
    it('removes the conflict from the unresolved set', () => {
      const seeded: ConflictState = {
        ...initialConflictState,
        unresolvedIds: new Set([100, 101]),
        unresolvedCount: 2,
      };
      const next = conflictReducer(seeded, {
        type: 'ADD_CONFLICT',
        event: baseResolveEvent,
      });
      expect(next.unresolvedCount).toBe(1);
      expect(next.unresolvedIds.has(100)).toBe(false);
      expect(next.unresolvedIds.has(101)).toBe(true);
      expect(next.lastEventWasNew).toBe(false);
    });

    it('is a no-op when the id is not tracked', () => {
      const next = conflictReducer(initialConflictState, {
        type: 'ADD_CONFLICT',
        event: baseResolveEvent,
      });
      expect(next).toBe(initialConflictState);
    });
  });

  describe('RESOLVE_CONFLICT action', () => {
    it('removes the id from the unresolved set', () => {
      const seeded: ConflictState = {
        ...initialConflictState,
        unresolvedIds: new Set([42]),
        unresolvedCount: 1,
      };
      const next = conflictReducer(seeded, {
        type: 'RESOLVE_CONFLICT',
        conflictId: 42,
      });
      expect(next.unresolvedCount).toBe(0);
    });
  });

  describe('SEED_COUNT action', () => {
    it('replaces the unresolved set with the seed values', () => {
      const seeded: ConflictState = {
        ...initialConflictState,
        unresolvedIds: new Set([1, 2, 3]),
        unresolvedCount: 3,
      };
      const next = conflictReducer(seeded, {
        type: 'SEED_COUNT',
        count: 7,
        ids: [10, 20, 30, 40, 50, 60, 70],
      });
      expect(next.unresolvedCount).toBe(7);
      expect(next.unresolvedIds.size).toBe(7);
      expect(next.unresolvedIds.has(1)).toBe(false);
      expect(next.unresolvedIds.has(70)).toBe(true);
    });
  });

  describe('RESET action', () => {
    it('returns the initial state', () => {
      const dirty: ConflictState = {
        unresolvedCount: 5,
        unresolvedIds: new Set([1, 2, 3, 4, 5]),
        recentEvents: [baseEvent],
        lastEventWasNew: true,
      };
      expect(conflictReducer(dirty, { type: 'RESET' })).toEqual(
        initialConflictState,
      );
    });
  });
});