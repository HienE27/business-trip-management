"use client";

import { useEffect } from "react";

/**
 * Auto-dismiss transient banner state (success/error messages) after a delay.
 *
 * Usage:
 *   const [message, setMessage] = useState<string | null>(null);
 *   useAutoDismiss(message, () => setMessage(null), 5000);
 *
 * The dismiss callback is only invoked when the value is non-null, so
 * passing null is safe and avoids spawning timers for idle state.
 *
 * Returns void. Cleans up the timer if the value changes or the component unmounts.
 */
export function useAutoDismiss<T>(
  value: T,
  onDismiss: () => void,
  delayMs = 5000,
): void {
  useEffect(() => {
    if (value == null) return;
    const timer = setTimeout(onDismiss, delayMs);
    return () => clearTimeout(timer);
  }, [value, onDismiss, delayMs]);
}