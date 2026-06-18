"use client";

import { useState } from "react";

type AllocationStat = {
  department: string;
  percentage: number;
};

type AllocationStatsProps = {
  stats: AllocationStat[];
  className?: string;
};

/* ── AllocationStats ──
 *
 * Animated progress bars with hover tooltips.
 * Bars animate from 0% on mount (CSS animation).
 * Tooltip shows exact percentage on hover.
 */

export function AllocationStats({ stats, className = "" }: AllocationStatsProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  return (
    <section className={`flex flex-col bg-surface-container-lowest border border-outline-variant rounded-lg shadow-sm p-4 ${className}`}>
      <h3 className="text-title-lg text-on-surface mb-4">Thống kê phân bổ (Tuần)</h3>

      <div className="flex flex-col gap-4">
        {stats.map((stat, index) => (
          <div
            key={stat.department}
            className="group relative"
            onMouseEnter={() => setHoveredIndex(index)}
            onMouseLeave={() => setHoveredIndex(null)}
          >
            <div className="flex justify-between font-label-sm text-label-sm text-on-surface mb-1.5">
              <span>{stat.department}</span>
              <span className="font-semibold tabular-nums">{stat.percentage}%</span>
            </div>

            {/* Track */}
            <div className="w-full bg-surface-container rounded-full h-2 overflow-hidden" aria-hidden="true">
              {/* Bar */}
              <div
                className="h-2 rounded-full bg-primary transition-all duration-500 ease-out"
                style={{
                  width: `${stat.percentage}%`,
                  animationDelay: `${index * 80}ms`,
                }}
              />
            </div>

            {/* Hover tooltip */}
            <div
              className={`absolute right-0 -top-8 z-10 pointer-events-none transition-opacity duration-150 ${
                hoveredIndex === index ? "opacity-100" : "opacity-0"
              }`}
            >
              <div className="bg-on-surface text-surface text-label-xs rounded px-2 py-1 shadow-lg whitespace-nowrap">
                {stat.percentage}%
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Footer */}
      {stats.length > 0 && (
        <div className="mt-4 pt-3 border-t border-outline-variant flex justify-between text-label-xs text-outline">
          <span>{stats.length} khoa/phòng</span>
          <span>TB: {Math.round(stats.reduce((s, x) => s + x.percentage, 0) / stats.length)}%</span>
        </div>
      )}
    </section>
  );
}
