"use client";

import type { DecisionNode } from "@/types/api";

interface NodeDetailPanelProps {
  node: DecisionNode;
}

/**
 * Panel showing details of a selected decision node.
 */
export function NodeDetailPanel({ node }: NodeDetailPanelProps) {
  const statusConfig: Record<string, { label: string; color: string }> = {
    ACCEPTED: { label: "Được chấp nhận", color: "bg-secondary-container text-on-secondary-container" },
    REJECTED: { label: "Bị từ chối", color: "bg-error-container text-on-error-container" },
    REJECTED_HARD: { label: "Hard Constraint", color: "bg-error-container text-on-error-container" },
    REJECTED_SOFT: { label: "Soft Constraint", color: "bg-tertiary-fixed text-on-tertiary" },
    REJECTED_TABU: { label: "Tabu", color: "bg-tertiary-fixed text-on-tertiary" },
    REJECTED_NO_IMPROVEMENT: { label: "Không cải thiện", color: "bg-surface-variant text-on-surface" },
    TRYING: { label: "Đang đánh giá", color: "bg-primary-fixed text-primary" },
  };

  const status = statusConfig[node.status ?? ""] || { label: node.status ?? "Unknown", color: "bg-surface-variant text-on-surface" };

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 space-y-4">
      <h3 className="font-title-lg text-title-lg text-on-surface">Node Details</h3>

      {/* Status */}
      <div className="flex items-center gap-2">
        <span className={`px-3 py-1 rounded-full text-label-sm font-medium ${status.color}`}>
          {status.label}
        </span>
      </div>

      {/* Staff */}
      {node.candidateStaffName && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Staff</div>
          <div className="flex items-center gap-2 bg-surface-container-low rounded-lg p-3">
            <span className="material-symbols-outlined text-primary bg-primary-fixed p-1 rounded text-[16px]">
              person
            </span>
            <div>
              <div className="text-body-md text-on-surface font-medium">{node.candidateStaffName}</div>
              {node.candidateStaffId && (
                <div className="text-label-xs text-on-surface-variant">ID: {node.candidateStaffId}</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Slot */}
      <div className="flex items-center justify-between">
        <div className="text-label-sm text-on-surface-variant">Slot</div>
        <span className="text-body-md text-on-surface font-medium">#{node.slotId}</span>
      </div>

      {/* Iteration */}
      <div className="flex items-center justify-between">
        <div className="text-label-sm text-on-surface-variant">Iteration</div>
        <span className="text-body-md text-on-surface font-medium">#{node.iteration}</span>
      </div>

      {/* Constraint Violated */}
      {node.violatedConstraint && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Constraint vi phạm</div>
          <div className="bg-error-container rounded-lg p-3">
            <span className="text-label-md font-bold text-error font-mono">
              {node.violatedConstraint}
            </span>
          </div>
        </div>
      )}

      {/* Rejection Reason */}
      {node.rejectionReason && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Lý do từ chối</div>
          <div className="bg-surface-container-low rounded-lg p-3">
            <span className="text-body-sm text-on-surface">{node.rejectionReason}</span>
          </div>
        </div>
      )}

      {/* Score Delta */}
      <div className="pt-4 border-t border-outline-variant space-y-3">
        <div className="text-label-sm text-on-surface-variant">Score Changes</div>
        
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Score Delta</span>
          <span className={`font-headline-md ${(node.scoreDelta ?? 0) > 0 ? "text-secondary" : (node.scoreDelta ?? 0) < 0 ? "text-error" : "text-on-surface"}`}>
            {(node.scoreDelta ?? 0) > 0 ? "+" : ""}{(node.scoreDelta ?? 0).toFixed(2)}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Coverage</span>
          <span className={`font-body-md ${(node.coverageDelta ?? 0) > 0 ? "text-secondary" : (node.coverageDelta ?? 0) < 0 ? "text-error" : "text-on-surface"}`}>
            {(node.coverageDelta ?? 0) > 0 ? "+" : ""}{(node.coverageDelta ?? 0).toFixed(2)}%
          </span>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Fairness</span>
          <span className={`font-body-md ${(node.fairnessDelta ?? 0) < 0 ? "text-secondary" : (node.fairnessDelta ?? 0) > 0 ? "text-error" : "text-on-surface"}`}>
            {(node.fairnessDelta ?? 0) > 0 ? "+" : ""}{(node.fairnessDelta ?? 0).toFixed(4)}
          </span>
        </div>
      </div>

      {/* Depth */}
      <div className="flex items-center justify-between">
        <span className="text-label-sm text-on-surface-variant">Depth</span>
        <span className="text-body-md text-on-surface font-medium">{node.depth}</span>
      </div>
    </div>
  );
}
