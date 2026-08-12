"use client";

import { memo } from "react";

type Breakdown = {
  shiftTypeName?: string;
  totalAssigned?: number;
  totalRequired?: number;
  coverageRate?: number;
  distinctStaffAssigned?: number;
};

const COLOR_MAP: Record<string, string> = {
  L01: "border-shift-24",
  L02: "border-shift-all-day",
  L03: "border-shift-service",
  L04: "border-shift-expert",
};

const ICON_BG_MAP: Record<string, string> = {
  L01: "bg-shift-24 text-on-shift-24",
  L02: "bg-shift-all-day text-on-shift-all-day",
  L03: "bg-shift-service text-on-shift-service",
  L04: "bg-shift-expert text-on-shift-expert",
};

const ICON_MAP: Record<string, string> = {
  L01: "emergency",
  L02: "schedule",
  L03: "medical_services",
  L04: "stethoscope",
};

/**
 * Card hiển thị 1 loại lịch (L01..L04) với thông tin coverage.
 * Được dùng trong KPI grid của AutoSchedulePanel.
 */
export const ShiftTypeBreakdownCard = memo(function ShiftTypeBreakdownCard({
  typeId,
  breakdown,
}: {
  typeId: string;
  breakdown: Breakdown;
}) {
  const color = COLOR_MAP[typeId] ?? "border-outline-variant";
  const iconBg = ICON_BG_MAP[typeId] ?? "bg-surface-container-low text-on-surface-variant";
  const icon = ICON_MAP[typeId] ?? "event";

  return (
    <div className={`flex items-center gap-2 p-3 rounded-xl border-2 ${color} bg-surface-container-lowest`}>
      <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${iconBg}`}>
        <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{icon}</span>
      </div>
      <div className="min-w-0">
        <p className="text-label-xs text-on-surface-variant truncate">{breakdown.shiftTypeName ?? typeId}</p>
        <p className="text-label-md font-bold text-on-surface tabular-nums">
          {breakdown.totalAssigned ?? 0}/{breakdown.totalRequired ?? 0}
        </p>
        <p className="text-label-xs text-on-surface-variant">
          {breakdown.coverageRate ?? 0}% phủ · {breakdown.distinctStaffAssigned ?? 0} NS
        </p>
      </div>
    </div>
  );
});