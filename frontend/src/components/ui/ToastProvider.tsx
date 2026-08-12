"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { cn } from "@/lib/utils";

/* ── Toast Notification System ──
 *
 * Types: success / error / warning / info
 * Position: fixed bottom-right (desktop), bottom-center (mobile)
 * Animation: slide-in from right, fade-out on dismiss
 * Auto-dismiss: 4s (configurable via `duration` param)
 * Accessibility: role="status" for success/info, role="alert" for error/warning
 *
 * Design tokens: surface, outline-variant, secondary, error, primary
 */

export type ToastType = "success" | "error" | "warning" | "info";

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}

type ToastContextType = {
  toasts: Toast[];
  success: (message: string, duration?: number) => void;
  error: (message: string, duration?: number) => void;
  warning: (message: string, duration?: number) => void;
  info: (message: string, duration?: number) => void;
  dismiss: (id: string) => void;
  dismissAll: () => void;
};

const ToastContext = createContext<ToastContextType | null>(null);

function createId() {
  // Use React's useId for stable IDs that don't cause hydration mismatch
  // For toasts created dynamically, use a counter to avoid SSR/client mismatch
  return `toast-${Date.now()}`;
}

const TOAST_ICONS: Record<ToastType, string> = {
  success: "check_circle",
  error: "error",
  warning: "warning",
  info: "info",
};

const TOAST_ICON_BG: Record<ToastType, string> = {
  success: "bg-emerald-100",
  error: "bg-red-100",
  warning: "bg-amber-100",
  info: "bg-blue-100",
};

const TOAST_ICON_COLOR: Record<ToastType, string> = {
  success: "text-emerald-800",
  error: "text-red-800",
  warning: "text-amber-800",
  info: "text-blue-800",
};

const TOAST_DEFAULT_DURATION = 4000;

function ToastItem({
  toast,
  onDismiss,
  duration = TOAST_DEFAULT_DURATION,
}: {
  toast: Toast;
  onDismiss: (id: string) => void;
  duration?: number;
}) {
  const [visible, setVisible] = useState(false);
  const onDismissRef = useRef(onDismiss);
  const toastIdRef = useRef(toast.id);

  // Ref pattern: stable callback ref — intentional mutation during render
  // eslint-disable-next-line react-hooks/refs
  onDismissRef.current = onDismiss;
  // Ref pattern: stable ID ref — intentional mutation during render
  // eslint-disable-next-line react-hooks/refs
  toastIdRef.current = toast.id;

  useEffect(() => {
    const raf = requestAnimationFrame(() => setVisible(true));
    const timer = setTimeout(() => {
      setVisible(false);
      setTimeout(() => onDismissRef.current(toastIdRef.current), 300);
    }, duration);
    return () => {
      cancelAnimationFrame(raf);
      clearTimeout(timer);
      setVisible(false);
    };
  }, [duration]);

  const handleClose = () => {
    setVisible(false);
    setTimeout(() => onDismissRef.current(toastIdRef.current), 300);
  };

  return (
    <div
      role={toast.type === "error" || toast.type === "warning" ? "alert" : "status"}
      className={cn(
        "flex items-center gap-3 bg-surface-container-lowest border border-outline-variant",
        "rounded-xl px-4 py-3 shadow-xl min-w-72 max-w-[420px]",
        "transition-all duration-300 ease-out",
        visible ? "opacity-100 translate-x-0" : "opacity-0 translate-x-8"
      )}
    >
      <div
        className={cn(
          "w-8 h-8 rounded-full flex items-center justify-center shrink-0",
          TOAST_ICON_BG[toast.type]
        )}
      >
        <span
          className={cn("material-symbols-outlined text-[18px]", TOAST_ICON_COLOR[toast.type])}
          style={{ fontVariationSettings: "'FILL' 1" }}
          aria-hidden="true"
        >
          {TOAST_ICONS[toast.type]}
        </span>
      </div>

      <p className="text-body-sm text-on-surface flex-1 leading-tight">{toast.message}</p>

      <button
        onClick={handleClose}
        className={cn(
          "w-7 h-7 flex items-center justify-center rounded-full",
          "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface",
          "transition-colors cursor-pointer shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
        )}
        aria-label="Đóng thông báo"
      >
        <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
      </button>
    </div>
  );
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [mounted, setMounted] = useState(false);
  // Cap the number of visible toasts so an API loop (e.g. 403 spam) can't
  // pile up hundreds of cards. Older toasts get evicted first.
  const MAX_TOASTS = 5;

  // Fix hydration mismatch by only rendering toasts after mount
  useEffect(() => {
    setMounted(true);
  }, []);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const dismissAll = useCallback(() => {
    setToasts([]);
  }, []);

  const addToast = useCallback((message: string, type: ToastType, duration?: number) => {
    const id = createId();
    setToasts((prev) => {
      const next = [...prev, { id, message, type, duration }];
      // Evict oldest toasts past the cap (5). Prevents the page from filling
      // with hundreds of identical errors when an API keeps failing in a loop.
      if (next.length > MAX_TOASTS) {
        return next.slice(next.length - MAX_TOASTS);
      }
      return next;
    });
    return id;
  }, []);

  const success = useCallback(
    (message: string, duration?: number) => addToast(message, "success", duration),
    [addToast]
  );
  const error = useCallback(
    (message: string, duration?: number) => addToast(message, "error", duration),
    [addToast]
  );
  const warning = useCallback(
    (message: string, duration?: number) => addToast(message, "warning", duration),
    [addToast]
  );
  const info = useCallback(
    (message: string, duration?: number) => addToast(message, "info", duration),
    [addToast]
  );

  return (
    <ToastContext.Provider value={{ toasts, success, error, warning, info, dismiss, dismissAll }}>
      {children}

      {/* Toast container — bottom-right desktop, bottom-center mobile */}
      {/* Only render after mount to prevent hydration mismatch */}
      {mounted && (
        <div
          role="region"
          aria-label="Thông báo"
          className="fixed bottom-6 right-6 z-[9999] flex flex-col gap-2 pointer-events-none sm:right-6 sm:left-auto left-1/2 sm:translate-x-0 -translate-x-1/2"
        >
          {toasts.map((toast) => (
            <div key={toast.id} className="pointer-events-auto">
              <ToastItem toast={toast} onDismiss={dismiss} duration={toast.duration} />
            </div>
          ))}
        </div>
      )}
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}
