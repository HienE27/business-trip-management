"use client";

import { useCallback } from "react";
import type { FieldMetadata } from "@/features/config/types/ConfigMetadata";

interface ChipGroupFieldProps {
  metadata: FieldMetadata;
  value: string[] | null;
  onChange: (value: string[]) => void;
  error?: string;
  disabled?: boolean;
}

export function ChipGroupField({ metadata, value, onChange, error, disabled }: ChipGroupFieldProps) {
  const selected = value ?? [];
  const options = metadata.allowedValues ?? [];
  const isInvalid = !!error;

  const toggle = useCallback(
    (chipValue: string) => {
      if (selected.includes(chipValue)) {
        onChange(selected.filter((v) => v !== chipValue));
      } else {
        onChange([...selected, chipValue]);
      }
    },
    [selected, onChange]
  );

  return (
    <div className="flex flex-col gap-2">
      {/* Chip group */}
      <div className="flex flex-wrap gap-2">
        {options.map((opt) => {
          const isSelected = selected.includes(opt.value);
          return (
            <button
              key={opt.value}
              type="button"
              disabled={disabled}
              onClick={() => toggle(opt.value)}
              className={`
                px-3 py-1.5 rounded-full text-label-sm font-semibold border transition-all
                focus:outline-none focus:ring-2 focus:ring-primary/20
                disabled:opacity-50 disabled:cursor-not-allowed
                ${isSelected
                  ? "bg-primary text-on-primary border-primary"
                  : "bg-surface-container-lowest text-on-surface border-outline-variant hover:border-primary hover:bg-primary-fixed"
                }
              `}
            >
              {opt.label}
            </button>
          );
        })}
      </div>

      {/* Selected count */}
      {selected.length > 0 && (
        <p className="text-[12px] text-on-surface-variant">
          Đã chọn: {selected.length} / {options.length}
        </p>
      )}

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
