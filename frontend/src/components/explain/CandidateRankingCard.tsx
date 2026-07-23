"use client";

import type { CandidateRankingExplanation } from "@/types/api";

interface CandidateRankingCardProps {
  ranking: CandidateRankingExplanation;
}

/**
 * Card showing candidate ranking for a slot.
 */
export function CandidateRankingCard({ ranking }: CandidateRankingCardProps) {
  return (
    <div className="space-y-4">
      {/* Summary */}
      <div className="grid grid-cols-3 gap-2">
        <div className="text-center p-3 bg-surface-container-low rounded-lg">
          <div className="text-title-lg text-on-surface font-bold">{ranking.totalCandidates}</div>
          <div className="text-label-xs text-on-surface-variant">Candidates</div>
        </div>
        <div className="text-center p-3 bg-secondary-container rounded-lg">
          <div className="text-title-lg text-secondary font-bold">{ranking.acceptedCount}</div>
          <div className="text-label-xs text-secondary">Accepted</div>
        </div>
        <div className="text-center p-3 bg-error-container rounded-lg">
          <div className="text-title-lg text-error font-bold">{ranking.rejectedCount}</div>
          <div className="text-label-xs text-error">Rejected</div>
        </div>
      </div>

      {/* Rankings */}
      <div className="space-y-2">
        <div className="text-label-sm text-on-surface-variant">Ranking</div>
        <div className="space-y-1">
          {(ranking.rankings ?? ranking.candidates).map((candidate) => (
            <div
              key={candidate.staffId}
              className={`flex items-center justify-between p-3 rounded-lg ${
                candidate.selected
                  ? "bg-secondary-container border-l-4 border-secondary"
                  : candidate.rejected
                  ? "bg-surface-container-low"
                  : "bg-surface-container-low"
              }`}
            >
              <div className="flex items-center gap-3">
                {/* Rank badge */}
                <div
                  className={`w-8 h-8 rounded-full flex items-center justify-center text-label-sm font-bold ${
                    candidate.rank === 1
                      ? "bg-secondary text-white"
                      : candidate.selected
                      ? "bg-secondary/80 text-white"
                      : "bg-surface-variant text-on-surface-variant"
                  }`}
                >
                  {candidate.rank}
                </div>

                {/* Staff info */}
                <div>
                  <div className="text-body-sm text-on-surface font-medium">{candidate.staffName}</div>
                  {candidate.rejected && candidate.primaryConstraint && (
                    <div className="flex items-center gap-1 text-label-xs text-error">
                      <span className="material-symbols-outlined text-[12px]">block</span>
                      {candidate.primaryConstraint}
                    </div>
                  )}
                </div>
              </div>

              {/* Score */}
              <div className="text-right">
                <div
                  className={`font-headline-md ${
                    candidate.score > 0 ? "text-secondary" : candidate.score < 0 ? "text-error" : "text-on-surface"
                  }`}
                >
                  {candidate.score > 0 ? "+" : ""}{candidate.score.toFixed(1)}
                </div>
                {candidate.selected && (
                  <span className="material-symbols-outlined text-secondary text-[16px]">check_circle</span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Summary Stats */}
      {ranking.summary && (
        <div className="p-3 bg-surface-container-low rounded-lg space-y-2">
          <div className="text-label-sm text-on-surface-variant">Summary</div>
          <div className="grid grid-cols-2 gap-2 text-body-sm">
            <div className="flex justify-between">
              <span className="text-on-surface-variant">Highest:</span>
              <span className="text-secondary font-medium">+{(ranking.summary.highestScore ?? 0).toFixed(1)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">Lowest:</span>
              <span className="text-on-surface font-medium">{(ranking.summary.lowestScore ?? 0).toFixed(1)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">Average:</span>
              <span className="text-on-surface font-medium">{(ranking.summary.averageScore ?? 0).toFixed(1)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">Branching:</span>
              <span className="text-on-surface font-medium">{(ranking.summary.averageBranchingFactor ?? 0).toFixed(1)}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
