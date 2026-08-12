"use client";

import type { InputHTMLAttributes } from "react";

/* ── FormInput ──
 *
 * Accessible text input with:
 * - Optional leading icon (Material Symbols)
 * - Optional trailing action button (e.g. password toggle, clear)
 * - Per-field error state (red border + error message below)
 * - Required field indicator
 * - Disabled state styling
 *
 * Design tokens:
 *   border: border-outline-variant → border-error (error)
 *   bg: bg-surface-container-lowest (default) → bg-surface-container-low (disabled)
 *   focus: focus:border-primary focus:ring-2 focus:ring-primary/20
 */

type FormInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "size"> & {
  label?: string;
  error?: string;
  hint?: string;
  icon?: string;          // Material Symbols name for leading icon
  trailingAction?: React.ReactNode;   // e.g. password toggle, clear button
  /** Make the label visually hidden (sr-only) while keeping it accessible */
  hideLabel?: boolean;
  /** Remove border-radius for inputs inside table cells */
  flush?: boolean;
};

import { useId } from "react";

export function FormInput({
  label,
  error,
  hint,
  icon,
  trailingAction,
  hideLabel,
  flush,
  id,
  className = "",
  disabled,
  required,
  ...rest
}: FormInputProps) {
  const uid = useId();
  const inputId = id ?? `input-${uid}`;
  const errorId = error ? `${inputId}-error` : undefined;
  const hintId = hint ? `${inputId}-hint` : undefined;

  const borderColor = error
    ? "border-error focus:border-error focus:ring-error/30 focus:shadow-sm"
    : "border-outline-variant focus:border-primary focus:ring-primary/30 focus:shadow-sm";

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label
          htmlFor={inputId}
          className={`text-body-sm font-semibold text-on-surface ${hideLabel ? "sr-only" : ""}`}
        >
          {label}
          {required && <span className="text-red-800 ml-0.5" aria-hidden="true">*</span>}
        </label>
      )}

      <div className="relative">
        {icon && (
          <span
            aria-hidden="true"
            className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none"
          >
            {icon}
          </span>
        )}

        <input
          {...rest}
          id={inputId}
          disabled={disabled}
          required={required}
          aria-invalid={error ? "true" : undefined}
          aria-describedby={[errorId, hintId].filter(Boolean).join(" ") || undefined}
          className={[
            "w-full h-10 px-3 bg-surface-container-low text-body-md text-on-surface",
            "rounded-lg border transition-all outline-none",
            "placeholder:text-outline",
            borderColor,
            icon ? "pl-10" : "",
            trailingAction ? "pr-10" : "",
            flush ? "rounded-none border-x-0" : "",
            disabled
              ? "bg-surface-container-low cursor-not-allowed opacity-60"
              : "",
            className,
          ].join(" ")}
          style={flush ? { borderRadius: 0 } : undefined}
        />

        {trailingAction && (
          <div className="absolute right-2 top-1/2 -translate-y-1/2">
            {trailingAction}
          </div>
        )}
      </div>

      {error && (
        <p id={errorId} className="flex items-center gap-1 text-label-sm text-red-800" role="alert">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">error</span>
          {error}
        </p>
      )}

      {hint && !error && (
        <p id={hintId} className="text-label-sm text-on-surface-variant">
          {hint}
        </p>
      )}
    </div>
  );
}
