"use client";

import { useMemo } from "react";
import type { TimelineIterationPoint } from "@/types/api";

interface MoveStatisticsProps {
  iterations: TimelineIterationPoint[];
}

/**
 * Displays move type breakdown (Assign, Swap, Change, Unassign).
 */
export function MoveStatistics({ iterations }: MoveStatisticsProps) {
  const stats = useMemo(() => {
    const counts: Record<string, number> = {
      ASSIGN: 0,
      SWAP: 0,
      CHANGE: 0,
      UNASSIGN: 0,
      REPAIR: 0,
    };

    let accepted = 0;
    let rejected = 0;

    iterations.forEach((i) => {
      if (i.moveType) {
        const type = i.moveType.toUpperCase();
        if (counts[type] !== undefined) {
          counts[type]++;
        } else {
          // Group unknown types
          counts["REPAIR"] = (counts["REPAIR"] || 0) + 1;
        }
      }

      if (i.accepted === true) accepted++;
      if (i.accepted === false) rejected++;
    });

    const total = iterations.length || 1;
    const percentages = Object.fromEntries(
      Object.entries(counts).map(([k, v]) => [k, (v / total) * 100])
    );

    return { counts, percentages, accepted, rejected, total };
  }, [iterations]);

  const moveConfig: Record<string, { label: string; color: string; icon: string; textColor: string }> = {
    ASSIGN: { label: "Gán", color: "bg-primary", textColor: "text-primary", icon: "add_circle" },
    SWAP: { label: "Đổi", color: "bg-secondary", textColor: "text-secondary", icon: "swap_horiz" },
    CHANGE: { label: "Thay đổi", color: "bg-tertiary", textColor: "text-tertiary", icon: "edit" },
    UNASSIGN: { label: "Bỏ gán", color: "bg-outline", textColor: "text-outline", icon: "remove_circle" },
    REPAIR: { label: "Sửa chữa", color: "bg-surface-variant", textColor: "text-on-surface-variant", icon: "build" },
  };

  if (iterations.length === 0) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 h-48 flex items-center justify-center">
        <p className="text-on-surface-variant text-label-md">Chưa có dữ liệu</p>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      {/* Progress bars */}
      <div className="space-y-3">
        {Object.entries(moveConfig).map(([type, config]) => {
          const count = stats.counts[type] || 0;
          const pct = stats.percentages[type] || 0;

          return (
            <div key={type}>
              <div className="flex justify-between items-center mb-1">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-[16px]">{config.icon}</span>
                  <span className="text-label-md text-on-surface">{config.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-label-sm text-on-surface-variant">{count}</span>
                  <span className={`text-label-sm font-bold ${config.textColor}`}>{pct.toFixed(0)}%</span>
                </div>
              </div>
              <div className="w-full bg-surface-variant rounded-full h-2">
                <div
                  className={`h-2 rounded-full ${config.color}`}
                  style={{ width: `${pct}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>

      {/* Acceptance rate */}
      <div className="mt-6 pt-4 border-t border-outline-variant">
        <div className="flex justify-between items-center mb-2">
          <span className="text-label-md text-on-surface">Tỷ lệ chấp nhận</span>
          <span className="text-label-lg font-bold text-primary">
            {((stats.accepted / stats.total) * 100).toFixed(1)}%
          </span>
        </div>
        <div className="w-full bg-surface-variant rounded-full h-3">
          <div
            className="h-3 rounded-full bg-primary"
            style={{ width: `${(stats.accepted / stats.total) * 100}%` }}
          />
        </div>
        <div className="flex justify-between mt-1 text-label-xs text-on-surface-variant">
          <span>Chấp nhận: {stats.accepted}</span>
          <span>Từ chối: {stats.rejected}</span>
        </div>
      </div>
    </div>
  );
}
