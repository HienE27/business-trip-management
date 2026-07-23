"use client";

import type { SandboxStatus } from "@/types/api";

interface LiveMetricsProps {
  iteration: number;
  score: number;
  coverage: number;
  fairnessCv: number;
  violations: number;
  isRunning: boolean;
}

/**
 * Live metrics panel showing current simulation state.
 */
export function LiveMetrics({
  iteration,
  score,
  coverage,
  fairnessCv,
  violations,
  isRunning,
}: LiveMetricsProps) {
  const coverageDelta = coverage - 80; // Assume initial coverage ~80%
  const isImproving = coverageDelta > 0;

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-title-lg text-title-lg text-on-surface">Metrics</h3>
        {isRunning && (
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-secondary animate-pulse" />
            <span className="text-label-sm text-secondary">Live</span>
          </div>
        )}
      </div>

      {/* Main metrics */}
      <div className="space-y-4">
        {/* Iteration */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary bg-primary-fixed p-1.5 rounded-md text-[18px]">
              tag
            </span>
            <span className="text-label-md text-on-surface-variant">Iteration</span>
          </div>
          <span className="font-headline-lg text-headline-lg text-on-surface tabular-nums">
            {iteration}
          </span>
        </div>

        {/* Score */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary bg-secondary-container p-1.5 rounded-md text-[18px]">
              score
            </span>
            <span className="text-label-md text-on-surface-variant">Score</span>
          </div>
          <span className="font-headline-lg text-headline-lg text-secondary tabular-nums">
            {score.toFixed(1)}
          </span>
        </div>

        {/* Coverage */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-primary bg-primary-fixed p-1.5 rounded-md text-[18px]">
                verified_user
              </span>
              <span className="text-label-md text-on-surface-variant">Coverage</span>
            </div>
            <div className="flex items-center gap-2">
              <span className={`text-label-sm ${isImproving ? "text-secondary" : "text-error"}`}>
                {coverageDelta >= 0 ? "+" : ""}{coverageDelta.toFixed(1)}%
              </span>
              <span className="font-headline-lg text-headline-lg text-on-surface tabular-nums">
                {coverage.toFixed(1)}%
              </span>
            </div>
          </div>
          <div className="w-full bg-surface-variant rounded-full h-2">
            <div
              className={`h-2 rounded-full transition-all duration-300 ${
                isImproving ? "bg-secondary" : "bg-primary"
              }`}
              style={{ width: `${Math.min(100, coverage)}%` }}
            />
          </div>
        </div>

        {/* Fairness CV */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-on-surface-variant bg-surface-container-high p-1.5 rounded-md text-[18px]">
                balance
              </span>
              <span className="text-label-md text-on-surface-variant">Fairness (CV)</span>
            </div>
            <span
              className={`font-headline-lg text-headline-lg tabular-nums ${
                fairnessCv < 0.15
                  ? "text-secondary"
                  : fairnessCv < 0.25
                  ? "text-tertiary"
                  : "text-error"
              }`}
            >
              {fairnessCv.toFixed(3)}
            </span>
          </div>
          <div className="w-full bg-surface-variant rounded-full h-2">
            <div
              className={`h-2 rounded-full transition-all duration-300 ${
                fairnessCv < 0.15
                  ? "bg-secondary"
                  : fairnessCv < 0.25
                  ? "bg-tertiary"
                  : "bg-error"
              }`}
              style={{ width: `${Math.min(100, fairnessCv * 100 * 3)}%` }}
            />
          </div>
        </div>

        {/* Violations */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-error bg-error-container p-1.5 rounded-md text-[18px]">
              warning
            </span>
            <span className="text-label-md text-on-surface-variant">Violations</span>
          </div>
          <span
            className={`font-headline-lg text-headline-lg tabular-nums ${
              violations === 0
                ? "text-secondary"
                : violations < 5
                ? "text-tertiary"
                : "text-error"
            }`}
          >
            {violations}
          </span>
        </div>
      </div>

      {/* Progress bar */}
      <div className="mt-6 pt-4 border-t border-outline-variant">
        <div className="flex items-center justify-between mb-2">
          <span className="text-label-sm text-on-surface-variant">Progress</span>
          <span className="text-label-sm text-on-surface font-medium">
            {Math.min(100, Math.round((iteration / 500) * 100))}%
          </span>
        </div>
        <div className="w-full bg-surface-variant rounded-full h-1.5">
          <div
            className={`h-1.5 rounded-full transition-all duration-300 ${
              isRunning ? "bg-primary animate-pulse" : "bg-secondary"
            }`}
            style={{ width: `${Math.min(100, (iteration / 500) * 100)}%` }}
          />
        </div>
      </div>
    </div>
  );
}
