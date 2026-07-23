"use client";

import { useMemo } from "react";
import type { TimelineIterationPoint } from "@/types/api";

interface ScoreChartProps {
  iterations: TimelineIterationPoint[];
}

/**
 * Line chart showing score progression over iterations.
 * Uses CSS for simplicity - can be upgraded to recharts or similar.
 */
export function ScoreChart({ iterations }: ScoreChartProps) {
  const { maxScore, minScore, points, acceptedPoints, rejectedPoints } = useMemo(() => {
    if (iterations.length === 0) {
      return { maxScore: 100, minScore: 0, points: "", acceptedPoints: "", rejectedPoints: "" };
    }

    const scores = iterations.map((i) => i.score);
    const maxScore = Math.max(...scores);
    const minScore = Math.min(...scores);
    const range = maxScore - minScore || 1;

    const width = 100;
    const height = 60;

    const points = iterations
      .map((i, idx) => {
        const x = (idx / Math.max(1, iterations.length - 1)) * width;
        const y = height - ((i.score - minScore) / range) * height;
        return `${x},${y}`;
      })
      .join(" ");

    const acceptedPoints = iterations
      .filter((i) => i.accepted === true)
      .map((i, _, arr) => {
        const idx = iterations.indexOf(i);
        const x = (idx / Math.max(1, iterations.length - 1)) * width;
        const y = height - ((i.score - minScore) / range) * height;
        return `${x},${y}`;
      })
      .join(" ");

    const rejectedPoints = iterations
      .filter((i) => i.accepted === false)
      .map((i, _, arr) => {
        const idx = iterations.indexOf(i);
        const x = (idx / Math.max(1, iterations.length - 1)) * width;
        const y = height - ((i.score - minScore) / range) * height;
        return `${x},${y}`;
      })
      .join(" ");

    return { maxScore, minScore, points, acceptedPoints, rejectedPoints };
  }, [iterations]);

  if (iterations.length === 0) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 h-64 flex items-center justify-center">
        <p className="text-on-surface-variant text-label-md">Chưa có dữ liệu</p>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      {/* Legend */}
      <div className="flex gap-4 mb-4">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-secondary" />
          <span className="text-label-sm text-on-surface-variant">Score</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-primary" />
          <span className="text-label-sm text-on-surface-variant">Accepted</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-error" />
          <span className="text-label-sm text-on-surface-variant">Rejected</span>
        </div>
      </div>

      {/* Chart */}
      <div className="relative h-64">
        <svg viewBox="0 0 100 60" className="w-full h-full" preserveAspectRatio="none">
          {/* Grid lines */}
          {[0, 25, 50, 75, 100].map((pct) => (
            <line
              key={pct}
              x1="0"
              y1={`${pct * 0.6}`}
              x2="100"
              y2={`${pct * 0.6}`}
              stroke="currentColor"
              strokeWidth="0.1"
              className="text-outline-variant"
            />
          ))}

          {/* Score line */}
          <polyline
            points={points}
            fill="none"
            stroke="currentColor"
            strokeWidth="0.3"
            className="text-secondary"
          />

          {/* Accepted points */}
          {acceptedPoints && (
            <polyline
              points={acceptedPoints}
              fill="none"
              stroke="currentColor"
              strokeWidth="0.5"
              className="text-primary"
            />
          )}

          {/* Rejected points */}
          {rejectedPoints && (
            <polyline
              points={rejectedPoints}
              fill="none"
              stroke="currentColor"
              strokeWidth="0.5"
              strokeDasharray="1,1"
              className="text-error"
            />
          )}
        </svg>

        {/* Y-axis labels */}
        <div className="absolute left-0 top-0 bottom-0 flex flex-col justify-between text-label-xs text-on-surface-variant">
          <span>{maxScore.toFixed(1)}</span>
          <span>{((maxScore + minScore) / 2).toFixed(1)}</span>
          <span>{minScore.toFixed(1)}</span>
        </div>

        {/* X-axis label */}
        <div className="absolute bottom-0 left-8 right-0 flex justify-between text-label-xs text-on-surface-variant">
          <span>0</span>
          <span>{Math.floor(iterations.length / 2)}</span>
          <span>{iterations.length}</span>
        </div>
      </div>

      {/* Stats */}
      <div className="mt-4 pt-4 border-t border-outline-variant grid grid-cols-3 gap-4 text-center">
        <div>
          <div className="text-title-lg text-on-surface font-bold">
            {iterations.length}
          </div>
          <div className="text-label-sm text-on-surface-variant">Tổng Moves</div>
        </div>
        <div>
          <div className="text-title-lg text-on-surface font-bold">
            {iterations.filter((i) => i.accepted === true).length}
          </div>
          <div className="text-label-sm text-on-surface-variant">Được chấp nhận</div>
        </div>
        <div>
          <div className="text-title-lg text-on-surface font-bold">
            {iterations.filter((i) => i.accepted === false).length}
          </div>
          <div className="text-label-sm text-on-surface-variant">Bị từ chối</div>
        </div>
      </div>
    </div>
  );
}
