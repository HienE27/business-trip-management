"use client";

import type { SandboxPromotionDiff } from "@/types/api";

interface ConstraintImprovementProps {
  diff: SandboxPromotionDiff | null;
}

/**
 * Displays constraint improvement from simulation.
 * Shows which constraints were improved based on diff data.
 */
export function ConstraintImprovement({ diff }: ConstraintImprovementProps) {
  // Simulated constraint data - in real implementation, this would come from snapshots
  const constraints = [
    { id: "BR01", name: "L01 vs L02 Conflict", before: 9, after: 2 },
    { id: "BR02", name: "L03 vs L04 Conflict", before: 5, after: 1 },
    { id: "BR03", name: "Compensation Day", before: 3, after: 0 },
    { id: "BR04", name: "Leave Conflict", before: 7, after: 2 },
    { id: "BR05", name: "Max Hours", before: 4, after: 1 },
    { id: "BR06", name: "Weekend Limit", before: 6, after: 2 },
    { id: "BR07", name: "Fairness", before: 8, after: 3 },
    { id: "BR08", name: "Coverage", before: 2, after: 0 },
  ];

  const getImprovement = (before: number, after: number) => {
    if (before === 0) return 100;
    return Math.round(((before - after) / before) * 100);
  };

  const maxBefore = Math.max(...constraints.map((c) => c.before), 1);

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      {/* Header */}
      <div className="grid grid-cols-3 gap-4 mb-4 px-2">
        <div className="text-label-sm text-on-surface-variant">Constraint</div>
        <div className="text-label-sm text-on-surface-variant text-center">Before</div>
        <div className="text-label-sm text-on-surface-variant text-center">After</div>
      </div>

      {/* Constraint bars */}
      <div className="space-y-3">
        {constraints.map((c) => {
          const improvement = getImprovement(c.before, c.after);
          const beforeWidth = (c.before / maxBefore) * 100;
          const afterWidth = (c.after / maxBefore) * 100;
          const isImproved = c.after < c.before;

          return (
            <div key={c.id} className="relative">
              <div className="grid grid-cols-3 gap-4 items-center">
                <div className="flex items-center gap-2">
                  <span className="text-label-md font-mono text-primary bg-primary-fixed px-1.5 py-0.5 rounded">
                    {c.id}
                  </span>
                  <span className="text-label-sm text-on-surface truncate">{c.name}</span>
                </div>

                {/* Before bar */}
                <div className="relative h-6 bg-surface-variant rounded overflow-hidden">
                  <div
                    className="absolute inset-y-0 left-0 bg-error/30"
                    style={{ width: `${beforeWidth}%` }}
                  />
                  <span className="absolute inset-0 flex items-center justify-center text-label-sm font-bold text-error">
                    {c.before}
                  </span>
                </div>

                {/* After bar */}
                <div className="relative h-6 bg-surface-variant rounded overflow-hidden">
                  <div
                    className={`absolute inset-y-0 left-0 ${isImproved ? "bg-secondary/30" : "bg-tertiary/30"}`}
                    style={{ width: `${afterWidth}%` }}
                  />
                  <span className={`absolute inset-0 flex items-center justify-center text-label-sm font-bold ${isImproved ? "text-secondary" : "text-tertiary"}`}>
                    {c.after}
                  </span>
                </div>
              </div>

              {/* Improvement indicator */}
              {isImproved && (
                <div className="absolute -right-2 top-1/2 -translate-y-1/2 bg-secondary text-on-secondary text-label-xs px-1.5 py-0.5 rounded-full">
                  -{improvement}%
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Summary */}
      <div className="mt-6 pt-4 border-t border-outline-variant flex justify-between items-center">
        <span className="text-label-md text-on-surface">Tổng cải thiện</span>
        <div className="flex items-center gap-4">
          <div className="text-center">
            <div className="text-title-lg text-error font-bold">
              {constraints.reduce((sum, c) => sum + c.before, 0)}
            </div>
            <div className="text-label-xs text-on-surface-variant">Before</div>
          </div>
          <span className="material-symbols-outlined text-on-surface-variant">arrow_forward</span>
          <div className="text-center">
            <div className="text-title-lg text-secondary font-bold">
              {constraints.reduce((sum, c) => sum + c.after, 0)}
            </div>
            <div className="text-label-xs text-on-surface-variant">After</div>
          </div>
          <div className="ml-4 px-3 py-1 bg-secondary-container rounded-full">
            <span className="text-label-sm font-bold text-on-secondary-container">
              -{Math.round(
                ((constraints.reduce((sum, c) => sum + c.before, 0) -
                  constraints.reduce((sum, c) => sum + c.after, 0)) /
                  Math.max(1, constraints.reduce((sum, c) => sum + c.before, 0))) *
                  100
              )}%
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
