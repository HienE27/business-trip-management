"use client";

import { useMemo } from "react";
import type { TimelineEvent } from "@/types/api";

interface LiveScoreChartProps {
  events: TimelineEvent[];
}

/**
 * Real-time score chart that updates as events come in.
 * Shows score progression over iterations.
 */
export function LiveScoreChart({ events }: LiveScoreChartProps) {
  const { data, maxScore, minScore } = useMemo(() => {
    if (events.length === 0) {
      return { data: [], maxScore: 100, minScore: 0 };
    }

    const scores = events.map((e) => e.score ?? 0);
    const maxScore = Math.max(...scores, 1);
    const minScore = Math.min(...scores, 0);
    const range = maxScore - minScore || 1;

    const width = 100;
    const height = 50;

    const data = events.map((event, idx) => {
      const x = (idx / Math.max(1, events.length - 1)) * width;
      const y = height - (((event.score ?? 0) - minScore) / range) * height;
      return { x, y, event };
    });

    return { data, maxScore, minScore };
  }, [events]);

  const recentEvents = events.slice(-50);

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-title-lg text-title-lg text-on-surface">Điểm số theo Iteration</h3>
        <div className="flex items-center gap-4 text-label-sm text-on-surface-variant">
          <div className="flex items-center gap-2">
            <div className="w-3 h-0.5 bg-secondary" />
            <span>Score</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-primary" />
            <span>Best</span>
          </div>
        </div>
      </div>

      {/* Chart */}
      <div className="relative h-48">
        {data.length === 0 ? (
          <div className="absolute inset-0 flex items-center justify-center">
            <p className="text-on-surface-variant text-label-md">Đang chờ dữ liệu...</p>
          </div>
        ) : (
          <svg viewBox="0 0 100 50" className="w-full h-full" preserveAspectRatio="none">
            {/* Grid lines */}
            {[0, 25, 50, 75, 100].map((pct) => (
              <line
                key={pct}
                x1="0"
                y1={`${pct * 0.5}`}
                x2="100"
                y2={`${pct * 0.5}`}
                stroke="currentColor"
                strokeWidth="0.1"
                className="text-outline-variant"
              />
            ))}

            {/* Score line */}
            {data.length > 1 && (
              <polyline
                points={data.map((d) => `${d.x},${d.y}`).join(" ")}
                fill="none"
                stroke="currentColor"
                strokeWidth="0.3"
                className="text-secondary"
              />
            )}

            {/* Current point */}
            {data.length > 0 && (
              <circle
                cx={data[data.length - 1].x}
                cy={data[data.length - 1].y}
                r="1"
                fill="currentColor"
                className="text-primary"
              />
            )}

            {/* Best point */}
            {data.length > 0 && (
              <circle
                cx={data[data.length - 1].x}
                cy={Math.min(...data.map((d) => d.y), 50)}
                r="0.8"
                fill="currentColor"
                className="text-secondary"
              />
            )}
          </svg>
        )}

        {/* Y-axis labels */}
        <div className="absolute left-0 top-0 bottom-0 flex flex-col justify-between text-label-xs text-on-surface-variant pr-2">
          <span>{maxScore.toFixed(0)}</span>
          <span>{((maxScore + minScore) / 2).toFixed(0)}</span>
          <span>{minScore.toFixed(0)}</span>
        </div>

        {/* X-axis label */}
        <div className="absolute bottom-0 left-8 right-0 flex justify-between text-label-xs text-on-surface-variant">
          <span>0</span>
          <span>{Math.floor(events.length / 2)}</span>
          <span>{events.length}</span>
        </div>
      </div>

      {/* Stats bar */}
      <div className="mt-6 pt-4 border-t border-outline-variant flex justify-between text-center">
        <div>
          <div className="text-title-lg text-on-surface font-bold">{events.length}</div>
          <div className="text-label-xs text-on-surface-variant">Iterations</div>
        </div>
        <div>
          <div className="text-title-lg text-secondary font-bold">
            {data.length > 0 ? (data[data.length - 1].event.score ?? 0).toFixed(0) : "—"}
          </div>
          <div className="text-label-xs text-on-surface-variant">Current Score</div>
        </div>
        <div>
          <div className="text-title-lg text-primary font-bold">
            {data.length > 0 ? Math.max(...data.map((d) => d.event.score ?? 0)).toFixed(0) : "—"}
          </div>
          <div className="text-label-xs text-on-surface-variant">Best Score</div>
        </div>
        <div>
          <div className="text-title-lg text-on-surface font-bold">
            {recentEvents.filter((e) => e.accepted === true).length}
          </div>
          <div className="text-label-xs text-on-surface-variant">Accepted</div>
        </div>
        <div>
          <div className="text-title-lg text-error font-bold">
            {recentEvents.filter((e) => e.accepted === false).length}
          </div>
          <div className="text-label-xs text-on-surface-variant">Rejected</div>
        </div>
      </div>
    </div>
  );
}
