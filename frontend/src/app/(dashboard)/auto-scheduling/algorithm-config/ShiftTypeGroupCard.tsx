"use client";

import type { RuntimeConfig } from "./types";
import type { ShiftTypeGroup } from "./paramConfig";
import { getShiftRowLabel, getShiftRowTooltip } from "./paramConfig";

type Props = {
  group: ShiftTypeGroup;
  form: RuntimeConfig;
  editing: boolean;
  onChange: (key: string, value: number) => void;
};

export function ShiftTypeGroupCard({ group, form, editing, onChange }: Props) {
  return (
    <div
      className={`bg-surface-container-lowest rounded-xl border ${group.borderColor} overflow-hidden flex flex-col w-[180px] shrink-0 group/card`}
      style={{ minHeight: 160 }}
    >
      <div className={`px-3 py-2 border-b ${group.borderColor}/30 bg-surface-container-low flex items-start gap-2 shrink-0`}>
        <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${group.colorBg} ${group.color}`}>
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{group.icon}</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-label-sm font-bold text-on-surface leading-tight">{group.label}</p>
          <p className="text-[10px] text-on-surface-variant leading-tight">{group.subtitle}</p>
        </div>
      </div>

      <div className="px-3 py-1.5 bg-surface-container-low/50 border-b border-outline-variant/30">
        <p className="text-[9px] text-on-surface-variant leading-tight">{group.description}</p>
      </div>

      <div className="flex flex-col divide-y divide-outline-variant/40 flex-1">
        {group.params.map((param) => {
          const numVal = typeof form[param] === "number" ? (form[param] as number) : 0;
          const display = numVal === 0 ? "Tắt" : numVal.toString();
          const label = getShiftRowLabel(param);
          const tooltip = getShiftRowTooltip(param);
          return (
            <div
              key={param}
              className="flex items-center justify-between gap-2 px-3 py-2 hover:bg-surface-container-low/50 transition-colors group/row"
              title={tooltip}
            >
              <div className="flex items-center gap-1.5 min-w-0">
                <span className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/50 px-1 py-0.5 rounded leading-none whitespace-nowrap shrink-0">
                  {label}
                </span>
                <span
                  className="material-symbols-outlined text-[12px] text-on-surface-variant/60 hover:text-primary transition-colors shrink-0 cursor-help"
                  aria-hidden="true"
                >
                  info
                </span>
              </div>
              <div className="flex items-center shrink-0">
                {editing ? (
                  <input
                    type="number"
                    min={0}
                    max={99}
                    step={1}
                    className="h-8 w-16 rounded-lg border border-outline-variant bg-surface-container-low px-2 text-center text-[13px] font-mono text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors tabular-nums"
                    value={numVal}
                    onChange={(e) => onChange(param, parseInt(e.target.value) || 0)}
                  />
                ) : (
                  <span className="font-mono text-sm font-bold text-on-surface w-12 text-right shrink-0 tabular-nums">{display}</span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}