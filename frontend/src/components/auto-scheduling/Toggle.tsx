"use client";

type ToggleProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  id?: string;
};

export function Toggle({ checked, onChange, disabled, id }: ToggleProps) {
  return (
    <label className="relative inline-flex items-center cursor-pointer">
      <input
        checked={checked}
        className="sr-only peer"
        disabled={disabled}
        id={id}
        type="checkbox"
        onChange={(e) => onChange(e.target.checked)}
      />
      <div className="w-9 h-5 bg-outline-variant rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-primary-container" />
    </label>
  );
}
