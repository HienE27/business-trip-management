"use client";

import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { FixedSizeList } from "react-window";
import { EmptyState } from "@/components/ui/EmptyState";
import {
  type ScheduleTableViewProps,
  type SortKey,
  PAGE_SIZE,
} from "./schedule-table/constants";
import {
  applyTablePipeline,
  countActiveFilters,
  EMPTY_FILTERS,
  type TableFilters,
} from "./schedule-table/filterSort";
import { ScheduleTableToolbar } from "./schedule-table/ScheduleTableToolbar";
import {
  ScheduleTableHeader,
  ScheduleTablePagination,
} from "./schedule-table/ScheduleTablePagination";
import { TABLE_HEADERS } from "./schedule-table/ScheduleTablePagination";
import { ScheduleTableRow } from "./schedule-table/ScheduleTableRow";
import type { FilterConflict } from "./schedule-table/constants";
import type { Schedule } from "@/types/api";

const ROW_HEIGHT = 60; // px per virtualized row

// Column widths matching TABLE_HEADERS — must match the actual td widths
const COL_WIDTHS: number[] = [96, 64, 112, 176, 80, 112, 112];
const TOTAL_WIDTH = COL_WIDTHS.reduce((a, b) => a + b, 0);

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
  const [searchInput, setSearchInput] = useState("");

  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingSearchRef = useRef<string | null>(null);
  useEffect(() => {
    pendingSearchRef.current = searchInput;
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTimerRef.current = setTimeout(() => {
      setFilters((p) =>
        p.search !== pendingSearchRef.current
          ? { ...p, search: pendingSearchRef.current ?? "" }
          : p
      );
    }, 300);
    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    };
  }, [searchInput]);

  const { pageData, safePage, totalPages, totalFiltered } = useMemo(
    () => applyTablePipeline(schedules, filters, page),
    [schedules, filters, page]
  );

  const conflictCount = useMemo(
    () => pageData.filter((s) => s.hasConflict).length,
    [pageData]
  );
  const activeCount = countActiveFilters(filters);

  const handleSort = useCallback(
    (key: SortKey) => {
      setPage(1);
      setFilters((prev) =>
        prev.sortKey === key
          ? { ...prev, sortDir: prev.sortDir === "asc" ? "desc" : "asc" }
          : { ...prev, sortKey: key, sortDir: "asc" }
      );
    },
    []
  );

  const handleReset = useCallback(() => {
    setPage(1);
    setSearchInput("");
    setFilters(EMPTY_FILTERS);
  }, []);

  // Keyboard shortcut
  useEffect(() => {
    const searchId = `${uid}-search`;
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        document.getElementById(searchId)?.focus();
      } else if (e.key === "Escape" && searchInput) {
        setSearchInput("");
        setFilters((p) => (p.search ? { ...p, search: "" } : p));
      } else if (
        e.key === "/" &&
        target.tagName !== "INPUT" &&
        target.tagName !== "TEXTAREA" &&
        target.tagName !== "SELECT"
      ) {
        e.preventDefault();
        document.getElementById(searchId)?.focus();
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [uid, searchInput]);

  // Reset scroll on page change
  const scrollRef = useRef<HTMLDivElement>(null);
  const prevPage = useRef(page);
  useEffect(() => {
    if (prevPage.current !== page && scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
    prevPage.current = page;
  }, [page]);

  // Auto-enable when dataset is large
  const useVirtualization = totalFiltered > 100 && pageData.length > 30;

  // Virtual row: renders one row using CSS grid to avoid table-in-table issues
  const VirtualRow = useCallback(
    ({ index, style }: { index: number; style: React.CSSProperties }) => {
      const s = pageData[index];
      const tone = ScheduleTableRow.displayName; // just for reference
      return (
        <div
          style={{
            ...style,
            display: "grid",
            gridTemplateColumns: COL_WIDTHS.map((w) => `${w}px`).join(" "),
            borderBottom: "1px solid var(--color-outline-variant, #c3c6d7)",
            background: "white",
          }}
          className="group hover:bg-primary-fixed/10 transition-colors"
          onClick={() => onViewDetail?.(s)}
          role="row"
          aria-rowindex={index + 2}
        >
          <CellValue index={0} schedule={s} />
          <CellValue index={1} schedule={s} />
          <CellValue index={2} schedule={s} />
          <CellValue index={3} schedule={s} />
          <CellValue index={4} schedule={s} />
          <CellValue index={5} schedule={s} />
          <ActionCell
            index={6}
            schedule={s}
            canEdit={canEdit}
            onEdit={onEdit}
            onDelete={onDelete}
            onResolveConflict={onResolveConflict}
            onViewDetail={onViewDetail}
          />
        </div>
      );
    },
    [pageData, canEdit, onEdit, onDelete, onResolveConflict, onViewDetail]
  );

  return (
    <div className="flex flex-col h-full">
      <ScheduleTableToolbar
        schedules={schedules}
        filters={filters}
        searchValue={searchInput}
        onSearchChange={(v) => {
          setPage(1);
          setSearchInput(v);
        }}
        onTypeChange={(v) => {
          setPage(1);
          setFilters((p) => ({ ...p, filterType: v }));
        }}
        onStaffChange={(v) => {
          setPage(1);
          setFilters((p) => ({ ...p, filterStaff: v }));
        }}
        onConflictChange={(v: FilterConflict) => {
          setPage(1);
          setFilters((p) => ({ ...p, filterConflict: v }));
        }}
        onDateFromChange={(v) => {
          setPage(1);
          setFilters((p) => ({ ...p, dateFrom: v }));
        }}
        onDateToChange={(v) => {
          setPage(1);
          setFilters((p) => ({ ...p, dateTo: v }));
        }}
        onReset={handleReset}
        resultCount={totalFiltered}
        conflictCount={conflictCount}
      />

      {/* Table container */}
      <div className="flex-1 overflow-hidden flex flex-col border border-outline-variant rounded-b-lg">
        {/* Sticky header grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: COL_WIDTHS.map((w) => `${w}px`).join(" "),
            width: TOTAL_WIDTH,
            background: "var(--color-surface-container-low, #f2f4f6)",
            borderBottom: "1px solid var(--color-outline-variant, #c3c6d7)",
            position: "sticky",
            top: 0,
            zIndex: 10,
          }}
          role="rowgroup"
        >
          {TABLE_HEADERS.map((h, i) => (
            <div
              key={i}
              className={`px-3 py-3 border-r border-outline-variant shrink-0 ${h.className}`}
              role="columnheader"
              aria-colindex={i + 1}
            >
              <span className="text-label-sm font-semibold text-on-surface-variant uppercase tracking-wide block truncate">
                {h.label}
              </span>
            </div>
          ))}
        </div>

        {/* Scrollable body */}
        <div ref={scrollRef} className="flex-1 overflow-auto" role="rowgroup">
          {pageData.length === 0 ? (
            <div className="p-8">
              <EmptyState
                icon={activeCount > 0 ? "filter_alt_off" : "event_busy"}
                title={
                  activeCount > 0
                    ? "Không có lịch phù hợp"
                    : "Chưa có lịch nào"
                }
                description={
                  activeCount > 0
                    ? "Thử điều chỉnh bộ lọc hoặc xóa bộ lọc để xem tất cả lịch."
                    : "Khi có lịch trực được tạo, chúng sẽ xuất hiện tại đây."
                }
                action={
                  activeCount > 0 ? (
                    <button
                      type="button"
                      onClick={handleReset}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-on-primary text-label-sm font-semibold hover:bg-primary/90 transition-colors"
                    >
                      <span
                        aria-hidden="true"
                        className="material-symbols-outlined text-[16px]"
                      >
                        filter_alt_off
                      </span>
                      Xóa tất cả bộ lọc
                    </button>
                  ) : undefined
                }
              />
            </div>
          ) : useVirtualization ? (
            <div style={{ width: TOTAL_WIDTH }}>
              <FixedSizeList
                width={TOTAL_WIDTH}
                height={Math.min(pageData.length * ROW_HEIGHT, 600)}
                itemCount={pageData.length}
                itemSize={ROW_HEIGHT}
                overscanCount={5}
              >
                {VirtualRow}
              </FixedSizeList>
            </div>
          ) : (
            <table
              className="w-full border-collapse text-label-sm"
              style={{ width: TOTAL_WIDTH }}
              aria-label="Danh sách lịch trực"
            >
              <tbody className="divide-y divide-outline-variant/40">
                {pageData.map((s) => (
                  <ScheduleTableRow
                    key={s.id}
                    schedule={s}
                    onEdit={onEdit}
                    onDelete={onDelete}
                    onResolveConflict={onResolveConflict}
                    onViewDetail={onViewDetail}
                    canEdit={canEdit}
                  />
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <ScheduleTablePagination
        totalFiltered={totalFiltered}
        page={safePage}
        totalPages={totalPages}
        onPageChange={setPage}
      />
    </div>
  );
}

// Inline cell renderers for virtualized rows (avoid ScheduleTableRow overhead)
function CellValue({ index, schedule }: { index: number; schedule: Schedule }) {
  const dateObj = new Date(schedule.workDate + "T00:00:00");
  const dow = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"][dateObj.getDay()];
  const dateShort = dateObj.toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
  });

  if (index === 0) {
    return (
      <div className="px-3 py-3.5 flex flex-col leading-snug gap-0.5">
        <span className="text-label-sm font-semibold text-on-surface tabular-nums">
          {dateShort}
        </span>
        <span className="text-label-sm text-on-surface-variant">
          {dateObj.getFullYear()}
        </span>
      </div>
    );
  }
  if (index === 1) {
    return (
      <div className="px-3 py-3.5 flex items-center">
        <span
          className={`text-label-sm font-bold tabular-nums ${
            dow === "CN" || dow === "T7" ? "text-error" : "text-on-surface"
          }`}
        >
          {dow}
        </span>
      </div>
    );
  }
  if (index === 2) {
    const TONE = {
      L01: {
        bg: "bg-red-50",
        text: "text-red-700",
        label: "Trực 24/24",
        dot: "bg-red-500",
      },
      L02: {
        bg: "bg-blue-50",
        text: "text-blue-700",
        label: "Thông tầm",
        dot: "bg-blue-500",
      },
      L03: {
        bg: "bg-green-50",
        text: "text-green-700",
        label: "Dịch vụ",
        dot: "bg-green-500",
      },
      L04: {
        bg: "bg-purple-50",
        text: "text-purple-700",
        label: "Chuyên gia",
        dot: "bg-purple-500",
      },
    };
    const tone =
      TONE[schedule.shiftType.id as keyof typeof TONE] ?? {
        bg: "bg-surface-container-low",
        text: "text-on-surface",
        label: schedule.shiftType.name,
        dot: "bg-outline",
      };
    return (
      <div className="px-3 py-3.5 flex items-center gap-1.5">
        <span
          aria-hidden="true"
          className={`w-2 h-2 rounded-full shrink-0 ${tone.dot}`}
        />
        <span
          className={`px-2 py-1 rounded-md text-label-sm font-bold whitespace-nowrap leading-none ${tone.bg} ${tone.text}`}
        >
          {tone.label}
        </span>
      </div>
    );
  }
  if (index === 3) {
    return (
      <div className="px-3 py-3.5 flex items-center gap-2 min-w-0">
        <div
          className={`w-8 h-8 rounded-full shrink-0 flex items-center justify-center ${schedule.shiftType.id === "L01" ? "bg-red-50" : schedule.shiftType.id === "L02" ? "bg-blue-50" : schedule.shiftType.id === "L03" ? "bg-green-50" : "bg-purple-50"}`}
        >
          <span className="text-[11px] font-bold">
            {schedule.staff.fullName
              .split(" ")
              .slice(-2)
              .map((p) => p[0])
              .join("")
              .toUpperCase()}
          </span>
        </div>
        <div className="min-w-0">
          <p className="text-label-sm font-semibold text-on-surface truncate leading-snug">
            {schedule.staff.fullName}
          </p>
          {schedule.staff.specialty && (
            <p className="text-label-sm text-on-surface-variant truncate leading-snug">
              {schedule.staff.specialty}
            </p>
          )}
        </div>
      </div>
    );
  }
  if (index === 4) {
    if (schedule.staff2) {
      return (
        <div className="px-3 py-3.5 flex items-center gap-2 min-w-0">
          <div
            className={`w-8 h-8 rounded-full shrink-0 flex items-center justify-center ${schedule.shiftType.id === "L01" ? "bg-red-50" : schedule.shiftType.id === "L02" ? "bg-blue-50" : schedule.shiftType.id === "L03" ? "bg-green-50" : "bg-purple-50"}`}
          >
            <span className="text-[11px] font-bold">
              {schedule.staff2.fullName
                .split(" ")
                .slice(-2)
                .map((p) => p[0])
                .join("")
                .toUpperCase()}
            </span>
          </div>
          <div className="min-w-0">
            <p className="text-label-sm font-semibold text-on-surface truncate leading-snug">
              {schedule.staff2.fullName}
            </p>
          </div>
        </div>
      );
    }
    return (
      <div className="px-3 py-3.5 flex items-center">
        <span className="text-label-sm text-on-surface-variant">—</span>
      </div>
    );
  }
  if (index === 5) {
    return (
      <div className="px-3 py-3.5 flex items-center gap-1.5">
        <span
          className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold border ${
            schedule.hasConflict
              ? "bg-red-50 border-red-200 text-red-700"
              : "bg-green-50 border-green-200 text-green-700"
          }`}
        >
          {schedule.hasConflict ? (
            <>
              <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
              {schedule.conflictCount ?? 1} xung đột
            </>
          ) : (
            <>
              <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
              Bình thường
            </>
          )}
        </span>
      </div>
    );
  }
  return null;
}

function ActionCell({
  index,
  schedule,
  canEdit,
  onEdit,
  onDelete,
  onResolveConflict,
  onViewDetail,
}: {
  index: number;
  schedule: Schedule;
  canEdit: boolean;
  onEdit?: (s: Schedule) => void;
  onDelete?: (s: Schedule) => void;
  onResolveConflict?: (s: Schedule) => void;
  onViewDetail?: (s: Schedule) => void;
}) {
  return (
    <div
      className="px-3 py-3.5 flex items-center justify-end gap-1"
      style={{ gridColumn: index + 1 }}
    >
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 transition-opacity">
        {canEdit && (
          <>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onResolveConflict?.(schedule);
              }}
              disabled={!schedule.hasConflict}
              className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-tertiary hover:bg-orange-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
              title="Giải quyết xung đột"
            >
              <span className="material-symbols-outlined text-[18px]">flash_on</span>
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onEdit?.(schedule);
              }}
              className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-secondary hover:bg-green-100 transition-colors"
              title="Chỉnh sửa"
            >
              <span className="material-symbols-outlined text-[18px]">edit</span>
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onDelete?.(schedule);
              }}
              className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-error hover:bg-red-100 transition-colors"
              title="Xóa"
            >
              <span className="material-symbols-outlined text-[18px]">delete</span>
            </button>
          </>
        )}
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onViewDetail?.(schedule);
          }}
          className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-primary hover:bg-blue-100 transition-colors"
          title="Xem chi tiết"
        >
          <span className="material-symbols-outlined text-[18px]">visibility</span>
        </button>
      </div>
    </div>
  );
}
