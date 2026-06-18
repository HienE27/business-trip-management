"use client";

import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import {
  type ScheduleTableViewProps,
  type SortKey,
} from "./schedule-table/constants";
import {
  applyTablePipeline,
  countActiveFilters,
  EMPTY_FILTERS,
  type TableFilters,
} from "./schedule-table/filterSort";
import { ScheduleTableToolbar } from "./schedule-table/ScheduleTableToolbar";
import { ScheduleTableHeader, ScheduleTablePagination } from "./schedule-table/ScheduleTablePagination";
import { ScheduleTableRow } from "./schedule-table/ScheduleTableRow";
import type { FilterConflict } from "./schedule-table/constants";

export function ScheduleTableView({
  schedules,
  onEdit,
  onDelete,
  onResolveConflict,
  onViewDetail,
  canEdit = false,
}: ScheduleTableViewProps) {
  const uid = useId();

  const [filters, setFilters] = useState<TableFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  const [searchInput, setSearchInput] = useState(""); // live input, debounced into filters.search

  // Debounce: update filters.search 300ms after user stops typing.
  // Uses a ref flag so that pressing Escape can cancel a pending timer.
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingSearchRef = useRef<string | null>(null);
  useEffect(() => {
    pendingSearchRef.current = searchInput;
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTimerRef.current = setTimeout(() => {
      setFilters((p) => p.search !== pendingSearchRef.current
        ? { ...p, search: pendingSearchRef.current ?? "" }
        : p
      );
    }, 300);
    return () => { if (searchTimerRef.current) clearTimeout(searchTimerRef.current); };
  }, [searchInput]);

  const { pageData, safePage, totalPages, totalFiltered } = useMemo(
    () => applyTablePipeline(schedules, filters, page),
    [schedules, filters, page],
  );

  const conflictCount = useMemo(() => pageData.filter((s) => s.hasConflict).length, [pageData]);
  const activeCount = countActiveFilters(filters);

  const handleSort = useCallback((key: SortKey) => {
    setPage(1);
    setFilters((prev) => (prev.sortKey === key ? { ...prev, sortDir: prev.sortDir === "asc" ? "desc" : "asc" } : { ...prev, sortKey: key, sortDir: "asc" }));
  }, []);

  const handleReset = useCallback(() => {
    setPage(1);
    setSearchInput("");
    setFilters(EMPTY_FILTERS);
  }, []);

  // Ctrl+K / "/" focus search, Escape clears search
  useEffect(() => {
    const searchId = `${uid}-search`;
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      const isSearchFocused = target.id === searchId;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        document.getElementById(searchId)?.focus();
      } else if (e.key === "Escape" && isSearchFocused) {
        target.blur();
      } else if (e.key === "Escape" && searchInput) {
        setSearchInput("");
        setFilters((p) => p.search ? { ...p, search: "" } : p);
      } else if (e.key === "/" && target.tagName !== "INPUT" && target.tagName !== "TEXTAREA" && target.tagName !== "SELECT") {
        e.preventDefault();
        document.getElementById(searchId)?.focus();
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [uid, searchInput]);

  return (
    <div className="flex flex-col h-full">
      <ScheduleTableToolbar
        schedules={schedules}
        filters={filters}
        searchValue={searchInput}
        onSearchChange={(v) => { setPage(1); setSearchInput(v); }}
        onTypeChange={(v) => { setPage(1); setFilters((p) => ({ ...p, filterType: v })); }}
        onStaffChange={(v) => { setPage(1); setFilters((p) => ({ ...p, filterStaff: v })); }}
        onConflictChange={(v: FilterConflict) => { setPage(1); setFilters((p) => ({ ...p, filterConflict: v })); }}
        onDateFromChange={(v) => { setPage(1); setFilters((p) => ({ ...p, dateFrom: v })); }}
        onDateToChange={(v) => { setPage(1); setFilters((p) => ({ ...p, dateTo: v })); }}
        onReset={handleReset}
        resultCount={totalFiltered}
        conflictCount={conflictCount}
      />

      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse border border-outline-variant text-label-sm">
          <ScheduleTableHeader sortKey={filters.sortKey} sortDir={filters.sortDir} onSort={handleSort} />
          <tbody className="divide-y divide-outline-variant/40">
            {pageData.length === 0 ? (
              <tr>
                <td colSpan={7} className="p-0">
                  <EmptyState
                    icon={activeCount > 0 ? "filter_alt_off" : "event_busy"}
                    title={activeCount > 0 ? "Không có lịch phù hợp" : "Chưa có lịch nào"}
                    description={activeCount > 0
                      ? "Thử điều chỉnh bộ lọc hoặc xóa bộ lọc để xem tất cả lịch."
                      : "Khi có lịch trực được tạo, chúng sẽ xuất hiện tại đây."}
                    action={activeCount > 0 ? (
                      <button
                        type="button"
                        onClick={handleReset}
                        className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-on-primary text-label-sm font-semibold hover:bg-primary/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
                      >
                        <span aria-hidden="true" className="material-symbols-outlined text-[16px]">filter_alt_off</span>
                        Xóa tất cả bộ lọc
                      </button>
                    ) : undefined}
                  />
                </td>
              </tr>
            ) : (
              pageData.map((s) => (
                <ScheduleTableRow
                  key={s.id}
                  schedule={s}
                  onEdit={onEdit}
                  onDelete={onDelete}
                  onResolveConflict={onResolveConflict}
                  onViewDetail={onViewDetail}
                  canEdit={canEdit}
                />
              ))
            )}
          </tbody>
        </table>
      </div>

      <ScheduleTablePagination totalFiltered={totalFiltered} page={safePage} totalPages={totalPages} onPageChange={setPage} />
    </div>
  );
}
