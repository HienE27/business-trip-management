"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton } from "@/components/ui/Skeleton";
import { Pagination } from "@/components/ui/Pagination";
import { Button, IconButton } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import { useToast } from "@/hooks/useToast";
import { BackButton } from "@/components/ui/BackButton";
import type { AlgorithmMetrics, ApiResponse, SchedulePeriod } from "@/types/api";

const CompareModal = dynamic(
  () => import("./CompareModal").then((m) => m.CompareModal),
  { loading: () => null },
);

const DELETE_ALL_CONFIRM_PHRASE = "XÓA TẤT CẢ";

const ALGO_LABELS: Record<string, string> = {
  GREEDY: "Tham lam",
  FAIR_GREEDY: "Luân phiên",
  CSP_MRV_FC: "CSP-MRV-FC",
};

const ALGO_COLORS: Record<string, string> = {
  GREEDY: "bg-emerald-100 text-emerald-800 border border-emerald-300",
  FAIR_GREEDY: "bg-emerald-100 text-emerald-800 border border-emerald-300 border-emerald-30030",
  CSP_MRV_FC: "bg-amber-100 text-amber-800 border border-amber-300",
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
  const color = pct >= 90 ? "bg-emerald-100" : pct >= 70 ? "bg-blue-100" : pct >= 50 ? "bg-tertiary" : "bg-error";
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
  deleteSelected: boolean;
  onToggleDelete: () => void;
}

function RunRow({ run, periodName, isSelected, onToggle, canSelectTwo, deleteSelected, onToggleDelete }: RunRowProps) {
  return (
    <tr
      className={`border-b border-outline-variant hover:bg-surface-container-low transition-colors h-12 cursor-pointer ${
        isSelected ? "bg-blue-100 text-blue-800" : ""
      } ${deleteSelected ? "ring-2 ring-error/40 ring-inset" : ""}`}
      onClick={onToggle}
    >
      {/* Checkbox (compare) */}
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
      {/* Checkbox (delete bulk) */}
      <td className="py-2 px-4 w-10">
        <input
          type="checkbox"
          checked={deleteSelected}
          onChange={onToggleDelete}
          aria-label={`Chọn để xóa lần chạy ${run.id}`}
          className="w-4 h-4 accent-error rounded cursor-pointer"
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
      {/* Total Shifts */}
      <td className="py-2 px-4">
        <span className="font-label-sm text-label-sm font-semibold text-on-surface">
          {run.totalSchedulesCreated ?? 0}
        </span>
      </td>
      {/* Coverage */}
      <td className="py-2 px-4">
        <CoverageBar value={run.coverageRate} />
      </td>
      {/* Balance */}
      <td className="py-2 px-4">
        <span className="font-label-sm text-label-sm font-semibold text-on-surface">
          {(run.balanceScore ?? 0).toFixed(1)}%
        </span>
      </td>
      {/* Conflicts */}
      <td className="py-2 px-4">
        {run.conflictCount === 0 ? (
          <span className="inline-flex items-center gap-1 text-emerald-800 font-label-sm font-semibold">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-100 inline-block" /> 0
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 text-red-800 font-label-sm font-semibold">
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
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Bulk delete + typed-confirm state — mirrors the audit-history pattern.
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [deleteDialogType, setDeleteDialogType] = useState<"bulk" | "date-range" | "all" | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteDateFrom, setDeleteDateFrom] = useState("");
  const [deleteDateTo, setDeleteDateTo] = useState("");
  const [deleteAllConfirmText, setDeleteAllConfirmText] = useState("");
  const toast = useToast();

  // Helper: today's date in YYYY-MM-DD using local timezone (matches the
  // backend storage column). Same shape as audit-history so the two
  // dialogs feel identical.
  const todayStr = useMemo(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }, []);
  const subDateStr = (days: number) => {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  };

  const loadPeriods = useCallback(async () => {
    try {
      const periodsRes = await api.getAllPeriods();
      if (periodsRes?.data) setPeriods(periodsRes.data);
    } catch {
      // Best-effort: history still works without period names
    }
  }, []);

  const loadRuns = useCallback(async () => {
    try {
      setLoading(true);
      const result = selectedPeriodId
        ? await api.getMetricsPage(page, pageSize, selectedPeriodId)
        : await api.getMetricsPage(page, pageSize);
      setAllRuns(result.content ?? []);
      setTotalPages(result.totalPages ?? 0);
      setTotalElements(result.totalElements ?? 0);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể tải lịch sử thuật toán."));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, selectedPeriodId]);

  useEffect(() => { void loadPeriods(); }, [loadPeriods]);
  useEffect(() => { void loadRuns(); }, [loadRuns]);

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

  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    if (selectedIds.size === allRuns.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(allRuns.map((r) => r.id)));
    }
  }

  async function handleConfirmDelete() {
    if (!deleteDialogType || deleting) return;
    setDeleting(true);
    try {
      if (deleteDialogType === "bulk") {
        const ids = Array.from(selectedIds);
        const count = await api.deleteMultipleMetrics(ids);
        toast.success(`Đã xóa ${count} lần chạy.`);
        setSelectedIds(new Set());
        setDeleteDialogType(null);
        await loadRuns();
      } else if (deleteDialogType === "date-range") {
        const count = await api.deleteMetricsByDateRange(deleteDateFrom, deleteDateTo);
        toast.success(`Đã xóa ${count} lần chạy.`);
        setDeleteDialogType(null);
        setDeleteDateFrom("");
        setDeleteDateTo("");
        await loadRuns();
      } else if (deleteDialogType === "all") {
        const count = await api.deleteAllMetrics();
        toast.success(`Đã xóa toàn bộ ${count} lần chạy.`);
        setDeleteDialogType(null);
        setDeleteAllConfirmText("");
        setSelectedIds(new Set());
        await loadRuns();
      }
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi xóa lịch sử thuật toán."));
    } finally {
      setDeleting(false);
    }
  }

  const getPeriodName = (run: AlgorithmMetrics) => {
    // AlgorithmMetrics doesn't have periodId in the type, but the backend returns it
    const p = (run as unknown as Record<string, unknown>).periodName as string | undefined;
    return p ?? "Kỳ lịch";
  };

  const canCompare = compareA != null && compareB != null;

  return (
    <div className="space-y-5">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-2" />

      {message && (
        <div className="rounded-lg border border-red-300 bg-red-100 text-red-800 px-4 py-3 text-sm">
          <div className="flex items-start gap-2">
            <span className="material-symbols-outlined text-[18px] shrink-0">error</span>
            {message}
          </div>
        </div>
      )}
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-display-lg font-bold text-on-surface">Lịch sử thuật toán</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Xem và so sánh các lần chạy auto-scheduling trước đó.
          </p>
        </div>
      </div>

      {/* Toolbar */}
      <div className="flex items-center justify-between flex-wrap gap-3 mb-5">
        <div className="flex items-center gap-3 flex-wrap">
          <label className="flex items-center gap-2 text-[12px] text-on-surface-variant cursor-pointer shrink-0">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-outline-variant accent-primary cursor-pointer"
              checked={allRuns.length > 0 && selectedIds.size === allRuns.length}
              onChange={toggleSelectAll}
              disabled={allRuns.length === 0}
              aria-label="Chọn tất cả"
            />
            <span>Chọn tất cả</span>
          </label>

          <div className="relative">
            <label htmlFor="history-period-select" className="sr-only">Lọc theo kỳ lịch</label>
            <select
              id="history-period-select"
              className="h-10 pl-3 pr-8 bg-surface-container-low border border-transparent focus:ring-1 focus:ring-blue-300 focus:border-blue-300 cursor-pointer rounded-lg font-label-md text-label-md text-on-surface appearance-none"
              value={selectedPeriodId ?? ""}
              onChange={(e) => { setSelectedPeriodId(e.target.value ? Number(e.target.value) : null); setPage(0); }}
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
            {totalElements} lần chạy
          </div>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {selectedIds.size > 0 && (
            <span className="text-[12px] text-blue-800 font-semibold tabular-nums">
              {selectedIds.size} đã chọn
            </span>
          )}

          {selectedIds.size > 0 && (
            <Button
              variant="danger"
              size="sm"
              onClick={() => setDeleteDialogType("bulk")}
              icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete</span>}
            >
              Xóa ({selectedIds.size})
            </Button>
          )}

          {allRuns.length > 0 && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => { setDeleteDialogType("date-range"); setDeleteDateFrom(subDateStr(29)); setDeleteDateTo(todayStr); }}
              icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete_sweep</span>}
            >
              Xóa theo ngày
            </Button>
          )}

          {allRuns.length > 0 && (
            <Button
              variant="danger"
              size="sm"
              onClick={() => { setDeleteDialogType("all"); setDeleteAllConfirmText(""); }}
              icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete_forever</span>}
            >
              Xóa tất cả
            </Button>
          )}

          <div className="w-px h-6 bg-outline-variant" />

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
                ? "bg-blue-100 text-blue-800 hover:opacity-90 cursor-pointer"
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
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase w-10">So sánh</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase w-10">Xóa</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thời gian</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Kỳ lịch</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thuật toán</th>
                  <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Tổng ca</th>
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
                    deleteSelected={selectedIds.has(run.id)}
                    onToggleDelete={() => toggleSelect(run.id)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
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

      {/* Bulk delete confirm */}
      {deleteDialogType === "bulk" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={(e) => { if (e.target === e.currentTarget && !deleting) setDeleteDialogType(null); }}>
          <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl shadow-2xl w-full max-w-md mx-4 animate-scale-in">
            <div className="flex items-start gap-3 px-5 pt-5 pb-4 border-b border-outline-variant">
              <div className="w-10 h-10 rounded-full bg-red-100 text-red-800 border border-red-300 flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-red-800" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
              </div>
              <div className="flex-1">
                <h2 className="text-title-lg font-semibold text-on-surface">Xóa {selectedIds.size} lần chạy?</h2>
                <p className="text-body-sm text-on-surface-variant mt-1">Bạn có chắc muốn xóa {selectedIds.size} lần chạy đã chọn? Hành động này không thể hoàn tác.</p>
              </div>
              <IconButton label="Đóng" variant="ghost" size="sm" disabled={deleting} onClick={() => !deleting && setDeleteDialogType(null)} className="shrink-0 text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <div className="flex gap-2 px-5 py-4">
              <Button variant="secondary" size="md" fullWidth disabled={deleting} onClick={() => setDeleteDialogType(null)}>Hủy</Button>
              <Button variant="danger" size="md" fullWidth loading={deleting} onClick={handleConfirmDelete}>Xóa</Button>
            </div>
          </div>
        </div>
      )}

      {/* Date-range delete */}
      {deleteDialogType === "date-range" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={(e) => { if (e.target === e.currentTarget && !deleting) setDeleteDialogType(null); }}>
          <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl shadow-2xl w-full max-w-sm mx-4 animate-scale-in">
            <div className="flex items-center justify-between px-5 pt-5 pb-4 border-b border-outline-variant">
              <h2 className="text-title-lg font-semibold text-on-surface">Xóa theo khoảng ngày</h2>
              <IconButton label="Đóng" variant="ghost" size="sm" disabled={deleting} onClick={() => !deleting && setDeleteDialogType(null)} className="ml-auto text-on-surface-variant">
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <div className="px-5 py-4 flex flex-col gap-3">
              <p className="text-body-sm text-on-surface-variant">Chọn khoảng ngày cần xóa (cả hai đầu đều bao gồm).</p>
              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="metrics-del-from" className="text-[12px] font-semibold text-on-surface-variant">Từ ngày</label>
                  <input id="metrics-del-from" type="date" className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 transition-all" value={deleteDateFrom} onChange={(e) => setDeleteDateFrom(e.target.value)} disabled={deleting} />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="metrics-del-to" className="text-[12px] font-semibold text-on-surface-variant">Đến ngày</label>
                  <input id="metrics-del-to" type="date" className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 transition-all" value={deleteDateTo} onChange={(e) => setDeleteDateTo(e.target.value)} disabled={deleting} />
                </div>
              </div>
              <div className="flex gap-2 pt-1">
                <Button variant="secondary" size="md" fullWidth disabled={deleting} onClick={() => setDeleteDialogType(null)}>Hủy</Button>
                <Button variant="danger" size="md" fullWidth disabled={!deleteDateFrom || !deleteDateTo || deleteDateFrom > deleteDateTo || deleting} loading={deleting} onClick={handleConfirmDelete}>Xóa</Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Typed-confirm delete all */}
      {deleteDialogType === "all" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="metrics-delete-all-title" onClick={(e) => { if (e.target === e.currentTarget && !deleting) { setDeleteDialogType(null); setDeleteAllConfirmText(""); } }}>
          <div className="bg-surface-container-lowest border border-red-300 rounded-2xl shadow-2xl w-full max-w-md mx-4 animate-scale-in">
            <div className="flex items-start gap-3 px-5 pt-5 pb-4 border-b border-outline-variant">
              <div className="w-10 h-10 rounded-full bg-red-100 text-red-800 border border-red-300 flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-red-800" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
              </div>
              <div className="flex-1">
                <h2 id="metrics-delete-all-title" className="text-title-lg font-semibold text-on-surface">Xóa toàn bộ lịch sử thuật toán?</h2>
                <p className="text-body-sm text-on-surface-variant mt-1">Hành động này sẽ xóa vĩnh viễn <strong className="font-semibold text-red-800 tabular-nums">{totalElements.toLocaleString("vi")}</strong> lần chạy trong bảng algorithm_metrics. Không thể hoàn tác.</p>
              </div>
              <IconButton label="Đóng" variant="ghost" size="sm" disabled={deleting} onClick={() => { if (!deleting) { setDeleteDialogType(null); setDeleteAllConfirmText(""); } }} className="shrink-0 text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <div className="px-5 py-4 flex flex-col gap-3">
              <div className="border border-red-300 bg-red-100 text-red-800 rounded-lg p-3 flex items-start gap-2">
                <span className="material-symbols-outlined text-red-800 text-[18px] mt-0.5">info</span>
                <p className="text-[13px] bg-red-100 text-red-800 leading-snug">
                  Để xác nhận, hãy gõ chính xác cụm từ{" "}
                  <code className="px-1.5 py-0.5 rounded bg-error/15 text-red-800 font-mono font-bold text-[12px]">{DELETE_ALL_CONFIRM_PHRASE}</code>
                  {" "}vào ô bên dưới.
                </p>
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="metrics-delete-all-confirm" className="text-[12px] font-semibold text-on-surface-variant">Xác nhận xóa</label>
                <input
                  id="metrics-delete-all-confirm"
                  type="text"
                  autoComplete="off"
                  spellCheck={false}
                  value={deleteAllConfirmText}
                  onChange={(e) => setDeleteAllConfirmText(e.target.value)}
                  placeholder={DELETE_ALL_CONFIRM_PHRASE}
                  className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-error/30 focus:border-error transition-all font-mono"
                  disabled={deleting}
                />
                {deleteAllConfirmText && deleteAllConfirmText !== DELETE_ALL_CONFIRM_PHRASE && (
                  <p className="text-[11px] text-red-800" role="alert">Cụm từ chưa khớp. Hãy gõ đúng: {DELETE_ALL_CONFIRM_PHRASE}</p>
                )}
              </div>
              <div className="flex gap-2 pt-1">
                <Button variant="secondary" size="md" fullWidth disabled={deleting} onClick={() => { setDeleteDialogType(null); setDeleteAllConfirmText(""); }}>Hủy</Button>
                <Button
                  variant="danger"
                  size="md"
                  fullWidth
                  disabled={deleting || deleteAllConfirmText !== DELETE_ALL_CONFIRM_PHRASE || totalElements === 0}
                  loading={deleting}
                  onClick={() => { if (deleteAllConfirmText === DELETE_ALL_CONFIRM_PHRASE) handleConfirmDelete(); }}
                >
                  {deleting ? "Đang xóa…" : `Xóa vĩnh viễn ${totalElements.toLocaleString("vi")} lần chạy`}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
