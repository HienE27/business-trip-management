"use client";

import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import type {
  ScenarioResponse,
  ScenarioStatus,
} from "@/types/api";

interface ScenarioCardProps {
  scenario: ScenarioResponse;
  compareMode: boolean;
  selected: boolean;
  onToggleCompare: () => void;
  onRun: () => void;
  onDelete: () => void;
  onViewReplay: () => void;
  onViewDecision: () => void;
}

/**
 * Card displaying a what-if scenario.
 */
export function ScenarioCard({
  scenario,
  compareMode,
  selected,
  onToggleCompare,
  onRun,
  onDelete,
  onViewReplay,
  onViewDecision,
}: ScenarioCardProps) {
  const statusConfig: Record<ScenarioStatus, { label: string; tone: "success" | "warning" | "error" | "info" }> = {
    DRAFT: { label: "Bản nháp", tone: "info" },
    PENDING: { label: "Chờ", tone: "info" },
    READY: { label: "Sẵn sàng", tone: "info" },
    RUNNING: { label: "Đang chạy", tone: "info" },
    COMPLETED: { label: "Hoàn thành", tone: "success" },
    FAILED: { label: "Thất bại", tone: "error" },
    CANCELLED: { label: "Đã hủy", tone: "warning" },
  };

  const status = statusConfig[scenario.status];
  const canRun = scenario.status === "DRAFT" || scenario.status === "FAILED";
  const hasResults = scenario.status === "COMPLETED";

  return (
    <div
      className={`bg-surface-container-lowest border rounded-xl p-5 transition-all ${
        selected ? "border-primary ring-2 ring-primary ring-offset-2" : "border-outline-variant"
      } ${scenario.baseline ? "border-l-4 border-l-secondary" : ""}`}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-2 mb-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-title-lg text-title-lg text-on-surface truncate">{scenario.name}</h3>
            {scenario.baseline && (
              <Badge tone="success">Baseline</Badge>
            )}
          </div>
          <p className="text-label-sm text-on-surface-variant mt-1">
            {new Date(scenario.createdAt).toLocaleDateString("vi-VN")}
          </p>
        </div>
        <Badge tone={status.tone}>{status.label}</Badge>
      </div>

      {/* Results */}
      {hasResults && scenario.results && (
        <div className="grid grid-cols-2 gap-2 mb-4">
          <div className="bg-surface-container-low rounded-lg p-3 text-center">
            <div className="text-title-lg text-secondary font-bold">
              {scenario.results.coverage?.toFixed(1) ?? "—"}%
            </div>
            <div className="text-label-xs text-on-surface-variant">Coverage</div>
          </div>
          <div className="bg-surface-container-low rounded-lg p-3 text-center">
            <div className="text-title-lg text-on-surface font-bold">
              {scenario.results.fairness?.toFixed(3) ?? "—"}
            </div>
            <div className="text-label-xs text-on-surface-variant">Fairness</div>
          </div>
          <div className="bg-surface-container-low rounded-lg p-3 text-center">
            <div className="text-title-lg text-on-surface font-bold">
              {scenario.results.violations ?? "—"}
            </div>
            <div className="text-label-xs text-on-surface-variant">Violations</div>
          </div>
          <div className="bg-surface-container-low rounded-lg p-3 text-center">
            <div className="text-title-lg text-on-surface font-bold">
              {scenario.results.score?.toFixed(0) ?? "—"}
            </div>
            <div className="text-label-xs text-on-surface-variant">Score</div>
          </div>
        </div>
      )}

      {/* Duration */}
      {scenario.simulationDurationMs && (
        <div className="text-label-sm text-on-surface-variant mb-4">
          Runtime: {(scenario.simulationDurationMs / 1000).toFixed(1)}s
        </div>
      )}

      {/* Actions */}
      <div className="flex flex-wrap gap-2">
        {compareMode && hasResults && (
          <Button
            variant={selected ? "primary" : "ghost"}
            size="sm"
            onClick={onToggleCompare}
          >
            <span className="material-symbols-outlined text-[16px]">compare</span>
            {selected ? "Đã chọn" : "So sánh"}
          </Button>
        )}

        {canRun && !compareMode && (
          <Button variant="primary" size="sm" onClick={onRun}>
            <span className="material-symbols-outlined text-[16px]">play_arrow</span>
            Run
          </Button>
        )}

        {hasResults && !compareMode && (
          <>
            <Button variant="ghost" size="sm" onClick={onViewReplay}>
              <span className="material-symbols-outlined text-[16px]">replay</span>
              Replay
            </Button>
            <Button variant="ghost" size="sm" onClick={onViewDecision}>
              <span className="material-symbols-outlined text-[16px]">account_tree</span>
              Decision
            </Button>
          </>
        )}

        {!compareMode && (
          <Button variant="ghost" size="sm" onClick={onDelete}>
            <span className="material-symbols-outlined text-[16px]">delete</span>
          </Button>
        )}
      </div>
    </div>
  );
}
