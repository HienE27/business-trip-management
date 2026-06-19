import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useConflictStream } from './useConflictStream';
import type { ConflictEvent } from '@/lib/realtime/conflictClient';

// --- Mocks (hoisted by Vitest) ---

let _emit: (e: ConflictEvent) => void;
let _connect: ReturnType<typeof vi.fn>;
let _disconnect: ReturnType<typeof vi.fn>;
let _unsubscribe: ReturnType<typeof vi.fn>;
let _applyEvent: ReturnType<typeof vi.fn>;

vi.mock('@/contexts/ConflictContext', () => ({
  useConflictStore: vi.fn(() => ({
    applyEvent: (e: ConflictEvent) => { _applyEvent?.(e); },
  })),
}));

vi.mock('@/lib/realtime/conflictClient', () => ({
  resolveConflictWsUrl: vi.fn(() => 'ws://localhost:8080/ws/conflicts'),
  createConflictClient: vi.fn(() => ({
    connect: () => { _connect(); },
    disconnect: () => { _disconnect(); },
    onEvent: (cb: (e: ConflictEvent) => void) => {
      _emit = cb;
      return () => { _unsubscribe(); };
    },
  })),
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
    vi.clearAllMocks();
    _applyEvent = vi.fn();
    _connect = vi.fn();
    _disconnect = vi.fn();
    _unsubscribe = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('connects when enabled is true', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_connect).toHaveBeenCalledOnce();
    unmount();
  });

  it('does NOT connect when enabled is false', () => {
    renderHook(() => useConflictStream({ enabled: false }));
    expect(_connect).not.toHaveBeenCalled();
  });

  it('disconnects on unmount', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_disconnect).not.toHaveBeenCalled();
    unmount();
    expect(_disconnect).toHaveBeenCalledOnce();
  });

  it('dispatches CONFLICT_DETECTED event to the store', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    act(() => { _emit(makeEvent({ conflictId: 7 })); });
    expect(_applyEvent).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'CONFLICT_DETECTED', conflictId: 7 })
    );
    unmount();
  });

  it('unsubscribes on cleanup', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    unmount();
    expect(_unsubscribe).toHaveBeenCalledOnce();
  });

  it('reconnects when enabled flips from false to true', () => {
    const { rerender, unmount } = renderHook(
      ({ enabled }: { enabled: boolean }) => useConflictStream({ enabled }),
      { initialProps: { enabled: false } }
    );
    rerender({ enabled: true });
    expect(_connect).toHaveBeenCalled();
    unmount();
  });

  it('disconnects when enabled flips from true to false', () => {
    const { rerender, unmount } = renderHook(
      ({ enabled }: { enabled: boolean }) => useConflictStream({ enabled }),
      { initialProps: { enabled: true } }
    );
    rerender({ enabled: false });
    expect(_disconnect).toHaveBeenCalledOnce();
    unmount();
  });

  it('handles CONFLICT_RESOLVED event', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    act(() => { _emit(makeEvent({ eventType: 'CONFLICT_RESOLVED', conflictId: 5 })); });
    expect(_applyEvent).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'CONFLICT_RESOLVED', conflictId: 5 })
    );
    unmount();
  });

  it('handles CONFLICT_BATCH event', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    act(() => { _emit(makeEvent({ eventType: 'CONFLICT_BATCH', totalConflicts: 3 })); });
    expect(_applyEvent).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'CONFLICT_BATCH', totalConflicts: 3 })
    );
    unmount();
  });

  it('connects via WebSocket using the resolved URL', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_connect).toHaveBeenCalledOnce();
    unmount();
  });

  it('does not crash when window is not available (SSR guard)', () => {
    const { unmount } = renderHook(() => useConflictStream({ enabled: true }));
    expect(_connect).toHaveBeenCalledOnce();
    unmount();
  });
});
