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

export type ToastType = "success" | "error" | "info";

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

type ToastContextType = {
  toasts: Toast[];
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
  dismiss: (id: string) => void;
};

const ToastContext = createContext<ToastContextType | null>(null);

function generateId() {
  return Math.random().toString(36).slice(2, 9);
}

function ToastItem({
  toast,
  onDismiss,
}: {
  toast: Toast;
  onDismiss: (id: string) => void;
}) {
  const [visible, setVisible] = useState(false);
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    const appearTimer = setTimeout(() => setVisible(true), 10);
    const dismissTimer = setTimeout(() => {
      setExiting(true);
      setTimeout(() => onDismiss(toast.id), 300);
    }, 3000);
    return () => {
      clearTimeout(appearTimer);
      clearTimeout(dismissTimer);
    };
  }, [toast.id, onDismiss]);

  const typeStyles = {
    success: {
      container: "border-secondary",
      icon: "check_circle",
      iconBg: "bg-secondary-container",
      iconColor: "text-secondary",
    },
    error: {
      container: "border-error",
      icon: "error",
      iconBg: "bg-error-container",
      iconColor: "text-error",
    },
    info: {
      container: "border-primary",
      icon: "info",
      iconBg: "bg-primary-fixed",
      iconColor: "text-primary",
    },
  };

  const style = typeStyles[toast.type];

  return (
    <div
      className={cn(
        "flex items-center gap-3 bg-surface-container-lowest border border-outline-variant rounded-xl px-4 py-3 shadow-lg",
        "min-w-72 max-w-96",
        "transition-all duration-300 ease-out",
        visible && !exiting
          ? "opacity-100 translate-x-0"
          : "opacity-0 translate-x-8"
      )}
    >
      <div
        className={cn(
          "w-8 h-8 rounded-full flex items-center justify-center shrink-0",
          style.iconBg
        )}
      >
        <span
          className={cn("material-symbols-outlined text-[18px]", style.iconColor)}
          style={{ fontVariationSettings: "'FILL' 1" }}
        >
          {style.icon}
        </span>
      </div>
      <p className="text-body-sm text-on-surface flex-1 leading-tight">
        {toast.message}
      </p>
      <button
        onClick={() => {
          setExiting(true);
          setTimeout(() => onDismiss(toast.id), 300);
        }}
        className={cn(
          "w-6 h-6 flex items-center justify-center rounded-full",
          "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface",
          "transition-colors duration-150 cursor-pointer"
        )}
        aria-label="Dismiss notification"
      >
        <span className="material-symbols-outlined text-[16px]">close</span>
      </button>
    </div>
  );
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
  }, []);

  const addToast = useCallback((message: string, type: ToastType) => {
    const id = generateId();
    setToasts((prev) => [...prev, { id, message, type }]);
  }, []);

  const success = useCallback(
    (message: string) => addToast(message, "success"),
    [addToast]
  );

  const error = useCallback(
    (message: string) => addToast(message, "error"),
    [addToast]
  );

  const info = useCallback(
    (message: string) => addToast(message, "info"),
    [addToast]
  );

  return (
    <ToastContext.Provider value={{ toasts, success, error, info, dismiss }}>
      {children}

      <div
        aria-live="polite"
        aria-label="Notifications"
        className="fixed bottom-6 right-6 z-[9999] flex flex-col gap-2 pointer-events-none"
      >
        {toasts.map((toast) => (
          <div key={toast.id} className="pointer-events-auto">
            <ToastItem toast={toast} onDismiss={dismiss} />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used inside ToastProvider");
  }
  return ctx;
}
