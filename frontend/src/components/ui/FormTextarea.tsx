"use client";

import type { TextareaHTMLAttributes } from "react";

/* ── FormTextarea ──
 *
 * Accessible textarea with:
 * - Per-field error state
 * - Character count (optional)
 * - Required field indicator
 */

type FormTextareaProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "rows"> & {
  label?: string;
  error?: string;
  hint?: string;
  hideLabel?: boolean;
  rows?: number;
  showCount?: boolean;
  maxLength?: number;
};

export function FormTextarea({
  label,
  error,
  hint,
  hideLabel,
  rows = 3,
  showCount,
  maxLength,
  id,
  className = "",
  disabled,
  required,
  value,
  ...rest
}: FormTextareaProps) {
  const inputId = id ?? `textarea-${Math.random().toString(36).slice(2)}`;
  const errorId = error ? `${inputId}-error` : undefined;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const charCount = typeof value === "string" ? value.length : 0;

  const borderColor = error
    ? "border-error focus:border-error focus:ring-error/20"
    : "border-outline-variant focus:border-primary focus:ring-primary/20";

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label
          htmlFor={inputId}
          className={`text-body-sm font-semibold text-on-surface ${hideLabel ? "sr-only" : ""}`}
        >
          {label}
          {required && <span className="text-error ml-0.5" aria-hidden="true">*</span>}
        </label>
      )}

      <textarea
        {...rest}
        id={inputId}
        rows={rows}
        disabled={disabled}
        required={required}
        maxLength={maxLength}
        value={value}
        aria-invalid={error ? "true" : undefined}
        aria-describedby={[errorId, hintId].filter(Boolean).join(" ") || undefined}
        className={[
          "w-full p-3 bg-surface-container-low text-body-md text-on-surface",
          "rounded-lg border transition-all outline-none resize-none",
          "placeholder:text-outline",
          borderColor,
          disabled ? "bg-surface-container-low cursor-not-allowed opacity-60" : "",
          className,
        ].join(" ")}
      />

      <div className="flex items-center justify-between">
        {error ? (
          <p id={errorId} className="flex items-center gap-1 text-label-sm text-error" role="alert">
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">error</span>
            {error}
          </p>
        ) : hint ? (
          <p id={hintId} className="text-label-sm text-on-surface-variant">{hint}</p>
        ) : (
          <span />
        )}

        {showCount && maxLength && (
          <span
            className={`text-label-sm ${charCount >= maxLength ? "text-error" : "text-on-surface-variant"}`}
            aria-live="polite"
          >
            {charCount}/{maxLength}
          </span>
        )}
      </div>
    </div>
  );
}
