"use client";

import type { FieldMetadata } from "@/features/config/types/ConfigMetadata";

interface SelectFieldProps {
  metadata: FieldMetadata;
  value: string | null;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}

export function SelectField({ metadata, value, onChange, error, disabled }: SelectFieldProps) {
  const isInvalid = !!error;
  const options = metadata.allowedValues ?? [];

  return (
    <div className="flex flex-col gap-1">
      <div className="relative">
        <select
          value={value ?? ""}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className={`
            w-full h-10 pl-3 pr-10 border rounded-lg text-body-md transition-all appearance-none
            bg-surface-container-lowest
            focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary
            disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer
            ${isInvalid ? "border-error focus:border-error focus:ring-error/20" : "border-outline-variant"}
          `}
        >
          {!metadata.required && (
            <option value="">— Chọn —</option>
          )}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        <span className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-outline text-[20px]">
          <span className="material-symbols-outlined">expand_more</span>
        </span>
      </div>

      {isInvalid && (
        <p className="text-[12px] text-error flex items-center gap-1" role="alert">
          <span className="material-symbols-outlined text-[12px]">error</span>
          {error}
        </p>
      )}

      {metadata.description && (
        <p className="text-[12px] text-on-surface-variant">{metadata.description}</p>
      )}
    </div>
  );
}
