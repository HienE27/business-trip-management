"use client";

import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton } from "@/components/ui/Skeleton";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import type { AlgorithmMetrics, ApiResponse, SchedulePeriod } from "@/types/api";

const CompareModal = dynamic(
  () => import("./CompareModal").then((m) => m.CompareModal),
  { loading: () => null },
);

const ALGO_LABELS: Record<string, string> = {
  GREEDY: "Tham lam",
  ROUND_ROBIN: "Luân phiên",
  BACKTRACKING: "Backtracking",
};

const ALGO_COLORS: Record<string, string> = {
  GREEDY: "bg-primary-fixed text-primary border-primary/30",
  ROUND_ROBIN: "bg-secondary-container text-on-secondary-container border-secondary/30",
  BACKTRACKING: "bg-tertiary-fixed text-on-tertiary border-tertiary/30",
};

function formatDateTime(iso: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function CoverageBar({ value }: { value: number }) {
  // coverageRate from API is already 0-100, no need to multiply
  const pct = Math.min(100, Math.max(0, Math.round(value)));
  const color = pct >= 90 ? "bg-secondary" : pct >= 70 ? "bg-primary" : pct >= 50 ? "bg-tertiary" : "bg-error";
  return (
    <div className="flex items-center gap-2">
      <div className="w-20 bg-surface-container-low rounded-full h-1.5 overflow-hidden">
        <div className={`h-1.5 rounded-full ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="font-label-sm text-label-sm text-on-surface">{pct}%</span>
    </div>
  );
}

interface RunRowProps {
  run: AlgorithmMetrics;
  periodName: string;
  isSelected: boolean;
  onToggle: () => void;
  canSelectTwo: boolean;
}

function RunRow({ run, periodName, isSelected, onToggle, canSelectTwo }: RunRowProps) {
  return (
    <tr
      className={`border-b border-outline-variant hover:bg-surface-container-low transition-colors h-12 cursor-pointer ${
        isSelected ? "bg-primary-fixed" : ""
      }`}
      onClick={onToggle}
    >
      {/* Checkbox */}
      <td className="py-2 px-4 w-10">
        <input
          type="checkbox"
          checked={isSelected}
          onChange={onToggle}
          disabled={!canSelectTwo && !isSelected}
          className="w-4 h-4 accent-primary rounded cursor-pointer"
          onClick={(e) => e.stopPropagation()}
        />
      </td>
      {/* Time */}
      <td className="py-2 px-4">
        <span className="font-label-sm text-label-sm text-on-surface whitespace-nowrap">
          {formatDateTime(run.createdAt)}
        </span>
      </td>
      {/* Period */}
      <td className="py-2 px-4">
        <span className="font-label-sm text-label-sm text-on-surface-variant">{periodName}</span>
      </td>
      {/* Algorithm */}
      <td className="py-2 px-4">
        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold border ${ALGO_COLORS[run.algorithmType] ?? "bg-surface-container text-on-surface-variant border-outline"}`}>
          {ALGO_LABELS[run.algorithmType] ?? run.algorithmType}
        </span>
      </td>
      {/* Coverage */}
      <td className="py-2 px-4">
        <CoverageBar value={run.coverageRate} />
      </td>
      {/* Balance */}
      <td className="py-2 px-4">
        <span className="font-label-sm text-label-sm font-semibold text-on-surface">
          {run.balanceScore.toFixed ? run.balanceScore.toFixed(2) : run.balanceScore}
        </span>
      </td>
      {/* Conflicts */}
      <td className="py-2 px-4">
        {run.conflictCount === 0 ? (
          <span className="inline-flex items-center gap-1 text-secondary font-label-sm font-semibold">
            <span className="w-1.5 h-1.5 rounded-full bg-secondary inline-block" /> 0
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 text-error font-label-sm font-semibold">
            <span className="w-1.5 h-1.5 rounded-full bg-error inline-block" /> {run.conflictCount}
          </span>
        )}
      </td>
      {/* Execution time */}
      <td className="py-2 px-4">
        <span className="font-label-sm text-label-sm text-on-surface-variant">
          {run.executionTimeMs < 1000
            ? `${run.executionTimeMs}ms`
            : `${(run.executionTimeMs / 1000).toFixed(1)}s`}
        </span>
      </td>
    </tr>
  );
}

export default function AlgorithmHistoryPage() {
  return <AlgorithmHistoryContent />;
}

function AlgorithmHistoryContent() {
  const [allRuns, setAllRuns] = useState<AlgorithmMetrics[]>([]);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [compareA, setCompareA] = useState<AlgorithmMetrics | null>(null);
  const [compareB, setCompareB] = useState<AlgorithmMetrics | null>(null);
  const [openCompare, setOpenCompare] = useState(false);

  const load = useCallback(async () => {
    try {
      const [runsRes, periodsRes] = await Promise.all([
        selectedPeriodId
          ? api.getMetricsByPeriod(selectedPeriodId)
          : api.getAllMetrics(),
        api.getAllPeriods(),
      ]);
      if (runsRes) setAllRuns(selectedPeriodId ? runsRes as AlgorithmMetrics[] : (runsRes as ApiResponse<AlgorithmMetrics[]>).data ?? []);
      if (periodsRes?.data) setPeriods(periodsRes.data);
    } catch {
      setMessage("Không thể tải lịch sử thuật toán.");
    } finally {
      setLoading(false);
    }
  }, [selectedPeriodId]);

  useEffect(() => { void load(); }, [load]);

  useAutoDismiss(message, () => setMessage(null));

  const handleToggle = (run: AlgorithmMetrics) => {
    if (compareA?.id === run.id) {
      setCompareA(null);
    } else if (compareB?.id === run.id) {
      setCompareB(null);
    } else if (!compareA) {
      setCompareA(run);
    } else if (!compareB) {
      setCompareB(run);
    }
  };

  const getPeriodName = (run: AlgorithmMetrics) => {
    // AlgorithmMetrics doesn't have periodId in the type, but the backend returns it
    const p = (run as unknown as Record<string, unknown>).periodName as string | undefined;
    return p ?? "Kỳ lịch";
  };

  const canCompare = compareA != null && compareB != null;

  return (
    <>
      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error mb-4">
          {message}
        </div>
      )}
      {/* Toolbar */}
      <div className="flex items-center justify-between flex-wrap gap-3 mb-5">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="relative">
            <label htmlFor="history-period-select" className="sr-only">Lọc theo kỳ lịch</label>
            <select
              id="history-period-select"
              className="h-10 pl-3 pr-8 bg-surface-container-low border border-transparent focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer rounded-lg font-label-md text-label-md text-on-surface appearance-none"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">Tất cả các kỳ</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.periodName}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">
              expand_more
            </span>
          </div>

          <div className="text-label-sm text-on-surface-variant">
            {allRuns.length} lần chạy
          </div>
        </div>

        <div className="flex items-center gap-2">
          {(compareA ? 1 : 0) + (compareB ? 1 : 0) > 0 && (
            <span className="text-label-sm text-on-surface-variant">
              {(compareA ? 1 : 0) + (compareB ? 1 : 0)}/2 đã chọn
            </span>
          )}
          <button
            type="button"
            disabled={!canCompare}
            onClick={() => {
              if (canCompare) {
                setOpenCompare(true);
              }
            }}
            className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-label-sm text-label-sm font-semibold transition-colors ${
              canCompare
                ? "bg-primary text-on-primary hover:opacity-90 cursor-pointer"
                : "bg-surface-container text-outline cursor-not-allowed"
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">compare_arrows</span>
            So sánh
          </button>
        </div>
      </div>

      {/* Table */}
      <SectionCard title="" description="">
        {loading ? (
          <div className="space-y-3 p-4">
            {[...Array(5)].map((_, i) => (
              <Skeleton key={i} className="h-12 rounded-lg" />
            ))}
          </div>
        ) : allRuns.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <span className="material-symbols-outlined text-outline text-[48px]">history</span>
            <p className="font-label-md text-label-md text-on-surface-variant">
              Chưa có lần chạy thuật toán nào.
            </p>
            <p className="text-label-sm text-label-sm text-outline">
              Quay lại trang Xếp lịch tự động và chạy thuật toán lần đầu.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse" aria-label="Page Table">
              <thead>
                <tr className="bg-surface-container-low border-b border-outline-variant">
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase w-10">Chọn</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thời gian</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Kỳ lịch</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thuật toán</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Độ phủ</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Cân bằng</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Xung đột</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thời gian chạy</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {allRuns.map((run) => (
                  <RunRow
                    key={run.id}
                    run={run}
                    periodName={getPeriodName(run)}
                    isSelected={compareA?.id === run.id || compareB?.id === run.id}
                    onToggle={() => handleToggle(run)}
                    canSelectTwo={!compareA || !compareB}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      {/* Compare Modal */}
      {canCompare && openCompare && (
        <CompareModal
          runA={compareA!}
          runB={compareB!}
          periodNameA={getPeriodName(compareA!)}
          periodNameB={getPeriodName(compareB!)}
          onClose={() => { setOpenCompare(false); setCompareA(null); setCompareB(null); }}
        />
      )}
    </>
  );
}
