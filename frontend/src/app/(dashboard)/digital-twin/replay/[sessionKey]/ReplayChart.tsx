"use client";

import { useMemo } from "react";
import type { ReplayScoreSummary } from "@/types/api";

interface ReplayChartProps {
  summary?: ReplayScoreSummary;
  currentIndex: number;
  onSeek: (index: number) => void;
}

/**
 * Interactive score chart for replay.
 * Shows score progression with current position indicator.
 */
export function ReplayChart({ summary, currentIndex, onSeek }: ReplayChartProps) {
  const chartData = useMemo(() => {
    if (!summary || summary.iterations === 0 || summary.scores.length === 0) {
      return { points: "", width: 100, height: 50, maxScore: 100, minScore: 0 };
    }

    const width = 100;
    const height = 50;

    const scores = summary.scores;
    const maxScore = Math.max(...scores.map(s => s.score), 1);
    const minScore = Math.min(...scores.map(s => s.score), 0);
    const range = maxScore - minScore || 1;

    const iterations = summary.iterations;
    const points = Array.from({ length: iterations }, (_, idx) => {
      const x = (idx / Math.max(1, iterations - 1)) * width;
      const y = height - ((scores[idx]?.score ?? 0 - minScore) / range) * height;
      return { x, y };
    });

    return { points, width, height, maxScore, minScore };
  }, [summary]);

  const currentX = useMemo(() => {
    if (!summary || summary.iterations === 0) return 0;
    const width = 100;
    const iterations = summary.iterations;
    const idx = Math.min(currentIndex, iterations - 1);
    return (idx / Math.max(1, iterations - 1)) * width;
  }, [summary, currentIndex]);

  const currentY = useMemo(() => {
    if (!summary || !summary.scores[currentIndex]) return 25;
    const scores = summary.scores;
    const maxScore = Math.max(...scores.map(s => s.score), 1);
    const minScore = Math.min(...scores.map(s => s.score), 0);
    const range = maxScore - minScore || 1;
    const height = 50;
    return height - (((scores[currentIndex]?.score ?? 0) - minScore) / range) * height;
  }, [summary, currentIndex]);

  if (!summary || summary.iterations === 0) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 h-64 flex items-center justify-center">
        <p className="text-on-surface-variant text-label-md">Chưa có dữ liệu</p>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-title-lg text-title-lg text-on-surface">Score Timeline</h3>
        <div className="flex items-center gap-4 text-label-sm text-on-surface-variant">
          <div className="flex items-center gap-2">
            <div className="w-4 h-0.5 bg-secondary" />
            <span>Score</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-primary" />
            <span>Current</span>
          </div>
        </div>
      </div>

      {/* Chart */}
      <div className="relative h-48 cursor-pointer" onClick={(e) => {
        if (!summary) return;
        const iterations = summary.iterations;
        const rect = e.currentTarget.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const pct = x / rect.width;
        const idx = Math.round(pct * (iterations - 1));
        onSeek(Math.max(0, Math.min(idx, iterations - 1)));
      }}>
        <svg viewBox="0 0 100 50" className="w-full h-full" preserveAspectRatio="none">
          {/* Grid */}
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
          {chartData.points && (
            <polyline
              points={(Array.isArray(chartData.points) ? chartData.points : []).map((p: { x: number; y: number }) => `${p.x},${p.y}`).join(" ")}
              fill="none"
              stroke="currentColor"
              strokeWidth="0.3"
              className="text-secondary"
            />
          )}

          {/* Current position indicator */}
          <line
            x1={currentX}
            y1="0"
            x2={currentX}
            y2="50"
            stroke="currentColor"
            strokeWidth="0.3"
            className="text-primary opacity-50"
          />
          <circle
            cx={currentX}
            cy={currentY}
            r="1.5"
            fill="currentColor"
            className="text-primary"
          />
        </svg>

        {/* Y-axis labels */}
        <div className="absolute left-0 top-0 bottom-0 flex flex-col justify-between text-label-xs text-on-surface-variant pr-2">
          <span>{chartData.maxScore.toFixed(0)}</span>
          <span>{((chartData.maxScore + chartData.minScore) / 2).toFixed(0)}</span>
          <span>{chartData.minScore.toFixed(0)}</span>
        </div>

        {/* X-axis labels */}
        <div className="absolute bottom-0 left-8 right-0 flex justify-between text-label-xs text-on-surface-variant">
          <span>0</span>
          <span>{Math.floor(summary.iterations / 2)}</span>
          <span>{summary.iterations}</span>
        </div>
      </div>
    </div>
  );
}
