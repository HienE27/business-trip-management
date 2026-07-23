"use client";

import type { WhyNotExplanation } from "@/types/api";

interface WhyNotExplainCardProps {
  explanation: WhyNotExplanation;
}

/**
 * Card showing why a candidate was not selected.
 */
export function WhyNotExplainCard({ explanation }: WhyNotExplainCardProps) {
  return (
    <div className="space-y-4">
      {/* Staff Info */}
      <div className="flex items-center gap-3 p-3 bg-error-container rounded-lg">
        <span className="material-symbols-outlined text-error bg-error/20 p-2 rounded-lg">
          cancel
        </span>
        <div>
          <div className="text-body-md text-on-surface font-medium">{explanation.staffName}</div>
          <div className="text-label-sm text-on-surface-variant">Không được chọn</div>
        </div>
      </div>

      {/* Rank */}
      {explanation.rank != null && explanation.rank > 0 && (
        <div className="text-center p-4 bg-surface-container-low rounded-lg">
          <div className="text-label-sm text-on-surface-variant">Hạng</div>
          <div className="text-display-lg text-on-surface font-bold">#{explanation.rank}</div>
        </div>
      )}

      {/* Score Impact */}
      <div className="text-center p-4 bg-surface-container-low rounded-lg">
        <div className="text-label-sm text-on-surface-variant">Score Impact</div>
        <div className="text-display-lg text-error font-bold">{(explanation.scoreImpact ?? 0).toFixed(1)}</div>
      </div>

      {/* Rejection Reasons */}
      {explanation.rejectionReasons && explanation.rejectionReasons.length > 0 && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Lý do từ chối</div>
          <div className="space-y-2">
            {explanation.rejectionReasons.map((reason, idx) => (
              <div
                key={idx}
                className={`p-3 rounded-lg ${
                  reason.isBlocking ? "bg-error-container border-l-4 border-error" : "bg-surface-container-low"
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono text-label-md font-bold text-error">
                    {reason.constraintId}
                  </span>
                  <span className="text-label-sm text-error font-medium">
                    {(reason.penalty ?? 0).toFixed(1)}
                  </span>
                </div>
                <div className="text-body-sm text-on-surface">{reason.constraintName ?? reason.description}</div>
                {reason.detail && (
                  <div className="text-label-sm text-on-surface-variant mt-1">{reason.detail}</div>
                )}
                {reason.isBlocking && (
                  <div className="text-label-xs text-error mt-2 font-medium">CONSTRAINT CHÍNH</div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {explanation.constraintChain && explanation.constraintChain.length > 0 && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Constraint Chain</div>
          <div className="space-y-1 pl-2 border-l-2 border-outline">
            {explanation.constraintChain.map((node, idx) => (
              <div key={idx} className="pl-3">
                <div className="text-body-sm text-on-surface">{node.description}</div>
                {node.detail && (
                  <div className="text-label-sm text-on-surface-variant mt-0.5">{node.detail}</div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Selected Alternative */}
      {explanation.selectedAlternative && (
        <div className="p-3 bg-secondary-container rounded-lg">
          <div className="text-label-xs text-secondary mb-1">Được chọn thay thế</div>
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary">person</span>
            <div>
              <div className="text-body-md text-on-surface font-medium">
                {explanation.selectedAlternative.staffName}
              </div>
              <div className="text-label-sm text-secondary">
                Score: +{(explanation.selectedAlternative.score ?? 0).toFixed(1)}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Natural Language */}
      {explanation.naturalLanguageExplanation && (
        <div className="p-3 bg-error-fixed rounded-lg">
          <div className="text-label-xs text-error mb-1">Giải thích</div>
          <p className="text-body-sm text-error whitespace-pre-wrap">{explanation.naturalLanguageExplanation}</p>
        </div>
      )}
    </div>
  );
}
