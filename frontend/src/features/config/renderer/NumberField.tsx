"use client";

import { useCallback, useState } from "react";
import type { FieldMetadata } from "@/features/config/types/ConfigMetadata";

interface NumberFieldProps {
  metadata: FieldMetadata;
  value: number | null;
  onChange: (value: number) => void;
  error?: string;
  disabled?: boolean;
}

export function NumberField({ metadata, value, onChange, error, disabled }: NumberFieldProps) {
  const [localValue, setLocalValue] = useState<string>(
    value !== null && value !== undefined ? String(value) : ""
  );
  const [showError, setShowError] = useState(false);

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const raw = e.target.value;
      setLocalValue(raw);
      setShowError(false);

      if (raw === "") {
        onChange(0);
        return;
      }

      const num = parseFloat(raw);
      if (isNaN(num)) return;

      // Clamp to bounds
      const clamped = Math.max(metadata.min, Math.min(metadata.max, num));
      onChange(clamped);
    },
    [metadata.min, metadata.max, onChange]
  );

  const handleBlur = useCallback(() => {
    setShowError(true);
    // Normalize on blur
    const num = parseFloat(localValue);
    if (!isNaN(num)) {
      const clamped = Math.max(metadata.min, Math.min(metadata.max, num));
      setLocalValue(String(clamped));
    }
  }, [localValue, metadata.min, metadata.max]);

  const handleStep = useCallback(
    (delta: number) => {
      const current = parseFloat(localValue) || 0;
      const step = metadata.step || 1;
      const newVal = Math.max(metadata.min, Math.min(metadata.max, current + delta * step));
      setLocalValue(String(newVal));
      onChange(newVal);
    },
    [localValue, metadata.min, metadata.max, metadata.step, onChange]
  );

  const isInvalid = showError && !!error;
  const step = metadata.step || 1;

  return (
    <div className="flex flex-col gap-1">
      <div className="relative flex items-center">
        <input
          type="number"
          value={localValue}
          onChange={handleChange}
          onBlur={handleBlur}
          disabled={disabled}
          min={metadata.min}
          max={metadata.max}
          step={step}
          className={`
            w-full h-10 px-3 pr-12 border rounded-lg text-body-md transition-all
            bg-surface-container-lowest
            focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary
            disabled:opacity-50 disabled:cursor-not-allowed
            ${isInvalid ? "border-error focus:border-error focus:ring-error/20" : "border-outline-variant"}
          `}
          placeholder={metadata.required ? "Bắt buộc" : ""}
        />

        {/* Stepper buttons */}
        <div className="absolute right-1 flex flex-col">
          <button
            type="button"
            onClick={() => handleStep(1)}
            disabled={disabled || (parseFloat(localValue) || 0) >= metadata.max}
            className="w-8 h-5 flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container-low rounded-t-md transition-colors disabled:opacity-30"
            aria-label="Tăng"
          >
            <span className="material-symbols-outlined text-[14px]">expand_less</span>
          </button>
          <button
            type="button"
            onClick={() => handleStep(-1)}
            disabled={disabled || (parseFloat(localValue) || 0) <= metadata.min}
            className="w-8 h-5 flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container-low rounded-b-md transition-colors disabled:opacity-30"
            aria-label="Giảm"
          >
            <span className="material-symbols-outlined text-[14px]">expand_more</span>
          </button>
        </div>
      </div>

      {isInvalid && (
        <p className="text-[12px] text-red-800 flex items-center gap-1" role="alert">
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
