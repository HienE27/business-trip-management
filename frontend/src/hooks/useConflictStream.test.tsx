import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useConflictStream } from './useConflictStream';
import type { ConflictEvent } from '@/lib/realtime/conflictClient';

// --- Mocks (hoisted by Vitest) ---

let _emit: (e: ConflictEvent) => void;
let _connectCalled = 0;
let _connect: () => void;
let _disconnectCalled = 0;
let _disconnect: () => void;
let _unsubscribeCalled = 0;
let _unsubscribe: () => void;
let _lastApplyEvent: ConflictEvent | null = null;
let _applyEvent: (e: ConflictEvent) => void;

vi.mock('@/contexts/ConflictContext', () => ({
  useConflictStore: () => ({
    applyEvent: (e: ConflictEvent) => { _lastApplyEvent = e; _applyEvent?.(e); },
  }),
}));

vi.mock('@/lib/realtime/conflictClient', () => ({
  resolveConflictWsUrl: () => 'ws://localhost:8080/ws/conflicts',
  createConflictClient: () => ({
    connect: () => { _connectCalled++; _connect?.(); },
    disconnect: () => { _disconnectCalled++; _disconnect?.(); },
    onEvent: (cb: (e: ConflictEvent) => void) => {
      _emit = cb;
      return () => { _unsubscribeCalled++; _unsubscribe?.(); };
    },
  }),
}));

function makeEvent(overrides: Partial<ConflictEvent> = {}): ConflictEvent {
  return {
    eventType: 'CONFLICT_DETECTED',
    conflictId: 1,
    staffName: 'BS. A',
    workDate: '2026-06-22',
    timestamp: new Date().toISOString(),
    ...overrides,
  };
}

describe('useConflictStream', () => {
  beforeEach(() => {
    _connectCalled = 0;
    _disconnectCalled = 0;
    _unsubscribeCalled = 0;
    _lastApplyEvent = null;
    _applyEvent = () => {};
    _connect = () => {};
    _disconnect = () => {};
    _unsubscribe = () => {};
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('connects when enabled is true', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_connectCalled).toBe(1);
    unmount();
  });

  it('does NOT connect when enabled is false', () => {
    renderHook(() => useConflictStream({ enabled: false }));
    expect(_connectCalled).toBe(0);
  });

  it('disconnects on unmount', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_disconnectCalled).toBe(0);
    unmount();
    expect(_disconnectCalled).toBe(1);
  });

  it('dispatches CONFLICT_DETECTED event to the store', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    act(() => { _emit(makeEvent({ conflictId: 7 })); });
    expect(_lastApplyEvent).toMatchObject({ eventType: 'CONFLICT_DETECTED', conflictId: 7 });
    unmount();
  });

  it('unsubscribes on cleanup', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    unmount();
    expect(_unsubscribeCalled).toBe(1);
  });

  it('reconnects when enabled flips from false to true', () => {
    const { rerender, unmount } = renderHook(
      ({ enabled }: { enabled: boolean }) => useConflictStream({ enabled }),
      { initialProps: { enabled: false } }
    );
    rerender({ enabled: true });
    expect(_connectCalled).toBe(1);
    unmount();
  });

  it('disconnects when enabled flips from true to false', () => {
    const { rerender, unmount } = renderHook(
      ({ enabled }: { enabled: boolean }) => useConflictStream({ enabled }),
      { initialProps: { enabled: true } }
    );
    rerender({ enabled: false });
    expect(_disconnectCalled).toBe(1);
    unmount();
  });

  it('handles CONFLICT_RESOLVED event', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    act(() => { _emit(makeEvent({ eventType: 'CONFLICT_RESOLVED', conflictId: 5 })); });
    expect(_lastApplyEvent).toMatchObject({ eventType: 'CONFLICT_RESOLVED', conflictId: 5 });
    unmount();
  });

  it('handles CONFLICT_BATCH event', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    act(() => { _emit(makeEvent({ eventType: 'CONFLICT_BATCH', totalConflicts: 3 })); });
    expect(_lastApplyEvent).toMatchObject({ eventType: 'CONFLICT_BATCH', totalConflicts: 3 });
    unmount();
  });

  it('connects via WebSocket using the resolved URL', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_connectCalled).toBe(1);
    unmount();
  });

  it('does not crash when window is not available (SSR guard)', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_connectCalled).toBe(1);
    unmount();
  });
});
