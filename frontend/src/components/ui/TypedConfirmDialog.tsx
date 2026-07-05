"use client";

import { useEffect, useRef, useState } from "react";
import { Modal } from "./Modal";
import { Button } from "./Button";

/* ── TypedConfirmDialog ──
 *
 * Confirmation dialog that requires the user to TYPE a specific phrase
 * (usually the resource name or "DELETE") before the confirm button is enabled.
 *
 * Use this for high-stakes destructive operations where a slip of the
 * finger has irreversible consequences (e.g. permanently deleting a period,
 * bulk-deleting a season, wiping algorithm history).
 *
 * - Tracks what the user typed locally; never persists it.
 * - Compare is case-insensitive and trims whitespace.
 * - Loading state disables the confirm button while the action is in flight.
 */

type TypedConfirmDialogProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description?: string;
  /** The exact phrase the user must type. Compare is case-insensitive. */
  confirmPhrase: string;
  confirmLabel?: string;
  cancelLabel?: string;
  loading?: boolean;
};

export function TypedConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
  confirmPhrase,
  confirmLabel = "Xác nhận xóa",
  cancelLabel = "Hủy",
  loading = false,
}: TypedConfirmDialogProps) {
  const [typed, setTyped] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  // Reset input when dialog opens/closes, and focus the input on open.
  useEffect(() => {
    if (open) {
      setTyped("");
      const timer = requestAnimationFrame(() => inputRef.current?.focus());
      return () => cancelAnimationFrame(timer);
    }
  }, [open]);

  const normalized = typed.trim().toLowerCase();
  const expected = confirmPhrase.trim().toLowerCase();
  const canConfirm = normalized.length > 0 && normalized === expected && !loading;

  return (
    <Modal open={open} onClose={onClose} title={title} description={description} size="sm">
      <div className="pt-4 space-y-4">
        <div>
          <label
            htmlFor="typed-confirm-input"
            className="block text-label-sm font-semibold text-on-surface mb-1.5"
          >
            Nhập <span className="font-mono px-1.5 py-0.5 bg-surface-container-low rounded text-on-surface">{confirmPhrase}</span> để xác nhận
          </label>
          <input
            ref={inputRef}
            id="typed-confirm-input"
            type="text"
            value={typed}
            onChange={(event) => setTyped(event.target.value)}
            disabled={loading}
            autoComplete="off"
            spellCheck={false}
            aria-describedby="typed-confirm-hint"
            className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface rounded-lg transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
          />
          <p id="typed-confirm-hint" className="mt-1 text-label-xs text-on-surface-variant">
            Hành động này không thể hoàn tác.
          </p>
        </div>

        <div className="flex items-center justify-end gap-3 pt-2">
          <Button
            variant="secondary"
            size="lg"
            onClick={onClose}
            disabled={loading}
            className="min-w-[100px]"
          >
            {cancelLabel}
          </Button>
          <Button
            variant="danger"
            size="lg"
            onClick={onConfirm}
            loading={loading}
            disabled={!canConfirm}
            className="min-w-[120px] px-6"
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
