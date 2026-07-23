"use client";

import type { GraphStatistics } from "@/types/api";

interface GraphStatsProps {
  stats: GraphStatistics;
}

/**
 * Graph statistics panel.
 */
export function GraphStats({ stats }: GraphStatsProps) {
  const totalCandidates = stats.totalCandidates ?? 0;
  const totalAccepted = stats.totalAccepted ?? 0;
  const totalRejected = stats.totalRejected ?? 0;
  const acceptanceRate = totalCandidates > 0
    ? ((totalAccepted / totalCandidates) * 100).toFixed(1)
    : "0";
  const rejectionRate = totalCandidates > 0
    ? ((totalRejected / totalCandidates) * 100).toFixed(1)
    : "0";

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 space-y-4">
      <h3 className="font-title-lg text-title-lg text-on-surface">Statistics</h3>

      {/* Main metrics */}
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-surface-container-low rounded-lg p-3 text-center">
          <div className="text-title-lg text-on-surface font-bold">{stats.totalNodes ?? 0}</div>
          <div className="text-label-xs text-on-surface-variant">Nodes</div>
        </div>
        <div className="bg-surface-container-low rounded-lg p-3 text-center">
          <div className="text-title-lg text-on-surface font-bold">{stats.totalEdges ?? 0}</div>
          <div className="text-label-xs text-on-surface-variant">Edges</div>
        </div>
      </div>

      {/* Candidates */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Candidates tried</span>
          <span className="text-body-md text-on-surface font-medium">{totalCandidates}</span>
        </div>

        {/* Acceptance bar */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-label-xs text-secondary">Accepted</span>
            <span className="text-label-xs text-secondary font-bold">{totalAccepted} ({acceptanceRate}%)</span>
          </div>
          <div className="w-full bg-surface-variant rounded-full h-2">
            <div
              className="bg-secondary h-2 rounded-full transition-all"
              style={{ width: `${acceptanceRate}%` }}
            />
          </div>
        </div>

        {/* Rejection bar */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-label-xs text-error">Rejected</span>
            <span className="text-label-xs text-error font-bold">{totalRejected} ({rejectionRate}%)</span>
          </div>
          <div className="w-full bg-surface-variant rounded-full h-2">
            <div
              className="bg-error h-2 rounded-full transition-all"
              style={{ width: `${rejectionRate}%` }}
            />
          </div>
        </div>
      </div>

      {/* Additional stats */}
      <div className="pt-4 border-t border-outline-variant space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Avg. Branching</span>
          <span className="text-body-md text-on-surface font-medium">{(stats.averageBranchingFactor ?? 0).toFixed(1)}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Max Depth</span>
          <span className="text-body-md text-on-surface font-medium">{stats.maxDepth ?? 0}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Max Candidates</span>
          <span className="text-body-md text-on-surface font-medium">{stats.maxCandidatesPerIteration ?? 0}</span>
        </div>
      </div>

      {/* Rejection reasons */}
      {stats.rejectionReasons && Object.keys(stats.rejectionReasons).length > 0 && (
        <div className="pt-4 border-t border-outline-variant">
          <div className="text-label-sm text-on-surface-variant mb-2">Top Rejection Reasons</div>
          <div className="space-y-1">
            {Object.entries(stats.rejectionReasons)
              .sort(([, a], [, b]) => b - a)
              .slice(0, 5)
              .map(([reason, count]) => (
                <div key={reason} className="flex items-center justify-between">
                  <span className="text-label-sm text-on-surface font-mono bg-error-container px-2 py-0.5 rounded">
                    {reason}
                  </span>
                  <span className="text-label-sm text-error font-bold">{count}</span>
                </div>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
