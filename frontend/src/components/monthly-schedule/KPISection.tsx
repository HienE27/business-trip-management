"use client";

import { memo } from "react";
import type { OperationalKpi } from "./types";

const TONE_CLASS: Record<OperationalKpi["tone"], string> = {
  success: "border-secondary/25 bg-secondary-container/40",
  warning: "border-tertiary-container bg-tertiary-fixed text-on-tertiary-fixed",
  danger: "border-error/25 bg-error-container/60",
  info: "border-primary/20 bg-primary-fixed/40 text-primary",
  neutral: "border-outline-variant bg-surface-container-lowest",
};

export const KPISection = memo(function KPISection({ kpis }: { kpis: OperationalKpi[] }) {
  return (
    <section
      className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5 stagger-children"
      aria-label="Chỉ số vận hành lập lịch tháng"
    >
      {kpis.map((item) => (
        <article
          key={item.label}
          className={`rounded-xl border p-3 shadow-sm flex flex-col justify-between min-h-[88px] transition-colors hover:shadow-md animate-slide-up ${TONE_CLASS[item.tone]}`}
        >
          <div className="flex items-start justify-between gap-2">
            <p className="text-label-sm opacity-80 leading-tight">{item.label}</p>
            <span className="material-symbols-outlined text-[16px] opacity-60 shrink-0" aria-hidden="true">
              {item.icon}
            </span>
          </div>
          <div className="flex items-end justify-between gap-2 mt-2">
            <p className="text-[22px] font-bold leading-none text-on-surface">{item.value}</p>
            {item.trend && (
              <span className="rounded-full bg-surface-container-low px-1.5 py-0.5 text-[10px] font-semibold text-on-surface-variant whitespace-nowrap">
                {item.trend}
              </span>
            )}
          </div>
          <p className="mt-1 text-[11px] text-on-surface-variant leading-tight">{item.helper}</p>
        </article>
      ))}
    </section>
  );
});
