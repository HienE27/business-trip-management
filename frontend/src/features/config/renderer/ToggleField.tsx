"use client";

import type { FieldMetadata } from "@/features/config/types/ConfigMetadata";

interface ToggleFieldProps {
  metadata: FieldMetadata;
  value: boolean | null;
  onChange: (value: boolean) => void;
  error?: string;
  disabled?: boolean;
}

export function ToggleField({ metadata, value, onChange, error, disabled }: ToggleFieldProps) {
  const isChecked = value === true;
  const isInvalid = !!error;

  return (
    <div className="flex items-start gap-3">
      {/* Toggle switch */}
      <button
        type="button"
        role="switch"
        aria-checked={isChecked}
        disabled={disabled}
        onClick={() => onChange(!isChecked)}
        className={`
          relative inline-block w-11 h-6 rounded-full transition-colors
          focus:outline-none focus:ring-2 focus:ring-blue-30020 focus:ring-offset-2
          disabled:opacity-50 disabled:cursor-not-allowed
          ${isChecked ? "bg-blue-100" : "bg-surface-variant"}
        `}
      >
        <span
          className={`
            absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow-sm
            transition-transform
            ${isChecked ? "translate-x-5" : "translate-x-0"}
          `}
        />
      </button>

      {/* Label + description */}
      <div className="flex flex-col gap-0.5 flex-1">
        <span className="text-label-md text-on-surface">{metadata.label}</span>
        {metadata.description && (
          <span className="text-[12px] text-on-surface-variant">{metadata.description}</span>
        )}
        {isInvalid && (
          <p className="text-[12px] text-red-800 flex items-center gap-1 mt-1" role="alert">
            <span className="material-symbols-outlined text-[12px]">error</span>
            {error}
          </p>
        )}
      </div>
    </div>
  );
}
