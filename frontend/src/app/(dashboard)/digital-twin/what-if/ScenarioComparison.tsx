"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  ScenarioResponse,
  ScenarioComparison,
} from "@/types/api";

interface ScenarioComparisonProps {
  baselineId: number;
  comparedId: number;
  scenarios: ScenarioResponse[];
}

/**
 * Component showing comparison between two scenarios.
 */
export function ScenarioComparison({ baselineId, comparedId, scenarios }: ScenarioComparisonProps) {
  const [comparison, setComparison] = useState<ScenarioComparison | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const baseline = scenarios.find((s) => s.id === baselineId);
  const compared = scenarios.find((s) => s.id === comparedId);

  const loadComparison = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await api.compareWhatIfScenarios(baselineId, comparedId);
      setComparison(data as ScenarioComparison);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [baselineId, comparedId]);

  useEffect(() => {
    loadComparison();
  }, [loadComparison]);

  if (loading) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-surface-container-low rounded w-1/3" />
          <div className="h-24 bg-surface-container-low rounded" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
        <p className="text-error">{error}</p>
      </div>
    );
  }

  if (!comparison || !baseline || !compared) {
    return null;
  }

  const metrics = comparison.metrics ?? {};

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-primary">compare</span>
          <h3 className="font-title-lg text-title-lg text-on-surface">So sánh Scenario</h3>
        </div>
        <div className="flex items-center gap-4 text-label-sm">
          <span className="text-secondary font-medium">{baseline.name}</span>
          <span className="text-outline">vs</span>
          <span className="text-primary font-medium">{compared.name}</span>
        </div>
      </div>

      {/* Comparison Table */}
      <div className="p-4">
        <table className="w-full">
          <thead>
            <tr className="border-b border-outline-variant">
              <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant font-medium">Metric</th>
              <th className="py-3 px-4 text-center text-label-sm text-secondary font-medium">Baseline</th>
              <th className="py-3 px-4 text-center text-label-sm text-primary font-medium">Compared</th>
              <th className="py-3 px-4 text-center text-label-sm text-on-surface-variant font-medium">Change</th>
              <th className="py-3 px-4 text-center text-label-sm text-on-surface-variant font-medium">Impact</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {/* Coverage */}
            <tr className="hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4 text-body-md text-on-surface">Coverage</td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.baselineCoverage?.toFixed(1) ?? "—"}%
              </td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.comparedCoverage?.toFixed(1) ?? "—"}%
              </td>
              <td className="py-3 px-4 text-center">
                {metrics.coverageDelta !== undefined && metrics.coverageDelta !== null && (
                  <span className={`font-medium ${
                    metrics.coverageDelta > 0 ? "text-secondary" : metrics.coverageDelta < 0 ? "text-error" : "text-on-surface"
                  }`}>
                    {metrics.coverageDelta > 0 ? "+" : ""}{metrics.coverageDelta.toFixed(1)}%
                  </span>
                )}
              </td>
              <td className="py-3 px-4 text-center">
                {comparison.changes?.coverage && (
                  <span className={`inline-flex px-2 py-1 rounded-full text-label-xs font-medium ${
                    comparison.changes.coverage.impact === "HIGH"
                      ? "bg-error-container text-error"
                      : "bg-surface-container-low text-on-surface-variant"
                  }`}>
                    {comparison.changes.coverage.impact}
                  </span>
                )}
              </td>
            </tr>

            {/* Fairness */}
            <tr className="hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4 text-body-md text-on-surface">Fairness (CV)</td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.baselineFairness?.toFixed(3) ?? "—"}
              </td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.comparedFairness?.toFixed(3) ?? "—"}
              </td>
              <td className="py-3 px-4 text-center">
                {metrics.fairnessDelta !== undefined && metrics.fairnessDelta !== null && (
                  <span className={`font-medium ${
                    metrics.fairnessDelta < 0 ? "text-secondary" : metrics.fairnessDelta > 0 ? "text-error" : "text-on-surface"
                  }`}>
                    {metrics.fairnessDelta > 0 ? "+" : ""}{metrics.fairnessDelta.toFixed(3)}
                  </span>
                )}
              </td>
              <td className="py-3 px-4 text-center">
                {comparison.changes?.fairness && (
                  <span className={`inline-flex px-2 py-1 rounded-full text-label-xs font-medium ${
                    comparison.changes.fairness.impact === "HIGH"
                      ? "bg-error-container text-error"
                      : "bg-surface-container-low text-on-surface-variant"
                  }`}>
                    {comparison.changes.fairness.impact}
                  </span>
                )}
              </td>
            </tr>

            {/* Violations */}
            <tr className="hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4 text-body-md text-on-surface">Violations</td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.baselineViolations ?? "—"}
              </td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.comparedViolations ?? "—"}
              </td>
              <td className="py-3 px-4 text-center">
                {metrics.violationsDelta !== undefined && metrics.violationsDelta !== null && (
                  <span className={`font-medium ${
                    metrics.violationsDelta < 0 ? "text-secondary" : metrics.violationsDelta > 0 ? "text-error" : "text-on-surface"
                  }`}>
                    {metrics.violationsDelta < 0 ? "" : "+"}{metrics.violationsDelta}
                  </span>
                )}
              </td>
              <td className="py-3 px-4 text-center">
                {comparison.changes?.violations && (
                  <span className={`inline-flex px-2 py-1 rounded-full text-label-xs font-medium ${
                    comparison.changes.violations.impact === "HIGH"
                      ? "bg-error-container text-error"
                      : "bg-surface-container-low text-on-surface-variant"
                  }`}>
                    {comparison.changes.violations.impact}
                  </span>
                )}
              </td>
            </tr>

            {/* Score */}
            <tr className="hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4 text-body-md text-on-surface">Score</td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.baselineScore?.toFixed(0) ?? "—"}
              </td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.comparedScore?.toFixed(0) ?? "—"}
              </td>
              <td className="py-3 px-4 text-center">
                {metrics.scoreDelta !== undefined && metrics.scoreDelta !== null && (
                  <span className={`font-medium ${
                    metrics.scoreDelta > 0 ? "text-secondary" : metrics.scoreDelta < 0 ? "text-error" : "text-on-surface"
                  }`}>
                    {metrics.scoreDelta > 0 ? "+" : ""}{metrics.scoreDelta.toFixed(0)}
                  </span>
                )}
              </td>
              <td className="py-3 px-4 text-center">—</td>
            </tr>

            {/* Runtime */}
            <tr className="hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4 text-body-md text-on-surface">Runtime</td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.baselineRuntime ? `${(metrics.baselineRuntime / 1000).toFixed(1)}s` : "—"}
              </td>
              <td className="py-3 px-4 text-center text-body-md text-on-surface">
                {metrics.comparedRuntime ? `${(metrics.comparedRuntime / 1000).toFixed(1)}s` : "—"}
              </td>
              <td className="py-3 px-4 text-center">
                {metrics.runtimeDelta !== undefined && metrics.runtimeDelta !== null && (
                  <span className={`font-medium ${
                    metrics.runtimeDelta < 0 ? "text-secondary" : metrics.runtimeDelta > 0 ? "text-error" : "text-on-surface"
                  }`}>
                    {metrics.runtimeDelta > 0 ? "+" : ""}{metrics.runtimeDelta.toFixed(1)}s
                  </span>
                )}
              </td>
              <td className="py-3 px-4 text-center">—</td>
            </tr>
          </tbody>
        </table>
      </div>

      {/* Recommendation */}
      <div className="p-4 border-t border-outline-variant bg-surface-container-low">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-primary">tips_and_updates</span>
          <div>
            <div className="text-label-sm text-on-surface-variant">Recommendation</div>
            <p className="text-body-md text-on-surface">{comparison.recommendation}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
