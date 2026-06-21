"use client";

import { useEffect, useRef, type RefObject } from "react";
import { Modal } from "./Modal";
import { Button } from "./Button";

/* ── ConfirmDialog ──
 *
 * Accessible confirmation dialog for destructive or irreversible actions.
 * - Focus trapped on open, returned on close
 * - Default focus on cancel button (safe choice)
 * - Escape key closes (only if closeOnBackdrop=true)
 * - Backdrop click closes (only if closeOnBackdrop=true)
 */

type ConfirmDialogProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "danger" | "primary";
  loading?: boolean;
  closeOnBackdrop?: boolean;
};

export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel = "Xác nhận",
  cancelLabel = "Hủy",
  variant = "danger",
  loading = false,
  closeOnBackdrop = true,
}: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      const timer = requestAnimationFrame(() => {
        cancelRef.current?.focus();
      });
      return () => cancelAnimationFrame(timer);
    }
  }, [open]);

  return (
    <Modal
      open={open}
      onClose={closeOnBackdrop ? onClose : undefined}
      title={title}
      description={description}
      size="sm"
    >

      <div className="flex items-center justify-end gap-2 pt-5">
        <Button ref={cancelRef} variant="secondary" onClick={onClose} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button
          variant={variant === "danger" ? "danger" : "primary"}
          onClick={onConfirm}
          loading={loading}
        >
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
