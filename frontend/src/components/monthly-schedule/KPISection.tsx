"use client";

import { memo } from "react";
import { Badge } from "@/components/ui/Badge";
import type { OperationalKpi } from "./types";

const TONE_CONFIG: Record<OperationalKpi["tone"], { 
  bg: string; 
  iconBg: string; 
  valueColor: string;
  iconColor: string;
}> = {
  success: { 
    bg: "bg-emerald-100 text-emerald-800", 
    iconBg: "bg-emerald-100", 
    valueColor: "text-emerald-800",
    iconColor: "text-emerald-800",
  },
  warning: { 
    bg: "bg-amber-100 text-amber-800", 
    iconBg: "bg-amber-100", 
    valueColor: "text-amber-800",
    iconColor: "text-amber-800",
  },
  danger: { 
    bg: "bg-red-100 text-red-800/30 border-red-30020", 
    iconBg: "bg-red-100 text-red-800", 
    valueColor: "text-red-800",
    iconColor: "text-red-800",
  },
  info: { 
    bg: "bg-blue-100", 
    iconBg: "bg-blue-100", 
    valueColor: "text-blue-800",
    iconColor: "text-blue-800",
  },
  neutral: { 
    bg: "bg-surface-container-lowest border-outline-variant", 
    iconBg: "bg-surface-container-high", 
    valueColor: "text-on-surface",
    iconColor: "text-on-surface-variant",
  },
};

export const KPISection = memo(function KPISection({ kpis }: { kpis: OperationalKpi[] }) {
  return (
    <section
      className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5 stagger-children"
      aria-label="Chỉ số vận hành lập lịch tháng"
    >
      {kpis.map((item) => {
        const config = TONE_CONFIG[item.tone];
        return (
          <article
            key={item.label}
            className={`rounded-xl border p-4 shadow-sm flex flex-col justify-between min-h-[100px] transition-all hover:shadow-md animate-slide-up ${config.bg}`}
          >
            <div className="flex items-start justify-between gap-2">
              <p className="text-label-sm text-on-surface-variant leading-tight">{item.label}</p>
              <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${config.iconBg}`}>
                <span className={`material-symbols-outlined text-[18px] ${config.iconColor}`} aria-hidden="true">
                  {item.icon}
                </span>
              </div>
            </div>
            <div className="flex items-end justify-between gap-2 mt-3">
              <p className={`text-[26px] font-bold leading-none tabular-nums ${config.valueColor}`}>{item.value}</p>
              {item.trend && (
                <Badge tone="neutral" size="sm">
                  {item.trend}
                </Badge>
              )}
            </div>
            {item.helper && (
              <p className="mt-2 text-[11px] text-on-surface-variant leading-tight">{item.helper}</p>
            )}
          </article>
        );
      })}
    </section>
  );
});
