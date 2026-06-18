"use client";

import { useCallback, useEffect, useId, useMemo, useState } from "react";
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

  // Reset page về 1 khi filter thay đổi
  useEffect(() => {
    setPage(1);
  }, [filters]);

  const { pageData, safePage, totalPages, totalFiltered } = useMemo(
    () => applyTablePipeline(schedules, filters, page),
    [schedules, filters, page],
  );

  const conflictCount = useMemo(() => schedules.filter((s) => s.hasConflict).length, [schedules]);
  const activeCount = countActiveFilters(filters);

  const handleSort = useCallback((key: SortKey) => {
    setFilters((prev) => (prev.sortKey === key ? { ...prev, sortDir: prev.sortDir === "asc" ? "desc" : "asc" } : { ...prev, sortKey: key, sortDir: "asc" }));
  }, []);

  const handleReset = useCallback(() => setFilters(EMPTY_FILTERS), []);

  // Ctrl+K / "/" focus search
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        document.getElementById(`${uid}-search`)?.focus();
      } else if (e.key === "/" && document.activeElement?.tagName !== "INPUT" && document.activeElement?.tagName !== "TEXTAREA") {
        e.preventDefault();
        document.getElementById(`${uid}-search`)?.focus();
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [uid]);

  return (
    <div className="flex flex-col h-full">
      <ScheduleTableToolbar
        schedules={schedules}
        filters={filters}
        onSearchChange={(v) => setFilters((p) => ({ ...p, search: v }))}
        onTypeChange={(v) => setFilters((p) => ({ ...p, filterType: v }))}
        onStaffChange={(v) => setFilters((p) => ({ ...p, filterStaff: v }))}
        onConflictChange={(v: FilterConflict) => setFilters((p) => ({ ...p, filterConflict: v }))}
        onDateFromChange={(v) => setFilters((p) => ({ ...p, dateFrom: v }))}
        onDateToChange={(v) => setFilters((p) => ({ ...p, dateTo: v }))}
        onReset={handleReset}
        resultCount={totalFiltered}
        conflictCount={conflictCount}
      />

      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse text-label-sm">
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

      <ScheduleTablePagination totalItems={totalFiltered} page={safePage} totalPages={totalPages} onPageChange={setPage} />
    </div>
  );
}
