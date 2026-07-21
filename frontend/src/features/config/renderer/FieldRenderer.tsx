"use client";

import { useCallback, useMemo } from "react";
import type { FieldMetadata } from "@/features/config/types/ConfigMetadata";
import { NumberField } from "./NumberField";
import { PercentageField } from "./PercentageField";
import { ToggleField } from "./ToggleField";
import { SelectField } from "./SelectField";
import { ChipGroupField } from "./ChipGroupField";

interface FieldRendererProps {
  metadata: FieldMetadata;
  value: unknown;
  onChange: (value: unknown) => void;
  error?: string;
  disabled?: boolean;
}

/**
 * Dynamic field renderer.
 * Dispatches to the correct component based on metadata.renderType.
 *
 * Supported render types:
 * - "number": integer/decimal input with stepper
 * - "slider": range slider for percentages
 * - "toggle": boolean switch
 * - "select": single-choice dropdown
 * - "chip_group": multi-choice chip buttons
 */
export function FieldRenderer({
  metadata,
  value,
  onChange,
  error,
  disabled,
}: FieldRendererProps) {
  const handleChange = useCallback(
    (newValue: unknown) => {
      onChange(newValue);
    },
    [onChange]
  );

  const commonProps = useMemo(
    () => ({ metadata, error, disabled }),
    [metadata, error, disabled]
  );

  switch (metadata.renderType) {
    case "number":
      return (
        <NumberField
          {...commonProps}
          value={value as number | null}
          onChange={handleChange as (v: number) => void}
        />
      );

    case "slider":
      return (
        <PercentageField
          {...commonProps}
          value={value as number | null}
          onChange={handleChange as (v: number) => void}
        />
      );

    case "toggle":
      return (
        <ToggleField
          {...commonProps}
          value={value as boolean | null}
          onChange={handleChange as (v: boolean) => void}
        />
      );

    case "select":
      return (
        <SelectField
          {...commonProps}
          value={value as string | null}
          onChange={handleChange as (v: string) => void}
        />
      );

    case "chip_group":
      return (
        <ChipGroupField
          {...commonProps}
          value={value as string[] | null}
          onChange={handleChange as (v: string[]) => void}
        />
      );

    default:
      return (
        <div className="p-3 bg-error-container text-on-error-container text-label-sm rounded-lg">
          <span className="material-symbols-outlined text-[16px] mr-1">error</span>
          Unknown renderType: <code>{metadata.renderType}</code>
        </div>
      );
  }
}
