"use client";

type ToggleProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  id?: string;
  label?: string;
};

export function Toggle({ checked, onChange, disabled, id, label }: ToggleProps) {
  return (
    <label className="relative inline-flex items-center cursor-pointer focus-within:ring-2 focus-within:ring-primary focus-within:ring-offset-1 rounded-full">
      <input
        checked={checked}
        className="peer sr-only"
        disabled={disabled}
        id={id}
        type="checkbox"
        aria-label={label}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span
        aria-hidden="true"
        className="block w-9 h-5 bg-outline-variant rounded-full peer-checked:bg-blue-100-container transition-colors"
      />
      <span
        aria-hidden="true"
        className="absolute top-[2px] left-[2px] block h-4 w-4 rounded-full bg-[var(--color-surface-container-lowest)] border border-[var(--color-outline-variant)] shadow-sm transition-transform duration-200 peer-checked:translate-x-4"
      />
    </label>
  );
}