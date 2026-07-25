"use client";

import type {
  PlanningReport,
  FairnessAnalysis,
} from "./types";

type Props = {
  report: PlanningReport;
  applying?: boolean;
  onApply: () => void;
  onDismiss: () => void;
};

function starString(n: number): string {
  return "★".repeat(n) + "☆".repeat(5 - n);
}

export function PlanningReportView({ report, applying = false, onApply, onDismiss }: Props) {
  const r = report;
  return (
    <div className="space-y-2 text-[11px]">
      {/* Header */}
      <div className="flex items-center gap-2 mb-1">
        <span className="material-symbols-outlined text-primary text-[16px]">insights</span>
        <p className="text-label-sm font-semibold text-on-surface">PLANNING REPORT</p>
      </div>

      {/* Capacity */}
      <div className="p-2.5 rounded-lg bg-surface-container-low/40 border border-outline-variant/40">
        <p className="font-semibold text-on-surface mb-1 text-[11px]">Capacity Analysis</p>
        <div className="flex gap-3 text-[10px] text-on-surface-variant">
          <span>{r.capacity.totalStaff} staff</span>
          <span>{r.capacity.periodDays} days</span>
          <span>Demand: {r.capacity.totalDemand}</span>
          <span>Capacity: {r.capacity.maxCapacity}</span>
        </div>
        <div className="mt-1 flex items-center gap-2">
          <div className="flex-1 h-2 bg-surface-variant rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all"
              style={{
                width: `${Math.min(r.capacity.coverageCeiling, 100)}%`,
                backgroundColor: r.capacity.coverageCeiling >= 90 ? "#22c55e" : r.capacity.coverageCeiling >= 70 ? "#eab308" : "#ef4444",
              }}
            />
          </div>
          <span className="font-semibold font-mono text-[10px] text-on-surface">
            {r.capacity.coverageCeiling.toFixed(1)}% ceiling
          </span>
        </div>
      </div>

      {/* Constraint */}
      <div className="p-2.5 rounded-lg bg-surface-container-low/40 border border-outline-variant/40">
        <p className="font-semibold text-on-surface mb-1 text-[11px]">Constraint Analysis</p>
        <div className="flex items-center gap-2">
          <div className="flex-1 h-2 bg-surface-variant rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all"
              style={{
                width: `${r.constraint.overallFeasibility}%`,
                backgroundColor: r.constraint.riskLevel === "LOW" ? "#22c55e" : r.constraint.riskLevel === "MEDIUM" ? "#eab308" : "#ef4444",
              }}
            />
          </div>
          <span className="font-semibold font-mono text-[10px] text-on-surface">
            {r.constraint.overallFeasibility.toFixed(0)}%
          </span>
          <span className={`px-1.5 py-0.5 rounded text-[9px] font-semibold ${
            r.constraint.riskLevel === "LOW" ? "bg-green-100 text-green-800" :
            r.constraint.riskLevel === "MEDIUM" ? "bg-yellow-100 text-yellow-800" :
            "bg-red-100 text-red-800"
          }`}>
            {r.constraint.riskLevel}
          </span>
        </div>
      </div>

      {/* Fairness Options (read-only; Apply All sets arrangementMode) */}
      <div className="p-2.5 rounded-lg bg-surface-container-low/40 border border-outline-variant/40">
        <p className="font-semibold text-on-surface mb-1.5 text-[11px]">Fairness Options</p>
        <div className="space-y-1.5">
          {r.fairnessOptions.map((f: FairnessAnalysis) => (
            <div key={f.type}
              className="p-2 rounded-lg bg-surface-container-lowest border border-outline-variant/30"
            >
              <div className="flex items-center gap-1.5">
                <span className={`text-[10px] ${
                  f.starRating >= 4 ? "text-yellow-500" : f.starRating >= 3 ? "text-yellow-400" : "text-yellow-300"
                }`}>
                  {starString(f.starRating)}
                </span>
                <span className="font-semibold text-[10px] text-on-surface truncate">{f.label}</span>
              </div>
              <div className="flex gap-2 text-[9px] text-on-surface-variant mt-0.5">
                <span>Feasibility: {f.feasibility.toFixed(0)}%</span>
                <span>Expected: {f.expectedFairness.toFixed(0)}%</span>
                {f.coverageImpact !== 0 && (
                  <span className={f.coverageImpact > 0 ? "text-green-600" : "text-orange-500"}>
                    {f.coverageImpact > 0 ? `+${f.coverageImpact.toFixed(0)}%` : `${f.coverageImpact.toFixed(0)}%`} cov
                  </span>
                )}
                <span className={`${
                  f.constraintRisk === "LOW" ? "text-green-600" :
                  f.constraintRisk === "MEDIUM" ? "text-yellow-600" : "text-red-600"
                }`}>
                  {f.constraintRisk}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Algorithm Recommendation */}
      <div className="p-2.5 rounded-lg bg-surface-container-low/40 border border-outline-variant/40">
        <p className="font-semibold text-on-surface mb-0.5 text-[11px]">Algorithm Recommendation</p>
        <div className="flex items-center gap-1.5">
          <span className="text-[13px]">🏆</span>
          <span className="font-bold text-[11px] text-primary">{r.algorithm.algorithm.replace(/_/g, " ")}</span>
        </div>
        <p className="text-[9px] text-on-surface-variant mt-0.5">{r.algorithm.rationale}</p>
        {r.algorithm.alternatives.length > 0 && (
          <p className="text-[9px] text-on-surface-variant mt-0.5">
            Alternatives: {r.algorithm.alternatives.join(", ")}
          </p>
        )}
      </div>

      {/* Parameters */}
      <div className="p-2.5 rounded-lg bg-surface-container-low/40 border border-outline-variant/40">
        <p className="font-semibold text-on-surface mb-1 text-[11px]">Recommended Parameters</p>
        <div className="grid grid-cols-2 gap-x-3 gap-y-0.5 text-[9px] text-on-surface-variant">
          {[
            { key: "beamWidth", label: "beamWidth", value: String(r.parameters.beamWidth) },
            { key: "rebalanceRounds", label: "rebalance", value: String(r.parameters.rebalanceRounds) },
            { key: "weekendWeight", label: "weekendWeight", value: r.parameters.weekendWeight.toFixed(1) },
            { key: "arrangementMode", label: "arrangement", value: r.parameters.arrangementMode === "WITH_INTER_BALANCE" ? "Inter-type" : "Intra-type" },
            { key: "scorerWeights", label: "weights (cov/fair/con)", value: `${r.parameters.coverageWeight.toFixed(2)}/${r.parameters.fairnessWeight.toFixed(2)}/${r.parameters.constraintWeight.toFixed(2)}` },
            { key: "maxShiftsPerStaff", label: "maxShifts", value: String(r.parameters.maxShiftsPerStaff) },
          ].map(({ key, label, value }) => {
            const relevant = r.parameters.paramRelevance?.[key] ?? true;
            return (
              <div key={key} className={`flex items-center gap-1 ${relevant ? "" : "opacity-40"}`}>
                <span className={`inline-block w-1.5 h-1.5 rounded-full ${relevant ? "bg-green-500" : "bg-gray-300"}`} />
                <span>{label}: <span className="font-mono font-semibold text-on-surface">{value}</span></span>
                {!relevant && <span className="text-[7px] text-gray-400 ml-auto">(ignored)</span>}
              </div>
            );
          })}
        </div>
        <p className="text-[8px] text-on-surface-variant mt-1">
          ● = active for {r.algorithm.algorithm.replace(/_/g, " ")} &nbsp; ○ = ignored by this algorithm
        </p>
      </div>

      {/* Expected Result */}
      <div className="p-2.5 rounded-lg bg-primary/5 border border-primary/20">
        <p className="font-semibold text-on-surface mb-1 text-[11px]">Expected Result</p>
        <div className="grid grid-cols-4 gap-2 text-center">
          {[
            { label: "Coverage", value: r.expected.coverage, color: "text-green-600" },
            { label: "Constraint", value: r.expected.constraintScore, color: "text-blue-600" },
            { label: "Fairness", value: r.expected.fairnessScore, color: "text-purple-600" },
            { label: "Quality", value: r.expected.qualityScore, color: "text-amber-600" },
          ].map(({ label, value, color }) => (
            <div key={label}>
              <p className="text-[9px] text-on-surface-variant">{label}</p>
              <p className={`font-bold text-[13px] font-mono ${color}`}>{value.toFixed(1)}%</p>
            </div>
          ))}
        </div>
      </div>

      {/* Warnings */}
      {r.warnings.length > 0 && (
        <div className="p-2 rounded-lg bg-orange-50/50 border border-orange-200/50">
          <p className="font-semibold text-[10px] text-orange-800 mb-0.5">⚠ Warnings</p>
          {r.warnings.map((w, i) => (
            <p key={i} className="text-[9px] text-orange-700">{w}</p>
          ))}
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-2 pt-1">
        <button
          onClick={onApply}
          disabled={applying}
          className="flex-1 px-3 py-1.5 rounded-lg bg-primary text-white text-[10px] font-semibold hover:bg-primary/90 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {applying ? "Applying..." : "Áp dụng thuật toán"}
        </button>
        <button
          onClick={onDismiss}
          disabled={applying}
          className="px-3 py-1.5 rounded-lg border border-outline-variant text-[10px] font-semibold text-on-surface-variant hover:bg-surface-container-low transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
        >
          Dismiss
        </button>
      </div>
    </div>
  );
}
