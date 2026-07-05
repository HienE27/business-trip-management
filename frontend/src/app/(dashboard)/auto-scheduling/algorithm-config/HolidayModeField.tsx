"use client";

type Props = {
  desc: { label: string; desc: string; hint: string };
  value: string;
  editing: boolean;
  onChange: (value: string) => void;
};

const OPTIONS = [
  { value: "SKIP", label: "SKIP — Bỏ qua" },
  { value: "PARTIAL", label: "PARTIAL — Giảm" },
];

export function HolidayModeField({ desc, value, editing, onChange }: Props) {
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex-1 min-w-0">
        <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{desc.label}</code>
        <p className="text-[11px] text-on-surface-variant mt-1 leading-relaxed">{desc.desc}</p>
        <p className="text-[10px] text-outline mt-0.5">{desc.hint}</p>
      </div>
      {editing ? (
        <select
          aria-label={desc.label}
          className="h-9 w-28 rounded-xl border border-outline-variant bg-surface-container-low px-2.5 text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
          value={value}
          onChange={(e) => onChange(e.target.value)}
        >
          {OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      ) : (
        <span className={`px-3 py-1 rounded-full text-label-sm font-semibold border ${value === "SKIP" ? "bg-teal-50 text-teal-700 border-teal-200" : "bg-amber-50 text-amber-700 border-amber-200"}`}>
          {value}
        </span>
      )}
    </div>
  );
}