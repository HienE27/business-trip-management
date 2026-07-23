"use client";

import { useState } from "react";
import { HeatmapWidget, type HeatmapMetric } from "@/components/auto-scheduling/HeatmapWidget";

interface DashboardHeatmapPanelProps {
  periodId: number | null;
}

const METRIC_OPTIONS: Array<{ id: HeatmapMetric; label: string; icon: string }> = [
  { id: "load", label: "Tổng số ca", icon: "calendar_month" },
  { id: "weekend", label: "Ca cuối tuần", icon: "weekend" },
  { id: "consecutive", label: "Chuỗi ngày liên tục", icon: "straighten" },
];

/**
 * Dashboard wrapper around {@link HeatmapWidget} that adds a metric
 * switcher pill group (load / weekend / consecutive) so managers can
 * pivot the heatmap with a single click.
 */
export function DashboardHeatmapPanel({ periodId }: DashboardHeatmapPanelProps) {
  const [metric, setMetric] = useState<HeatmapMetric>("load");

  return (
    <div className="rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-fixed">
            <span className="material-symbols-outlined text-[20px] text-primary" aria-hidden="true">
              grid_on
            </span>
          </div>
          <div>
            <h3 className="font-headline-md text-headline-md text-on-surface">
              Bản đồ nhiệt ca trực
            </h3>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Mỗi ô là một ngày trong kỳ · màu đậm hơn = giá trị cao hơn
            </p>
          </div>
        </div>
        <div className="inline-flex rounded-full bg-surface-container p-1 shadow-inner" role="tablist">
          {METRIC_OPTIONS.map((opt) => {
            const active = metric === opt.id;
            return (
              <button
                key={opt.id}
                role="tab"
                aria-selected={active}
                onClick={() => setMetric(opt.id)}
                className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 font-label-md text-label-md transition-colors ${
                  active
                    ? "bg-primary text-on-primary shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-high"
                }`}
              >
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                  {opt.icon}
                </span>
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>
      <div className="p-4">
        <HeatmapWidget periodId={periodId} metric={metric} />
      </div>
    </div>
  );
}

export default DashboardHeatmapPanel;
