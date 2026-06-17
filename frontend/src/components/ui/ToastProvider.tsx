"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
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

function createId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: (id: string) => void }) {
  const [visible, setVisible] = useState(false);

  // Stable refs so timer callbacks never go stale
  const onDismissRef = useRef(onDismiss);
  const toastIdRef = useRef(toast.id);

  // Keep refs current — these run on every render
  onDismissRef.current = onDismiss;
  toastIdRef.current = toast.id;

  useEffect(() => {
    // Fade in immediately
    setVisible(true);
    return () => setVisible(false);
  }, []);

  useEffect(() => {
    // Auto-dismiss after 3 seconds
    const timer = setTimeout(() => {
      onDismissRef.current(toastIdRef.current);
    }, 3000);
    return () => clearTimeout(timer);
  }, []); // empty — set up once on mount

  const handleClose = () => onDismissRef.current(toastIdRef.current);

  const icons = { success: "check_circle", error: "error", info: "info" };
  const iconBg = { success: "bg-secondary-container", error: "bg-error-container", info: "bg-primary-fixed" };
  const iconColor = { success: "text-secondary", error: "text-error", info: "text-primary" };

  return (
    <div
      className={cn(
        "flex items-center gap-3 bg-surface-container-lowest border border-outline-variant rounded-xl px-4 py-3 shadow-lg min-w-72 max-w-96",
        "transition-all duration-300 ease-out",
        visible ? "opacity-100 translate-x-0" : "opacity-0 translate-x-8"
      )}
    >
      <div className={cn("w-8 h-8 rounded-full flex items-center justify-center shrink-0", iconBg[toast.type])}>
        <span className={cn("material-symbols-outlined text-[18px]", iconColor[toast.type])} style={{ fontVariationSettings: "'FILL' 1" }}>
          {icons[toast.type]}
        </span>
      </div>
      <p className="text-body-sm text-on-surface flex-1 leading-tight">{toast.message}</p>
      <button onClick={handleClose} className="w-6 h-6 flex items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface transition-colors cursor-pointer" aria-label="Dismiss">
        <span className="material-symbols-outlined text-[16px]">close</span>
      </button>
    </div>
  );
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  // Stable identity — no useCallback, no deps change
  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback((message: string, type: ToastType) => {
    const id = createId();
    setToasts((prev) => [...prev, { id, message, type }]);
  }, []);

  const success = useCallback((message: string) => addToast(message, "success"), [addToast]);
  const error = useCallback((message: string) => addToast(message, "error"), [addToast]);
  const info = useCallback((message: string) => addToast(message, "info"), [addToast]);

  return (
    <ToastContext.Provider value={{ toasts, success, error, info, dismiss }}>
      {children}
      <div aria-live="polite" className="fixed bottom-6 right-6 z-[9999] flex flex-col gap-2 pointer-events-none">
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
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}
