"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { Badge } from "@/components/ui/Badge";

interface BenchmarkResult {
  id: number;
  name: string;
  staffCount: number;
  dayCount: number;
  algorithm: string;
  executionTimeMs: number;
  iterations: number;
  movesPerSecond: number;
  peakMemoryMb: number;
  score: number;
  coverageRate: number;
  fairnessCv: number;
  totalViolations: number;
  success: boolean;
  createdAt: string;
}

interface Summary {
  totalBenchmarks: number;
  successfulRuns: number;
  failedRuns: number;
  avgExecutionTimeMs: number;
  avgScore: number;
  avgCoverage: number;
  avgFairness: number;
  runsByAlgorithm: Record<string, number>;
  runsByStaffCount: Record<number, number>;
}

/**
 * v12 Benchmark Dashboard Page
 *
 * Performance measurement and stress testing:
 * - Run benchmarks
 * - View results
 * - Compare algorithms
 * - Stress test
 */
export default function BenchmarkPage() {
  const [results, setResults] = useState<BenchmarkResult[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedAlgo, setSelectedAlgo] = useState<string>("all");

  // Load data
  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [sum, benchmarkList] = await Promise.all([
        api.get<Summary>("/benchmark/summary"),
        api.get<BenchmarkResult[]>("/benchmark/results"),
      ]);
      setSummary(sum);
      setResults(benchmarkList || []);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Run benchmark
  const handleRunBenchmark = async () => {
    setRunning(true);
    try {
      await api.post("/benchmark/run", {
        name: `Manual-${Date.now()}`,
        staffCount: 50,
        dayCount: 30,
        shiftTypeCount: 4,
        algorithm: "TABU",
      });
      await loadData();
    } catch (err) {
      console.error("Benchmark failed:", err);
    } finally {
      setRunning(false);
    }
  };

  // Run stress test
  const handleStressTest = async () => {
    if (!confirm("Run full stress test? This will take several minutes.")) return;
    
    setRunning(true);
    try {
      await api.post("/benchmark/stress", {});
      await loadData();
    } catch (err) {
      console.error("Stress test failed:", err);
    } finally {
      setRunning(false);
    }
  };

  // Run comparison
  const handleCompare = async () => {
    setRunning(true);
    try {
      await api.post("/benchmark/compare", {});
      await loadData();
    } catch (err) {
      console.error("Comparison failed:", err);
    } finally {
      setRunning(false);
    }
  };

  const filteredResults = selectedAlgo === "all" 
    ? results 
    : results.filter(r => r.algorithm === selectedAlgo);

  const formatTime = (ms: number) => {
    if (!ms) return "—";
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  };

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-surface mb-2">Benchmark</h1>
          <p className="text-body-md text-on-surface-variant">
            Performance measurement & Stress testing
          </p>
        </div>

        <div className="flex gap-2">
          <Button 
            variant="secondary" 
            onClick={handleCompare}
            disabled={running}
          >
            <span className="material-symbols-outlined text-[18px]">compare</span>
            Compare
          </Button>
          <Button 
            variant="secondary" 
            onClick={handleStressTest}
            disabled={running}
          >
            <span className="material-symbols-outlined text-[18px]">speed</span>
            Stress Test
          </Button>
          <Button 
            variant="primary" 
            onClick={handleRunBenchmark}
            disabled={running}
          >
            <span className="material-symbols-outlined text-[18px]">play_arrow</span>
            {running ? "Running..." : "Run Benchmark"}
          </Button>
        </div>
      </div>

      {/* Summary Cards */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map(i => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      ) : summary ? (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-label-sm text-on-surface-variant">Total Runs</span>
              <span className="material-symbols-outlined text-primary bg-primary-fixed p-1.5 rounded-lg text-[20px]">analytics</span>
            </div>
            <div className="text-display-lg text-on-surface font-bold">{summary.totalBenchmarks}</div>
            <div className="flex gap-2 mt-2">
              <Badge tone="success">{summary.successfulRuns} OK</Badge>
              <Badge tone="error">{summary.failedRuns} Fail</Badge>
            </div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-label-sm text-on-surface-variant">Avg Time</span>
              <span className="material-symbols-outlined text-secondary bg-secondary-container p-1.5 rounded-lg text-[20px]">timer</span>
            </div>
            <div className="text-display-lg text-on-surface font-bold">
              {formatTime(summary.avgExecutionTimeMs)}
            </div>
            <div className="text-label-sm text-on-surface-variant mt-1">per run</div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-label-sm text-on-surface-variant">Avg Coverage</span>
              <span className="material-symbols-outlined text-tertiary bg-tertiary-fixed p-1.5 rounded-lg text-[20px]">percent</span>
            </div>
            <div className="text-display-lg text-on-surface font-bold">
              {summary.avgCoverage?.toFixed(1) ?? "—"}%
            </div>
            <div className="text-label-sm text-on-surface-variant mt-1">coverage rate</div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-label-sm text-on-surface-variant">Avg Score</span>
              <span className="material-symbols-outlined text-warning bg-warning-container p-1.5 rounded-lg text-[20px]">grade</span>
            </div>
            <div className="text-display-lg text-on-surface font-bold">
              {summary.avgScore?.toFixed(0) ?? "—"}
            </div>
            <div className="text-label-sm text-on-surface-variant mt-1">final score</div>
          </div>
        </div>
      ) : null}

      {/* Algorithm Filter */}
      <div className="flex items-center gap-4">
        <span className="text-label-md text-on-surface-variant">Filter by algorithm:</span>
        <div className="flex gap-2">
          {["all", "TABU", "GREEDY", "RANDOM"].map(algo => (
            <button
              key={algo}
              onClick={() => setSelectedAlgo(algo)}
              className={`px-4 py-2 rounded-lg text-label-md transition-colors ${
                selectedAlgo === algo
                  ? "bg-primary text-white"
                  : "bg-surface-container-low text-on-surface hover:bg-surface-container-high"
              }`}
            >
              {algo === "all" ? "All" : algo}
            </button>
          ))}
        </div>
      </div>

      {/* Results Table */}
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
        <div className="p-4 border-b border-outline-variant bg-surface-container-low">
          <h3 className="font-title-lg text-title-lg text-on-surface">
            Results ({filteredResults.length})
          </h3>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-outline-variant bg-surface-container-low">
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant">Name</th>
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant">Staff</th>
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant">Algorithm</th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant">Time</th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant">Iterations</th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant">Coverage</th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant">Fairness</th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant">Violations</th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant">Score</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {filteredResults.length === 0 ? (
                <tr>
                  <td colSpan={9} className="py-8 text-center text-on-surface-variant">
                    No results yet. Run a benchmark to see results.
                  </td>
                </tr>
              ) : (
                filteredResults.map(result => (
                  <tr key={result.id} className="hover:bg-surface-container-low transition-colors">
                    <td className="py-3 px-4">
                      <span className="text-body-sm text-on-surface font-medium">{result.name}</span>
                      <div className="text-label-xs text-on-surface-variant">
                        {new Date(result.createdAt).toLocaleDateString("vi-VN")}
                      </div>
                    </td>
                    <td className="py-3 px-4 text-body-sm text-on-surface">{result.staffCount}</td>
                    <td className="py-3 px-4">
                      <Badge 
                        tone={result.algorithm === "TABU" ? "info" : result.algorithm === "GREEDY" ? "success" : "warning"}
                      >
                        {result.algorithm}
                      </Badge>
                    </td>
                    <td className="py-3 px-4 text-right text-body-sm text-on-surface">
                      {formatTime(result.executionTimeMs)}
                    </td>
                    <td className="py-3 px-4 text-right text-body-sm text-on-surface">
                      {result.iterations?.toLocaleString() ?? "—"}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <span className={`text-body-sm font-medium ${
                        (result.coverageRate ?? 0) >= 95 ? "text-secondary" : "text-warning"
                      }`}>
                        {result.coverageRate?.toFixed(1) ?? "—"}%
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right text-body-sm text-on-surface">
                      {result.fairnessCv?.toFixed(3) ?? "—"}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <span className={`text-body-sm font-medium ${
                        (result.totalViolations ?? 0) === 0 ? "text-secondary" : "text-error"
                      }`}>
                        {result.totalViolations ?? 0}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right text-body-sm text-on-surface font-medium">
                      {result.score?.toFixed(0) ?? "—"}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Quick Reference */}
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
        <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Expected Performance</h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="text-center">
            <div className="text-headline-lg text-on-surface font-bold">20</div>
            <div className="text-label-sm text-on-surface-variant">staff</div>
            <div className="text-label-sm text-secondary">~1-2s</div>
          </div>
          <div className="text-center">
            <div className="text-headline-lg text-on-surface font-bold">50</div>
            <div className="text-label-sm text-on-surface-variant">staff</div>
            <div className="text-label-sm text-secondary">~3-5s</div>
          </div>
          <div className="text-center">
            <div className="text-headline-lg text-on-surface font-bold">100</div>
            <div className="text-label-sm text-on-surface-variant">staff</div>
            <div className="text-label-sm text-secondary">~10-15s</div>
          </div>
          <div className="text-center">
            <div className="text-headline-lg text-on-surface font-bold">500</div>
            <div className="text-label-sm text-on-surface-variant">staff</div>
            <div className="text-label-sm text-secondary">~30-60s</div>
          </div>
        </div>
      </div>
    </div>
  );
}
