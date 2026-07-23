"use client";

import type { AssignmentExplanation } from "@/types/api";

interface AssignmentExplainCardProps {
  explanation: AssignmentExplanation;
}

/**
 * Card showing why a staff was assigned.
 */
export function AssignmentExplainCard({ explanation }: AssignmentExplainCardProps) {
  return (
    <div className="space-y-4">
      {/* Staff Info */}
      <div className="flex items-center gap-3 p-3 bg-secondary-container rounded-lg">
        <span className="material-symbols-outlined text-secondary bg-secondary/20 p-2 rounded-lg">
          check_circle
        </span>
        <div>
          <div className="text-body-md text-on-surface font-medium">{explanation.staffName}</div>
          <div className="text-label-sm text-on-surface-variant">Được phân công</div>
        </div>
      </div>

      {/* Score */}
      <div className="text-center p-4 bg-surface-container-low rounded-lg">
        <div className="text-label-sm text-on-surface-variant">Total Score</div>
        <div className="text-display-lg text-secondary font-bold">+{(explanation.totalScore ?? explanation.score ?? 0).toFixed(1)}</div>
      </div>

      {/* Score Breakdown */}
      {explanation.scoreBreakdown && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Score Breakdown</div>
          <div className="space-y-1">
            {(explanation.scoreBreakdown.coverageScore ?? explanation.scoreBreakdown.coverage ?? 0) > 0 && (
              <div className="flex justify-between text-body-sm">
                <span>Coverage</span>
                <span className="text-secondary font-medium">+{(explanation.scoreBreakdown.coverageScore ?? explanation.scoreBreakdown.coverage ?? 0).toFixed(1)}</span>
              </div>
            )}
            {(explanation.scoreBreakdown.fairnessScore ?? explanation.scoreBreakdown.fairness ?? 0) > 0 && (
              <div className="flex justify-between text-body-sm">
                <span>Fairness</span>
                <span className="text-secondary font-medium">+{(explanation.scoreBreakdown.fairnessScore ?? explanation.scoreBreakdown.fairness ?? 0).toFixed(1)}</span>
              </div>
            )}
            {(explanation.scoreBreakdown.preferenceScore ?? 0) > 0 && (
              <div className="flex justify-between text-body-sm">
                <span>Preference</span>
                <span className="text-secondary font-medium">+{(explanation.scoreBreakdown.preferenceScore ?? 0).toFixed(1)}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Selection Reasons */}
      {explanation.selectionReasons && explanation.selectionReasons.length > 0 && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Lý do được chọn</div>
          <div className="space-y-1">
            {explanation.selectionReasons.map((reason, idx) => (
              <div key={idx} className="flex items-start gap-2">
                <span className="material-symbols-outlined text-secondary text-[16px] mt-0.5">check</span>
                <span className="text-body-sm text-on-surface">{typeof reason === "string" ? reason : reason.reason}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Hard Constraints */}
      {explanation.hardConstraints && explanation.hardConstraints.length > 0 && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Hard Constraints</div>
          <div className="space-y-1">
            {explanation.hardConstraints.map((c, idx) => (
              <div key={idx} className="flex items-center justify-between p-2 bg-surface-container-low rounded">
                <span className="text-body-sm text-on-surface">{c.name ?? c.constraintName ?? c.id}</span>
                <span className={`text-label-sm ${c.satisfied !== false ? "text-secondary" : "text-error"}`}>
                  {c.satisfied !== false ? "OK" : "FAIL"}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Natural Language */}
      {explanation.naturalLanguageExplanation && (
        <div className="p-3 bg-primary-fixed rounded-lg">
          <div className="text-label-xs text-primary mb-1">Giải thích</div>
          <p className="text-body-sm text-primary whitespace-pre-wrap">{explanation.naturalLanguageExplanation}</p>
        </div>
      )}
    </div>
  );
}
