"use client";

import type { ReplayExplanation } from "@/types/api";

interface ReplayExplainCardProps {
  explanation: ReplayExplanation;
}

/**
 * Card showing explanation for a replay iteration.
 */
export function ReplayExplainCard({ explanation }: ReplayExplainCardProps) {
  return (
    <div className="space-y-4">
      {/* Header */}
      <div className={`flex items-center gap-3 p-3 rounded-lg ${
        explanation.accepted ? "bg-secondary-container" : "bg-error-container"
      }`}>
        <span className={`material-symbols-outlined ${
          explanation.accepted ? "text-secondary bg-secondary/20" : "text-error bg-error/20"
        } p-2 rounded-lg`}>
          {explanation.accepted ? "check_circle" : "cancel"}
        </span>
        <div>
          <div className="text-body-md text-on-surface font-medium">
            Iteration {explanation.iteration}
          </div>
          <div className="text-label-sm text-on-surface-variant">
            {explanation.moveType || "Move"} • {explanation.accepted ? "Accepted" : "Rejected"}
          </div>
        </div>
      </div>

      {/* Staff involved */}
      {(explanation.staffName || explanation.targetStaffName) && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Staff Involved</div>
          <div className="flex items-center gap-3">
            {explanation.staffName && (
              <div className="flex items-center gap-2 p-2 bg-surface-container-low rounded-lg">
                <span className="material-symbols-outlined text-primary">person</span>
                <span className="text-body-sm text-on-surface">{explanation.staffName}</span>
              </div>
            )}
            {explanation.targetStaffName && (
              <>
                <span className="material-symbols-outlined text-outline">swap_horiz</span>
                <div className="flex items-center gap-2 p-2 bg-surface-container-low rounded-lg">
                  <span className="material-symbols-outlined text-secondary">person</span>
                  <span className="text-body-sm text-on-surface">{explanation.targetStaffName}</span>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Score Breakdown */}
      {explanation.scoreBreakdown && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Score Breakdown</div>
          <div className="p-3 bg-surface-container-low rounded-lg space-y-1">
            <div className="flex justify-between">
              <span className="text-body-sm text-on-surface-variant">Coverage</span>
              <span className={`text-body-sm font-medium ${
                (explanation.scoreBreakdown.coverageDelta ?? 0) > 0 ? "text-secondary" :
                (explanation.scoreBreakdown.coverageDelta ?? 0) < 0 ? "text-error" : "text-on-surface"
              }`}>
                {(explanation.scoreBreakdown.coverageDelta ?? 0) > 0 ? "+" : ""}
                {(explanation.scoreBreakdown.coverageDelta ?? 0).toFixed(2)}%
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-body-sm text-on-surface-variant">Fairness</span>
              <span className={`text-body-sm font-medium ${
                (explanation.scoreBreakdown.fairnessDelta ?? 0) < 0 ? "text-secondary" :
                (explanation.scoreBreakdown.fairnessDelta ?? 0) > 0 ? "text-error" : "text-on-surface"
              }`}>
                {(explanation.scoreBreakdown.fairnessDelta ?? 0) > 0 ? "+" : ""}
                {(explanation.scoreBreakdown.fairnessDelta ?? 0).toFixed(4)}
              </span>
            </div>
            <div className="flex justify-between border-t border-outline-variant pt-1 mt-1">
              <span className="text-body-sm text-on-surface font-medium">Total</span>
              <span className={`text-body-sm font-bold ${
                (explanation.scoreBreakdown.totalDelta ?? 0) > 0 ? "text-secondary" :
                (explanation.scoreBreakdown.totalDelta ?? 0) < 0 ? "text-error" : "text-on-surface"
              }`}>
                {(explanation.scoreBreakdown.totalDelta ?? 0) > 0 ? "+" : ""}
                {(explanation.scoreBreakdown.totalDelta ?? 0).toFixed(1)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Constraint Changes */}
      {explanation.constraintChanges && explanation.constraintChanges.length > 0 && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Constraint Changes</div>
          <div className="space-y-1">
            {explanation.constraintChanges.map((change, idx) => (
              <div
                key={idx}
                className={`flex items-center justify-between p-2 rounded ${
                  change.improved ? "bg-secondary-container" : "bg-surface-container-low"
                }`}
              >
                <div>
                  <span className="font-mono text-label-sm text-on-surface">{change.constraintId}</span>
                  <span className="text-label-sm text-on-surface-variant ml-2">{change.constraintName}</span>
                </div>
                <span className={`text-label-sm ${
                  change.delta < 0 ? "text-secondary" : change.delta > 0 ? "text-error" : "text-on-surface"
                }`}>
                  {change.delta < 0 ? "" : "+"}{change.delta}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Acceptance/Rejection Reason */}
      {(explanation.acceptanceReason || explanation.rejectionReason) && (
        <div className={`p-3 rounded-lg ${
          explanation.accepted ? "bg-secondary-container" : "bg-error-container"
        }`}>
          <div className="text-label-xs text-on-surface-variant mb-1">
            {explanation.accepted ? "Lý do chấp nhận" : "Lý do từ chối"}
          </div>
          <p className="text-body-sm text-on-surface">
            {explanation.accepted ? explanation.acceptanceReason : explanation.rejectionReason}
          </p>
        </div>
      )}

      {/* Natural Language */}
      {explanation.naturalLanguageExplanation && (
        <div className="p-3 bg-primary-fixed rounded-lg">
          <div className="flex items-center gap-2 mb-2">
            <span className="material-symbols-outlined text-primary text-[16px]">psychology</span>
            <span className="text-label-xs text-primary font-medium">Giải thích</span>
          </div>
          <p className="text-body-sm text-primary whitespace-pre-wrap">
            {explanation.naturalLanguageExplanation}
          </p>
        </div>
      )}
    </div>
  );
}
