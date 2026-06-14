"use client";

import { memo } from "react";
import type { OperationalKpi } from "./types";

const TONE_CLASS: Record<OperationalKpi["tone"], string> = {
  success: "border-secondary/25 bg-secondary-container/40 text-on-secondary-container",
  warning: "border-amber-200 bg-amber-50 text-amber-800",
  danger: "border-error/25 bg-error-container/60 text-on-error-container",
  info: "border-primary/20 bg-primary-fixed/40 text-primary",
  neutral: "border-outline-variant bg-surface-container-lowest text-on-surface",
};

export const KPISection = memo(function KPISection({ kpis }: { kpis: OperationalKpi[] }) {
  return (
    <section className="grid gap-4 md:grid-cols-2 lg:grid-cols-5" aria-label="Chỉ số vận hành lập lịch tháng">
      {kpis.map((item) => (
        <article key={item.label} className={`rounded-xl border p-5 shadow-sm ${TONE_CLASS[item.tone]}`}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-label-sm opacity-80">{item.label}</p>
              <p className="mt-3 text-display-lg text-on-surface">{item.value}</p>
            </div>
            <span className="material-symbols-outlined text-[24px] opacity-75" aria-hidden="true">{item.icon}</span>
          </div>
          <p className="mt-2 text-body-sm text-on-surface-variant">{item.helper}</p>
          {item.trend && (
            <p className="mt-3 inline-flex rounded-full bg-surface-container-low px-2 py-0.5 text-[11px] font-semibold text-on-surface-variant">
              {item.trend}
            </p>
          )}
        </article>
      ))}
    </section>
  );
});
