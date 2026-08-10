"use client";

import { useCallback, useEffect, useState } from "react";
import { Pagination } from "@/components/ui";
import { api } from "@/lib/api";
import { parseNumber } from "@/lib/number-utils";
import { EmptyState } from "@/components/ui/EmptyState";
import type { AlgorithmMetrics } from "./types";

type AlgoFilter = "ALL" | "GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC";
type CoverageFilter = "ALL" | "high" | "medium" | "low";

const ALGO_OPTIONS: { value: AlgoFilter; label: string }[] = [
  { value: "ALL", label: "Tất cả thuật toán" },
  { value: "GREEDY", label: "Greedy" },
  { value: "FAIR_GREEDY", label: "Fair Greedy" },
  { value: "CSP_MRV_FC", label: "CSP-MRV-FC" },
];

const COVERAGE_OPTIONS: { value: CoverageFilter; label: string }[] = [
  { value: "ALL", label: "Tất cả phủ lịch" },
  { value: "high", label: "≥ 90% (Tốt)" },
  { value: "medium", label: "70-90% (Trung bình)" },
  { value: "low", label: "< 70% (Thấp)" },
];

function coverageTone(value: number): { bar: string; text: string } {
  if (value >= 90) return { bar: "bg-primary", text: "text-primary" };
  if (value >= 70) return { bar: "bg-secondary", text: "text-secondary" };
  return { bar: "bg-error", text: "text-error" };
}

function formatExecTime(ms: number): string {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;
}

function formatDate(s: string): string {
  return new Date(s).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

export function MetricsHistory() {
  const [metrics, setMetrics] = useState<AlgorithmMetrics[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [algoFilter, setAlgoFilter] = useState<AlgoFilter>("ALL");
  const [coverageFilter, setCoverageFilter] = useState<CoverageFilter>("ALL");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  // BUGFIX #6: debounce keyword → server-side filter (300ms)
  const [debouncedKeyword, setDebouncedKeyword] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setDebouncedKeyword(keyword.trim()), 300);
    return () => clearTimeout(t);
  }, [keyword]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // BUGFIX #6: server-side filters instead of filtering the page slice client-side
      const data = await api.getPage<AlgorithmMetrics>("/auto-schedule/metrics/page", {
        page,
        size: pageSize,
        ...(debouncedKeyword ? { keyword: debouncedKeyword } : {}),
        ...(algoFilter !== "ALL" ? { algoType: algoFilter } : {}),
        ...(coverageFilter !== "ALL" ? { coverageFilter } : {}),
      });
      setMetrics(data.content ?? []);
      setTotalPages(data.totalPages ?? 0);
      setTotalElements(data.totalElements ?? 0);
    } catch {
      setMetrics([]);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedKeyword, algoFilter, coverageFilter]);

  useEffect(() => { void load(); }, [load]);

  // BUGFIX #6: server filters — render the page slice as-is.
  const filtered = metrics;

  if (loading) {
    return (
      <div className="space-y-2">
        {[1, 2, 3].map(i => <div key={i} className="h-12 bg-surface-container-low rounded-xl animate-pulse" />)}
      </div>
    );
  }

  if (metrics.length === 0) {
    return (
      <EmptyState
        icon="history"
        title="Chưa có lần chạy nào"
        description="Chạy thuật toán để xem lịch sử tại đây"
        className="rounded-xl border border-dashed border-outline-variant bg-surface-container-lowest"
      />
    );
  }

  return (
    <div className="space-y-3">
      <FilterBar
        keyword={keyword}
        onKeywordChange={(v) => { setKeyword(v); setPage(0); }}
        algoFilter={algoFilter}
        onAlgoFilterChange={(v) => { setAlgoFilter(v); setPage(0); }}
        coverageFilter={coverageFilter}
        onCoverageFilterChange={(v) => { setCoverageFilter(v); setPage(0); }}
        shown={filtered.length}
        total={totalElements}
      />

      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant">
                {TABLE_HEADERS.map(({ key, label, align }) => (
                  <th
                    key={key}
                    scope="col"
                    className={`px-3 py-2.5 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant ${align === "right" ? "text-right" : ""}`}
                  >
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/50">
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={TABLE_HEADERS.length} className="px-4 py-8 text-center text-label-sm text-on-surface-variant">
                    Không có kết quả phù hợp với bộ lọc
                  </td>
                </tr>
              ) : filtered.map(m => (
                <MetricsRow key={m.id} metric={m} />
              ))}
            </tbody>
          </table>
        </div>
        {!loading && totalElements > 0 && (
          <Pagination
            currentPage={page + 1}
            totalPages={totalPages}
            totalItems={totalElements}
            pageSize={pageSize}
            onPageChange={(p) => setPage(p - 1)}
            onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
          />
        )}
      </div>
    </div>
  );
}

const TABLE_HEADERS = [
  { key: "algo", label: "Thuật toán", align: "left" as const },
  { key: "total", label: "Tổng ca", align: "right" as const },
  { key: "coverage", label: "Phủ lịch", align: "right" as const },
  { key: "balance", label: "Cân bằng", align: "right" as const },
  { key: "conflict", label: "Xung đột", align: "right" as const },
  { key: "time", label: "Thời gian", align: "right" as const },
  { key: "date", label: "Ngày chạy", align: "left" as const },
  { key: "detail", label: "Chi tiết", align: "left" as const },
];

function MetricsRow({ metric }: { metric: AlgorithmMetrics }) {
  const coverage = parseNumber(metric.coverageRate);
  const balance = parseNumber(metric.balanceScore);
  const covTone = coverageTone(coverage);
  const balTone = coverageTone(balance);

  return (
    <tr className="hover:bg-surface-container-low transition-colors">
      <td className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary-fixed text-primary">
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">auto_mode</span>
          </span>
          <div>
            <span className="text-label-sm font-semibold text-on-surface">{metric.algorithmType}</span>
            {metric.periodName && <p className="text-[10px] text-on-surface-variant">{metric.periodName}</p>}
          </div>
        </div>
      </td>
      <td className="px-3 py-2.5 text-right">
        <span className="font-label-sm font-semibold text-on-surface tabular-nums">
          {metric.totalSchedulesCreated ?? 0}
        </span>
      </td>
      <td className="px-3 py-2.5 text-right">
        <ProgressCell value={coverage} tone={covTone} />
      </td>
      <td className="px-3 py-2.5 text-right">
        <ProgressCell value={balance} tone={balTone} />
      </td>
      <td className="px-3 py-2.5 text-right">
        <span className={`inline-flex items-center gap-1 text-label-xs font-semibold tabular-nums ${metric.conflictCount === 0 ? "text-blue-600" : "text-red-600"}`}>
          {metric.conflictCount > 0 && <span className="material-symbols-outlined text-[10px]" aria-hidden="true">warning</span>}
          {metric.conflictCount}
        </span>
      </td>
      <td className="px-3 py-2.5 text-right text-label-xs text-on-surface-variant tabular-nums">
        {formatExecTime(metric.executionTimeMs)}
      </td>
      <td className="px-3 py-2.5 text-label-xs text-on-surface-variant whitespace-nowrap">
        {formatDate(metric.createdAt)}
      </td>
      <td className="px-3 py-2.5">
        <button
          className="h-7 w-7 flex items-center justify-center rounded-lg hover:bg-surface-container-low active:scale-95 transition-all cursor-pointer"
          title="Xem chi tiết"
          aria-label="Xem chi tiết"
        >
          <span className="material-symbols-outlined text-[16px] text-on-surface-variant" aria-hidden="true">visibility</span>
        </button>
      </td>
    </tr>
  );
}

function ProgressCell({ value, tone }: { value: number; tone: { bar: string; text: string } }) {
  return (
    <div className="flex items-center justify-end gap-1.5">
      <div className="w-12 bg-surface-variant rounded-full h-1">
        <div
          className={`h-1 rounded-full transition-all duration-500 ${tone.bar}`}
          style={{ width: `${Math.min(100, value)}%` }}
        />
      </div>
      <span className={`text-label-xs font-semibold w-9 text-right tabular-nums ${tone.text}`}>
        {Math.round(value)}%
      </span>
    </div>
  );
}

type FilterBarProps = {
  keyword: string;
  onKeywordChange: (v: string) => void;
  algoFilter: AlgoFilter;
  onAlgoFilterChange: (v: AlgoFilter) => void;
  coverageFilter: CoverageFilter;
  onCoverageFilterChange: (v: CoverageFilter) => void;
  shown: number;
  total: number;
};

function FilterBar({ keyword, onKeywordChange, algoFilter, onAlgoFilterChange, coverageFilter, onCoverageFilterChange, shown, total }: FilterBarProps) {
  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-3 flex items-center gap-2 flex-wrap">
      <SearchInput value={keyword} onChange={onKeywordChange} placeholder="Tìm theo thuật toán hoặc kỳ..." />
      <FilterSelect<AlgoFilter> value={algoFilter} onChange={onAlgoFilterChange} options={ALGO_OPTIONS} />
      <FilterSelect<CoverageFilter> value={coverageFilter} onChange={onCoverageFilterChange} options={COVERAGE_OPTIONS} />
      <span className="text-[11px] text-on-surface-variant ml-auto">{shown}/{total} kết quả</span>
    </div>
  );
}

function SearchInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <div className="relative flex-1 min-w-[180px]">
      <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px]" aria-hidden="true">search</span>
      <input
        className="w-full h-8 pl-8 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all"
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
      />
    </div>
  );
}

function FilterSelect<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[] }) {
  return (
    <div className="relative">
      <select
        className="h-8 pl-2.5 pr-7 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer transition-all"
        value={value}
        onChange={e => onChange(e.target.value as T)}
      >
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px] pointer-events-none" aria-hidden="true">expand_more</span>
    </div>
  );
}