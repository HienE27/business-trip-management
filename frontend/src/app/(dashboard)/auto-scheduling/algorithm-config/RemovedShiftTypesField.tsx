"use client";

type Props = {
  desc: { label: string; desc: string; hint: string };
  current: string[];
  editing: boolean;
  onChange: (value: string[]) => void;
};

const ALL_TYPES = ["L01", "L02", "L03", "L04"] as const;

export function RemovedShiftTypesField({ desc, current, editing, onChange }: Props) {
  function toggle(code: string) {
    onChange(current.includes(code) ? current.filter(c => c !== code) : [...current, code]);
  }

  return (
    <div className="flex items-start justify-between gap-3">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <code className="font-mono text-[12px] font-semibold text-blue-800 bg-blue-100 text-blue-800 px-1.5 py-0.5 rounded">{desc.label}</code>
          <span className="material-symbols-outlined text-[14px] text-on-surface-variant/60 hover:text-blue-800 transition-colors cursor-help" aria-hidden="true">info</span>
        </div>
        <p className="text-[12px] text-on-surface-variant mt-1 leading-relaxed">{desc.desc}</p>
        <p className="text-[11px] text-outline mt-0.5">{desc.hint}</p>
      </div>
      {editing ? (
        <div className="flex flex-wrap gap-1.5 justify-end max-w-[60%]">
          {ALL_TYPES.map(code => {
            const active = current.includes(code);
            return (
              <button
                key={code}
                type="button"
                onClick={() => toggle(code)}
                aria-pressed={active}
                className={`px-2.5 py-1 rounded-full text-label-sm font-semibold border transition-all cursor-pointer ${
                  active
                    ? "bg-red-100 text-red-800 border border-red-300 border-error/30 hover:bg-red-100 text-red-800"
                    : "bg-surface-container-low text-on-surface-variant border-outline-variant hover:border-primary hover:text-blue-800"
                }`}
              >
                {active && <span className="material-symbols-outlined text-[12px] mr-0.5 align-middle" aria-hidden="true">block</span>}
                {code}
              </button>
            );
          })}
        </div>
      ) : (
        <div className="flex flex-wrap gap-1.5 justify-end max-w-[60%]">
          {current.length === 0 ? (
            <span className="px-3 py-1 rounded-full text-label-sm font-semibold border bg-emerald-100 text-emerald-800 border border-emerald-300">
              Không bỏ qua
            </span>
          ) : (
            current.map(code => (
              <span
                key={code}
                className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-label-sm font-semibold border bg-red-100 text-red-800 border border-red-300 border-error/30"
              >
                <span className="material-symbols-outlined text-[12px]" aria-hidden="true">block</span>
                {code}
              </span>
            ))
          )}
        </div>
      )}
    </div>
  );
}