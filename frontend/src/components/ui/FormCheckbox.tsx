"use client";

import type { InputHTMLAttributes } from "react";

/* ── FormCheckbox ──
 *
 * Accessible checkbox with:
 * - Custom styled (uses accent-color for native feel)
 * - Accessible label association
 * - Error state
 */

type FormCheckboxProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
  label: string;
  description?: string;
  error?: string;
};

export function FormCheckbox({
  label,
  description,
  error,
  id,
  className = "",
  disabled,
  ...rest
}: FormCheckboxProps) {
  const inputId = id ?? `checkbox-${Math.random().toString(36).slice(2)}`;

  return (
    <div className="flex items-start gap-2.5">
      <input
        {...rest}
        id={inputId}
        type="checkbox"
        disabled={disabled}
        aria-invalid={error ? "true" : undefined}
        className={[
          "mt-0.5 h-4 w-4 shrink-0 cursor-pointer rounded border border-outline accent-[var(--color-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
          disabled ? "cursor-not-allowed opacity-50" : "",
          error ? "border-error" : "",
          className,
        ].join(" ")}
      />
      <div className="flex flex-col gap-0.5">
        <label
          htmlFor={inputId}
          className={`text-body-sm cursor-pointer ${disabled ? "opacity-60 cursor-not-allowed" : ""}`}
        >
          {label}
        </label>
        {description && (
          <p className="text-label-sm text-on-surface-variant">{description}</p>
        )}
        {error && (
          <p className="flex items-center gap-1 text-label-sm text-error" role="alert">
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">error</span>
            {error}
          </p>
        )}
      </div>
    </div>
  );
}
