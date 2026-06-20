import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";

describe("useAutoDismiss", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("does nothing when value is null", () => {
    const onDismiss = vi.fn();
    renderHook(() => useAutoDismiss<string | null>(null, onDismiss, 5000));
    vi.advanceTimersByTime(10_000);
    expect(onDismiss).not.toHaveBeenCalled();
  });

  it("calls onDismiss after delay when value becomes truthy", () => {
    const onDismiss = vi.fn();
    renderHook(() => useAutoDismiss("Đã lưu thành công", onDismiss, 5000));
    expect(onDismiss).not.toHaveBeenCalled();
    vi.advanceTimersByTime(4_999);
    expect(onDismiss).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("uses default delay of 5000ms", () => {
    const onDismiss = vi.fn();
    renderHook(() => useAutoDismiss("Lỗi", onDismiss));
    vi.advanceTimersByTime(4_999);
    expect(onDismiss).not.toHaveBeenCalled();
    vi.advanceTimersByTime(2);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("resets timer when value changes before delay elapses", () => {
    const onDismiss = vi.fn();
    const { rerender } = renderHook(
      ({ msg }: { msg: string }) => useAutoDismiss(msg, onDismiss, 5000),
      { initialProps: { msg: "first" } },
    );
    vi.advanceTimersByTime(3_000);
    rerender({ msg: "second" });
    vi.advanceTimersByTime(3_000);
    // First timer (would have fired at t=5000) was reset when value changed
    // at t=3000; second timer fires at t=3000+5000=8000.
    expect(onDismiss).not.toHaveBeenCalled();
    vi.advanceTimersByTime(2_000);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("clears pending timer on unmount", () => {
    const onDismiss = vi.fn();
    const { unmount } = renderHook(() => useAutoDismiss("pending", onDismiss, 5000));
    vi.advanceTimersByTime(2_000);
    unmount();
    vi.advanceTimersByTime(10_000);
    expect(onDismiss).not.toHaveBeenCalled();
  });

  it("supports non-string value types (e.g. error object)", () => {
    type Err = { code: number; message: string } | null;
    const onDismiss = vi.fn();
    const err: Err = { code: 409, message: "Conflict" };
    renderHook(() => useAutoDismiss<Err>(err, onDismiss, 1000));
    vi.advanceTimersByTime(1_000);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});