"use client";

import type { SelectHTMLAttributes } from "react";

/* ── FormSelect ──
 *
 * Accessible select dropdown with:
 * - Custom chevron icon (Material Symbols)
 * - Per-field error state
 * - Required field indicator
 * - Disabled state
 *
 * Design tokens: same as FormInput
 */

type FormSelectProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, "size"> & {
  label?: string;
  error?: string;
  hint?: string;
  options: Array<{ value: string; label: string; disabled?: boolean }>;
  placeholder?: string;
  hideLabel?: boolean;
  flush?: boolean;
};

export function FormSelect({
  label,
  error,
  hint,
  options,
  placeholder,
  hideLabel,
  flush,
  id,
  className = "",
  disabled,
  required,
  value,
  ...rest
}: FormSelectProps) {
  const inputId = id ?? `select-${Math.random().toString(36).slice(2)}`;
  const errorId = error ? `${inputId}-error` : undefined;
  const hintId = hint ? `${inputId}-hint` : undefined;

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

      <div className="relative">
        <select
          {...rest}
          id={inputId}
          disabled={disabled}
          required={required}
          value={value}
          aria-invalid={error ? "true" : undefined}
          aria-describedby={[errorId, hintId].filter(Boolean).join(" ") || undefined}
          className={[
            "w-full h-10 pl-3 pr-10 bg-surface-container-low text-body-md text-on-surface",
            "rounded-lg border transition-all outline-none appearance-none cursor-pointer",
            borderColor,
            flush ? "rounded-none border-x-0" : "",
            disabled
              ? "bg-surface-container-low cursor-not-allowed opacity-60"
              : "",
            !value && placeholder ? "text-outline" : "",
            className,
          ].join(" ")}
          style={flush ? { borderRadius: 0 } : undefined}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value} disabled={opt.disabled}>
              {opt.label}
            </option>
          ))}
        </select>

        <span
          aria-hidden="true"
          className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none"
        >
          expand_more
        </span>
      </div>

      {error && (
        <p id={errorId} className="flex items-center gap-1 text-label-sm text-error" role="alert">
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
