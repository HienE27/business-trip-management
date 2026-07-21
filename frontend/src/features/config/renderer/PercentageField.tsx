"use client";

import type { FieldMetadata } from "@/features/config/types/ConfigMetadata";

interface PercentageFieldProps {
  metadata: FieldMetadata;
  value: number | null;
  onChange: (value: number) => void;
  error?: string;
  disabled?: boolean;
}

export function PercentageField({ metadata, value, onChange, error, disabled }: PercentageFieldProps) {
  const percentage = value !== null && value !== undefined ? value * 100 : 0;
  const min = metadata.min * 100;
  const max = metadata.max * 100;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const pct = parseFloat(e.target.value);
    if (!isNaN(pct)) {
      const normalized = Math.max(min, Math.min(max, pct)) / 100;
      onChange(Math.round(normalized * 100) / 100);
    }
  };

  const isInvalid = !!error;

  return (
    <div className="flex flex-col gap-2">
      {/* Slider + percentage display */}
      <div className="flex items-center gap-3">
        <input
          type="range"
          value={percentage}
          onChange={handleChange}
          disabled={disabled}
          min={min}
          max={max}
          step={(metadata.step * 100) || 1}
          className="flex-1 h-2 bg-surface-variant rounded-full appearance-none cursor-pointer
            [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4
            [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:bg-primary
            [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:cursor-pointer
            disabled:[&::-webkit-slider-thumb]:opacity-50"
        />
        <span className="w-14 text-right font-label-md text-on-surface tabular-nums">
          {percentage.toFixed(metadata.step < 0.1 ? 1 : 0)}%
        </span>
      </div>

      {/* Min/Max labels */}
      <div className="flex justify-between">
        <span className="text-[11px] text-on-surface-variant">{min.toFixed(0)}%</span>
        <span className="text-[11px] text-on-surface-variant">{max.toFixed(0)}%</span>
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
