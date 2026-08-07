"use client";

import { useEffect, useRef, type ReactNode } from "react";

type ModalProps = {
  open: boolean;
  onClose?: () => void;
  title: string;
  description?: string;
  /**
   * Optional leading icon element rendered inside the header next to the
   * title block. Used by dialogs that previously had a custom header with
   * an icon badge (e.g. Create Config, Config Diff). The icon is wrapped
   * in a circular badge matching the design system surface tokens.
   *
   * Example:
   *   <Modal icon={<span className="material-symbols-outlined">add</span>} ... />
   */
  icon?: ReactNode;
  /** Background class for the icon badge. Defaults to primary-fixed. */
  iconClassName?: string;
  children: ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
};

const SIZE_CLASS = {
  sm: "max-w-sm",
  md: "max-w-lg",
  lg: "max-w-2xl",
  xl: "max-w-4xl",
};

export function Modal({ open, onClose, title, description, children, size = "md", icon, iconClassName }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<Element | null>(null);

  // Focus trap and restore
  useEffect(() => {
    if (open) {
      previousActiveElement.current = document.activeElement;
      const firstFocusable = dialogRef.current?.querySelector<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      );
      firstFocusable?.focus();
    } else {
      if (previousActiveElement.current instanceof HTMLElement) {
        previousActiveElement.current.focus();
      }
    }
  }, [open]);

  // Close on Escape
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && onClose) onClose();
      // Focus trap
      if (e.key === "Tab" && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    if (open) {
      document.addEventListener("keydown", handleKey);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", handleKey);
      document.body.style.overflow = "";
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 overscroll-contain"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      aria-describedby={description ? "modal-description" : undefined}
    >
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-[var(--color-surface-dim)]/70 backdrop-blur-md"
        onClick={onClose ? () => onClose() : undefined}
        aria-hidden="true"
      />

      {/* Dialog */}
      <div
        ref={dialogRef}
        className={`relative w-full ${SIZE_CLASS[size]} bg-surface-container-lowest border border-outline-variant rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200 ease-out`}
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-outline-variant flex items-start justify-between gap-4">
          <div className="flex items-start gap-3 min-w-0">
            {icon && (
              <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${iconClassName ?? "bg-primary-fixed text-primary"}`}>
                {icon}
              </div>
            )}
            <div className="min-w-0">
              <h2 id="modal-title" className="text-title-lg text-on-surface">
                {title}
              </h2>
              {description && (
                <p id="modal-description" className="mt-1 text-label-sm text-on-surface-variant">{description}</p>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={onClose ? () => onClose() : undefined}
            className="p-1.5 rounded-lg hover:bg-surface-container-high transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary shrink-0 cursor-pointer"
            aria-label="Đóng"
            title="Đóng"
          >
            <span className="material-symbols-outlined text-[20px] text-on-surface-variant">close</span>
          </button>
        </div>

        {/* Body */}
        <div className="p-4 sm:p-6 max-h-[70vh] overflow-y-auto">{children}</div>
      </div>
    </div>
  );
}

export function ModalFooter({ children }: { children: ReactNode }) {
  return (
    <div className="px-6 py-4 border-t border-outline-variant flex items-center justify-end gap-3 bg-surface-container-low">
      {children}
    </div>
  );
}
