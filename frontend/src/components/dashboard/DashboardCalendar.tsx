"use client";

import { useState, useMemo, useRef, useEffect, useCallback, memo } from "react";
import type { Schedule } from "@/types/api";
import { EmptyState } from "@/components/ui/EmptyState";
import {
  addDays,
  formatFullDate,
  getStaffCode,
  MAX_VISIBLE_GROUPS,
  shiftTypeToTone,
  SHIFT_SHORT,
  summarizeItems,
  TONE,
  WEEKDAYS,
  weekStartOf,
  type CalendarAnnotation,
  type CalendarCell,
  type CalendarItem,
  type CalendarViewMode,
} from "./calendar/constants";
import { buildCalendar } from "./calendar/buildCalendar";
import { MobileHint } from "./calendar/MobileHint";
import { EventTooltip, type TooltipData } from "./calendar/EventTooltip";
import { OverflowPopover } from "./calendar/OverflowPopover";
import { CalendarToolbar } from "./calendar/CalendarToolbar";

// ─── Tooltip ─────────────────────────────────────────────────────────────────
// Sub-components (MobileHint, EventTooltip, OverflowPopover) đã được tách ra folder ./calendar/.

type DashboardCalendarProps = {
  schedules?: Schedule[];
  annotations?: CalendarAnnotation[];
  coverages?: Record<string, { required: number; assigned: number }>;
  staffList?: { id: number; fullName: string }[];
  staffFilter?: number | null;
  specialtyList?: { id: number; name: string }[];
  specialtyFilter?: number | null;
  initialYear?: number;
  initialMonth?: number;
  onEditSchedule?: (schedule: Schedule) => void;
  onDeleteSchedule?: (schedule: Schedule) => void;
  onResolveConflict?: (schedule: Schedule) => void;
  onViewDetail?: (schedule: Schedule) => void;
  onDayClick?: (date: Date, items: CalendarItem[]) => void;
  onAddClick?: (date: Date) => void;
  onStaffFilterChange?: (staffId: number | null) => void;
  onSpecialtyFilterChange?: (specialtyId: number | null) => void;
  /** Callback khi inline edit trong OverflowPopover cần refresh data. */
  onRefresh?: () => void;
  /** Khi true: ẩn toolbar filter (dashboard read-only). */
  hideFilters?: boolean;
  /** Tab hiện tại (L01..L04 / ALL). Khi truyền, dùng để sync active state của chip filter. */
  selectedTab?: string;
  /** Callback khi user click chip filter. Khi truyền, click chip sẽ gọi callback này thay vì set state nội bộ. */
  onFilterTypeChange?: (filter: string) => void;
};
// ─── Main Calendar ────────────────────────────────────────────────────────────
export const DashboardCalendar = memo(function DashboardCalendar({
  schedules = [],
  annotations = [],
  coverages = {},
  initialYear,
  initialMonth,
  onEditSchedule,
  onDeleteSchedule,
  onResolveConflict,
  onViewDetail,
  onDayClick,
  onAddClick,
  onRefresh,
  hideFilters = false,
  selectedTab,
  onFilterTypeChange,
}: DashboardCalendarProps) {
  const [filterType, setFilterType] = useState<string>("all");
  const activeFilter = selectedTab ?? filterType;
  const setActiveFilter = onFilterTypeChange ?? setFilterType;
  const [currentYear, setCurrentYear] = useState(() => initialYear ?? new Date().getFullYear());
  const [currentMonth, setCurrentMonth] = useState(() => initialMonth ?? new Date().getMonth());
  const [currentWeekStart, setCurrentWeekStart] = useState(() => weekStartOf(new Date()));
  const [viewMode, setViewMode] = useState<CalendarViewMode>("month");
  const [tooltip, setTooltip] = useState<TooltipData | null>(null);
  const [overflow, setOverflow] = useState<{ items: CalendarItem[]; anchor: { x: number; y: number } } | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [isMobile, setIsMobile] = useState(false);
  // Roving tabindex: track focused cell index for arrow-key navigation
  const [focusedCellIndex, setFocusedCellIndex] = useState(0);
  const calendarRef = useRef<HTMLDivElement>(null);
  const cellRefs = useRef<Map<number, HTMLElement>>(new Map());
  const currentMonthRef = useRef(currentMonth);
  useEffect(() => {
    currentMonthRef.current = currentMonth;
  }, [currentMonth]);

  useEffect(() => {
    const mq = window.matchMedia("(max-width: 1023px)");
    setIsMobile(mq.matches);
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);

  const filteredSchedules = useMemo(() => {
    const f = activeFilter;
    if (!f || f === "all" || f === "ALL") return schedules;
    return schedules.filter((s) => s.shiftType.id === f);
  }, [schedules, activeFilter]);

  const { cells, today } = useMemo(() => buildCalendar(filteredSchedules, annotations, currentYear, currentMonth), [annotations, filteredSchedules, currentYear, currentMonth]);

  // Reset focused cell when month/year changes
  useEffect(() => {
    setFocusedCellIndex(0);
  }, [currentYear, currentMonth]);

  // Close tooltip on scroll and Escape
  useEffect(() => {
    const handleScroll = () => setTooltip(null);
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") setTooltip(null); };
    window.addEventListener("scroll", handleScroll, { passive: true });
    document.addEventListener("keydown", handleKey);
    return () => {
      window.removeEventListener("scroll", handleScroll);
      document.removeEventListener("keydown", handleKey);
    };
  }, []);

  const handleMouseEnter = useCallback((e: React.MouseEvent, item: CalendarItem) => {
    setTooltip({ x: e.clientX, y: e.clientY, item });
  }, []);

  const handleMouseLeave = useCallback(() => setTooltip(null), []);

  const handleOverflowClick = useCallback((e: React.MouseEvent, items: CalendarItem[]) => {
    e.stopPropagation();
    setOverflow({ items, anchor: { x: e.clientX, y: e.clientY } });
  }, []);

  const openCell = useCallback((cell: CalendarCell, anchor?: { x: number; y: number }) => {
    if (cell.isCompensation) return;
    if (cell.items.length === 0) {
      onAddClick?.(cell.date);
      return;
    }
    onDayClick?.(cell.date, cell.items);
    setOverflow({
      items: cell.items,
      anchor: anchor ?? { x: window.innerWidth / 2, y: Math.max(120, window.innerHeight / 4) },
    });
  }, [onAddClick, onDayClick]);

  const handleCellClick = useCallback((event: React.MouseEvent, cell: CalendarCell) => {
    if (cell.isCompensation) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    openCell(cell, { x: event.clientX, y: event.clientY });
  }, [openCell]);

  const handleCellKeyDown = useCallback((event: React.KeyboardEvent, cell: CalendarCell) => {
    if (event.key !== "Enter" && event.key !== " ") return;
    if (cell.isCompensation) {
      event.preventDefault();
      return;
    }
    event.preventDefault();
    openCell(cell);
  }, [openCell]);

  const isToday = (cell: CalendarCell) =>
    cell.date.getTime() === today.getTime();

  const weekCells = useMemo(() => {
    const start = new Date(currentWeekStart);
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      const daySchedules = filteredSchedules.filter((s) => s.workDate.split("T")[0] === dateStr);
      return {
        date: d,
        dateStr,
        isWeekend: i >= 5,
        dayLabel: WEEKDAYS[i],
        items: daySchedules.map((s) => ({
          shiftLabel: SHIFT_SHORT[s.shiftType.id] ?? s.shiftType.id,
          staffName: s.staff.fullName ?? "—",
          staffCode: getStaffCode(s.staff.fullName ?? ""),
          tone: shiftTypeToTone(s.shiftType.id),
          shiftTypeId: s.shiftType.id,
          isOvernight: s.shiftType.id === "L01",
          schedule: s,
        })),
        annotations: annotations.filter((a) => a.date === dateStr),
        isCompensation: annotations.some((a) => a.date === dateStr && (a.tone === "compLeave" || a.isCompensation)),
        hasConflict: daySchedules.some((s) => s.hasConflict),
      };
    });
  }, [currentWeekStart, filteredSchedules, annotations]);

  const monthLabel = useMemo(() => {
    const months = ["Tháng 1","Tháng 2","Tháng 3","Tháng 4","Tháng 5","Tháng 6","Tháng 7","Tháng 8","Tháng 9","Tháng 10","Tháng 11","Tháng 12"];
    return `${months[currentMonth]} năm ${currentYear}`;
  }, [currentMonth, currentYear]);

  const weekLabel = useMemo(() => {
    const start = new Date(currentWeekStart);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt = (d: Date) => d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
    return `${fmt(start)} – ${fmt(end)}`;
  }, [currentWeekStart]);

  const navigateMonth = useCallback((direction: -1 | 1) => {
    const nm = direction === -1
      ? (currentMonth === 0 ? 11 : currentMonth - 1)
      : (currentMonth === 11 ? 0 : currentMonth + 1);
    const ny = direction === -1
      ? (currentMonth === 0 ? currentYear - 1 : currentYear)
      : (currentMonth === 11 ? currentYear + 1 : currentYear);
    setCurrentMonth(nm);
    setCurrentYear(ny);
  }, [currentMonth, currentYear]);

  const goToToday = useCallback(() => {
    const now = new Date();
    setCurrentYear(now.getFullYear());
    setCurrentMonth(now.getMonth());
    setCurrentWeekStart(weekStartOf(now));
  }, []);

  // Show a brief skeleton when month changes to indicate work is happening
  const [isSwitchingMonth, setIsSwitchingMonth] = useState(false);
  useEffect(() => {
    if (!isSwitchingMonth) return;
    const t = setTimeout(() => setIsSwitchingMonth(false), 150);
    return () => clearTimeout(t);
  }, [isSwitchingMonth]);
  useEffect(() => {
    setIsSwitchingMonth(true);
  }, [currentYear, currentMonth]);

  // Keyboard shortcuts: ←/→ for prev/next month, Home for today
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const tag = (document.activeElement?.tagName ?? "").toLowerCase();
      if (tag === "input" || tag === "textarea" || tag === "select") return;
      if (e.altKey || e.ctrlKey || e.metaKey) return;
      if (e.key === "ArrowLeft") {
        e.preventDefault();
        if (viewMode === "week") setCurrentWeekStart((d) => addDays(d, -7));
        else {
          setCurrentMonth((m) => {
            const nm = m === 0 ? 11 : m - 1;
            return nm;
          });
          setCurrentYear((y) => {
            if (currentMonthRef.current === 0) return y - 1;
            return y;
          });
        }
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        if (viewMode === "week") setCurrentWeekStart((d) => addDays(d, 7));
        else {
          setCurrentMonth((m) => {
            const nm = m === 11 ? 0 : m + 1;
            return nm;
          });
          setCurrentYear((y) => {
            if (currentMonthRef.current === 11) return y + 1;
            return y;
          });
        }
      } else if (e.key === "Home") {
        e.preventDefault();
        const now = new Date();
        setCurrentYear(now.getFullYear());
        setCurrentMonth(now.getMonth());
        setCurrentWeekStart(weekStartOf(now));
      } else if (e.key === "t" || e.key === "T") {
        e.preventDefault();
        setViewMode("month");
      } else if (e.key === "w" || e.key === "W") {
        e.preventDefault();
        setViewMode("week");
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [viewMode]);

  return (
    <section className="flex flex-col h-full" ref={calendarRef}>
      <CalendarToolbar
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        monthLabel={monthLabel}
        weekLabel={weekLabel}
        onPrev={() => navigateMonth(-1)}
        onNext={() => navigateMonth(1)}
        onToday={goToToday}
        activeFilter={activeFilter}
        onFilterChange={setActiveFilter}
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((v) => !v)}
      />

      {/* Mobile hint banner (first-time) */}
      {isMobile && <MobileHint />}

      {/* ── Week View ─────────────────────────────────────────────────── */}
      {viewMode === "week" && (
        <div className="flex-1 flex min-h-0">
          <div className="flex-1 flex flex-col min-w-0">
            {/* Day headers */}
            <div className="grid grid-cols-7 shrink-0">
              {weekCells.map((cell, i) => (
                <div
                  key={cell.dayLabel}
                  className={`py-2 text-center text-label-sm font-semibold border-b border-r border-outline-variant bg-surface-container-low ${
                    i >= 5 ? "text-error" : "text-on-surface-variant"
                  }`}
                >
                  <div>{cell.dayLabel}</div>
                  <div className={`text-label-xs font-normal ${cell.date.toDateString() === today.toDateString() ? "text-primary font-bold" : ""}`}>
                    {cell.date.getDate()}
                  </div>
                </div>
              ))}
            </div>

            {/* Week cells */}
            <div className="flex-1 grid grid-cols-7 flex-1 auto-rows-fr" style={{ minHeight: 0 }}>
              {weekCells.map((cell) => {
                const t = TONE;
                return (
                  <div
                    key={cell.dateStr}
                    onClick={(event) => handleCellClick(event, cell as unknown as CalendarCell)}
                    onKeyDown={(event) => handleCellKeyDown(event, cell as unknown as CalendarCell)}
                    role="button"
                    tabIndex={cell.isCompensation ? -1 : 0}
                    aria-label={
                      cell.isCompensation
                        ? `${formatFullDate(cell.date)}: ngày nghỉ bù (khóa)`
                        : `${formatFullDate(cell.date)}: ${cell.items.length} lịch${cell.hasConflict ? ", có xung đột" : ""}${cell.annotations.length > 0 ? `, ${cell.annotations.length} ghi chú` : ""}`
                    }
                    aria-disabled={cell.isCompensation}
                    className={`
                      border-r border-b border-outline-variant flex flex-col
                      min-h-[200px] group relative
                      transition-colors hover:bg-surface-container-low
                      ${cell.isCompensation ? "cursor-not-allowed" : "cursor-pointer"}
                      ${cell.date.toDateString() === today.toDateString() ? "bg-primary/5" : ""}
                      ${cell.hasConflict ? "ring-2 ring-inset ring-error" : ""}
                      focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary
                    `}
                  >
                    {/* Conflict indicator */}
                    {cell.hasConflict && (
                      <div className="absolute top-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-error-container text-error text-label-sm font-bold z-10">
                        <span className="material-symbols-outlined text-label-sm">warning</span>
                      </div>
                    )}
                    {/* Compensation indicator */}
                    {cell.isCompensation && (
                      <div className="absolute top-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-surface-container-high text-outline text-label-sm font-bold z-10">
                        <span className="material-symbols-outlined text-label-sm">lock</span>
                      </div>
                    )}

                    {/* Schedule items */}
                    <div className="flex-1 flex flex-col gap-0.5 p-0.5 min-h-0 overflow-hidden z-20">
                      {cell.annotations.map((ann) => {
                        if (ann.tone === "compLeave" || ann.isCompensation) return null;
                        const annTone = TONE[ann.tone ?? "neutral"] ?? TONE.neutral;
                        return (
                          <div
                            key={`${cell.dateStr}-${ann.label}`}
                            className={`flex items-center gap-1 px-1.5 py-0.5 rounded border-l-2 min-h-[22px] max-h-[22px] overflow-hidden ${annTone.bg} ${annTone.border}`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${annTone.dot}`} />
                            <span className={`text-label-sm font-bold leading-tight truncate ${annTone.text}`}>{ann.label}</span>
                          </div>
                        );
                      })}
                      {cell.items.map((item, i) => {
                        const st = t[item.tone] ?? t.neutral;
                        const workDate = new Date(item.schedule.workDate);
                        const nextDate = new Date(workDate);
                        nextDate.setDate(nextDate.getDate() + 1);
                        const dateFmt = (d: Date) =>
                          `${d.getDate()}/${d.getMonth() + 1}`;
                        return (
                          <div
                            key={`week-item-${item.schedule.id}-${i}`}
                            onMouseEnter={(e) => handleMouseEnter(e, item)}
                            onMouseLeave={handleMouseLeave}
                            className={`flex items-center gap-1 px-1.5 py-1 rounded border-l-2 min-h-[24px] max-h-[32px] overflow-hidden cursor-pointer transition-colors hover:filter-[brightness-0.96] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary relative before:content-[''] before:absolute before:inset-x-0 before:-inset-y-2 before:z-10 ${st.bg} ${st.border}`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${st.dot}`} />
                            <span className={`text-label-sm font-bold leading-tight truncate ${st.text}`}>{item.shiftLabel}</span>
                            <span className="text-label-sm leading-tight text-on-surface-variant/60 shrink-0">·</span>
                            <span className="text-label-sm leading-tight text-on-surface font-semibold truncate">{item.staffCode}</span>
                            {item.isOvernight && (
                              <span className="text-label-sm leading-tight text-on-surface-variant/80 shrink-0 truncate tabular-nums">
                                7h30→{dateFmt(nextDate)}
                              </span>
                            )}
                          </div>
                        );
                      })}
                      {cell.items.length === 0 && cell.annotations.length === 0 && !cell.isCompensation && (
                        <button
                          type="button"
                          className="flex items-center justify-center min-h-[24px] text-body-sm text-on-surface-variant/60 hover:text-primary/80 transition-colors w-full"
                          onClick={() => onAddClick?.(cell.date)}
                          aria-label={`Thêm lịch cho ngày ${cell.date.getDate()}`}
                        >
                          <span className="material-symbols-outlined text-[16px]">add</span>
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          {/* Sidebar — Week summary (mobile: drawer overlay, desktop: inline) */}
          {sidebarOpen && (
            isMobile ? (
              <>
                <div
                  className="fixed inset-0 bg-black/50 z-40"
                  onClick={() => setSidebarOpen(false)}
                  aria-hidden="true"
                />
                <aside id="calendar-sidebar" className="fixed right-0 top-0 bottom-0 z-50 w-80 max-w-[calc(100vw-32px)] bg-surface-container-low border-l border-outline-variant overflow-y-auto flex flex-col gap-3 p-3 shadow-2xl">
                  <div className="flex items-center justify-between mb-1">
                    <p className="text-title-md text-on-surface font-semibold">Tổng kết tuần</p>
                    <button
                      type="button"
                      onClick={() => setSidebarOpen(false)}
                      className="p-2 rounded-lg hover:bg-surface-container-high text-on-surface-variant focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                      aria-label="Đóng thanh bên"
                    >
                      <span aria-hidden="true" className="material-symbols-outlined text-[20px]">close</span>
                    </button>
                  </div>
                  <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                    <p className="text-label-sm font-semibold text-on-surface-variant mb-2">Tổng kết</p>
                    <div className="space-y-2">
                      {[
                        { label: "Tổng ca", value: weekCells.reduce((s, c) => s + c.items.length, 0), color: "text-primary" },
                        { label: "Xung đột", value: weekCells.filter((c) => c.hasConflict).length, color: "text-error" },
                        { label: "Ngày nghỉ bù", value: weekCells.filter((c) => c.isCompensation).length, color: "text-outline" },
                      ].map((m) => (
                        <div key={m.label} className="flex justify-between items-center">
                          <span className="text-label-sm text-on-surface-variant">{m.label}</span>
                          <span className={`text-label-md font-bold tabular-nums ${m.color}`}>{m.value}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                  <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                    <p className="text-label-sm font-bold text-on-surface-variant mb-2 leading-tight">Loại lịch</p>
                    <div className="space-y-1.5">
                      {[
                        { label: "Trực 24/24", color: "bg-primary" },
                        { label: "Thông tầm", color: "bg-secondary" },
                        { label: "Dịch vụ", color: "bg-tertiary" },
                        { label: "Chuyên gia", color: "bg-expert" },
                      ].map((l) => (
                        <div key={l.label} className="flex items-center gap-2">
                          <div aria-hidden="true" className={`w-5 h-3 rounded-sm ${l.color}`} />
                          <span className="text-label-sm font-medium text-on-surface leading-tight">{l.label}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </aside>
              </>
            ) : (
            <aside id="calendar-sidebar" className="hidden lg:flex w-64 shrink-0 border-l border-outline-variant bg-surface-container-low overflow-y-auto flex-col gap-3 p-3">
              <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                <p className="text-label-sm font-semibold text-on-surface-variant mb-2">Tổng kết tuần</p>
                <div className="space-y-2">
                  {[
                    { label: "Tổng ca", value: weekCells.reduce((s, c) => s + c.items.length, 0), color: "text-primary" },
                    { label: "Xung đột", value: weekCells.filter((c) => c.hasConflict).length, color: "text-error" },
                    { label: "Ngày nghỉ bù", value: weekCells.filter((c) => c.isCompensation).length, color: "text-outline" },
                  ].map((m) => (
                    <div key={m.label} className="flex justify-between items-center">
                      <span className="text-label-sm text-on-surface-variant">{m.label}</span>
                      <span className={`text-label-md font-bold tabular-nums ${m.color}`}>{m.value}</span>
                    </div>
                  ))}
                </div>
              </div>
              {/* Legend */}
              <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                <p className="text-label-sm font-semibold text-on-surface-variant mb-2">Loại lịch</p>
                <div className="space-y-1.5">
                  {[
                    { label: "Trực 24/24", color: "bg-primary" },
                    { label: "Thông tầm", color: "bg-secondary" },
                    { label: "Dịch vụ", color: "bg-tertiary" },
                    { label: "Chuyên gia", color: "bg-expert" },
                  ].map((l) => (
                    <div key={l.label} className="flex items-center gap-2">
                      <div aria-hidden="true" className={`w-5 h-3 rounded-sm ${l.color}`} />
                      <span className="text-label-sm text-on-surface">{l.label}</span>
                    </div>
                  ))}
                </div>
              </div>
            </aside>
            ))}
        </div>
      )}

      {/* ── Calendar Grid ──────────────────────────────────────────── */}
      {viewMode === "month" && (
        <div className="flex-1 flex min-h-0">
        <div className="flex-1 flex flex-col min-w-0 min-h-[400px] relative">
          {/* Day headers */}
          <div className="grid grid-cols-7 shrink-0">
            {WEEKDAYS.map((d, i) => (
              <div
                key={d}
                className={`py-2.5 text-center text-label-md font-bold uppercase tracking-wider border-b border-r border-outline-variant bg-surface-container-low leading-tight ${
                  i >= 5 ? "text-error" : "text-on-surface-variant"
                }`}
              >
                {d}
              </div>
            ))}
          </div>

          {/* Skeleton overlay during month switch */}
          {isSwitchingMonth && (
            <div className="absolute inset-0 z-30 bg-surface/60 backdrop-blur-[1px] flex items-center justify-center pointer-events-none" role="status" aria-live="polite">
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface-container-lowest border border-outline-variant shadow-sm">
                <span aria-hidden="true" className="material-symbols-outlined text-primary text-[18px] animate-spin">progress_activity</span>
                <span className="text-label-sm text-on-surface font-medium">Đang tải...</span>
              </div>
            </div>
          )}

          {/* Cells / Empty state */}
          {filteredSchedules.length === 0 ? (
            <div className="flex-1 flex items-center justify-center p-8 bg-surface-container-low/30">
              <EmptyState
                icon="event_busy"
                title="Chưa có lịch nào trong tháng này"
                description="Lịch trực sẽ hiển thị ở đây khi được tạo. Hãy thử chuyển sang tháng khác hoặc tạo lịch mới."
              />
            </div>
          ) : (
          /* Outer wrapper: provides missing left border and last-row bottom border */
          <div className="border-l border-t border-b border-outline-variant">
          <div
            className="flex-1 grid grid-cols-7 flex-1 auto-rows-fr"
            style={{ minHeight: 0 }}
            role="grid"
            aria-label="Lịch tháng"
          >
            {cells.map((cell, cellIndex) => {
              const summaries = summarizeItems(cell.items);
              const visibleSummaries = summaries.slice(0, MAX_VISIBLE_GROUPS);
              const visibleStaffCount = visibleSummaries.reduce((sum, item) => sum + item.count, 0);
              const hiddenStaffCount = Math.max(0, cell.items.length - visibleStaffCount);
              const hasMore = hiddenStaffCount > 0;
              const t = TONE;

              const hasAnnotations = cell.annotations.length > 0;
              const primaryAnnotation = cell.annotations[0] ?? null;
              const isCompLocked = cell.isCompensation;
              const isFocused = cellIndex === focusedCellIndex;

              return (
                <div
                  key={cell.dateStr}
                  ref={(el) => {
                    if (el) cellRefs.current.set(cellIndex, el);
                    else cellRefs.current.delete(cellIndex);
                  }}
                  onClick={(event) => {
                    setFocusedCellIndex(cellIndex);
                    handleCellClick(event, cell);
                  }}
                  onKeyDown={(event) => {
                    setFocusedCellIndex(cellIndex);
                    // Arrow key navigation (roving tabindex)
                    const cols = 7;
                    let next = cellIndex;
                    if (event.key === "ArrowRight") next = Math.min(cells.length - 1, cellIndex + 1);
                    else if (event.key === "ArrowLeft") next = Math.max(0, cellIndex - 1);
                    else if (event.key === "ArrowDown") next = Math.min(cells.length - 1, cellIndex + cols);
                    else if (event.key === "ArrowUp") next = Math.max(0, cellIndex - cols);
                    else if (event.key === "Home") {
                      event.preventDefault();
                      const firstOfWeek = Math.floor(cellIndex / cols) * cols;
                      next = firstOfWeek;
                    } else if (event.key === "End") {
                      event.preventDefault();
                      const firstOfWeek = Math.floor(cellIndex / cols) * cols;
                      next = Math.min(cells.length - 1, firstOfWeek + cols - 1);
                    } else if (event.key === "PageUp") {
                      event.preventDefault();
                      const m = currentMonth === 0 ? 11 : currentMonth - 1;
                      const y = currentMonth === 0 ? currentYear - 1 : currentYear;
                      setCurrentMonth(m);
                      setCurrentYear(y);
                      return;
                    } else if (event.key === "PageDown") {
                      event.preventDefault();
                      const m = currentMonth === 11 ? 0 : currentMonth + 1;
                      const y = currentMonth === 11 ? currentYear + 1 : currentYear;
                      setCurrentMonth(m);
                      setCurrentYear(y);
                      return;
                    } else {
                      handleCellKeyDown(event, cell);
                      return;
                    }
                    if (next !== cellIndex) {
                      event.preventDefault();
                      setFocusedCellIndex(next);
                      cellRefs.current.get(next)?.focus();
                    }
                  }}
                  role="gridcell"
                  tabIndex={isCompLocked ? -1 : (isFocused ? 0 : -1)}
                  aria-label={
                    isCompLocked
                      ? `${formatFullDate(cell.date)}: ngày nghỉ bù (khóa)`
                      : `${formatFullDate(cell.date)}: ${cell.items.length} lịch${cell.hasConflict ? ", có xung đột" : ""}${cell.annotations.length > 0 ? `, ${cell.annotations.length} ghi chú` : ""}`
                  }
                  aria-disabled={isCompLocked}
                  className={`
                    border-r border-b border-outline-variant flex flex-col
                    min-h-[120px] group relative
                    transition-colors hover:bg-surface-container-low
                    ${isCompLocked ? "cursor-not-allowed" : "cursor-pointer"}
                    ${!cell.isCurrentMonth ? "bg-surface-variant/20" : ""}
                    ${cell.hasConflict ? "ring-2 ring-inset ring-error" : ""}
                    ${isCompLocked ? "comp-locked-stripes" : ""}
                    focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary
                  `}
                >
                  {/* Diagonal stripes overlay for locked comp days */}
                  {isCompLocked && (
                    <div
                      className="absolute inset-0 pointer-events-none z-10 opacity-30"
                      style={{
                        backgroundImage: "repeating-linear-gradient(45deg, var(--color-outline-variant) 0px, var(--color-outline-variant) 1px, transparent 1px, transparent 10px)",
                        backgroundSize: "14px 14px",
                      }}
                      aria-hidden="true"
                    />
                  )}

                  {/* Day number */}
                  <div className={`shrink-0 px-2 py-1.5 flex items-center justify-between z-20 leading-tight ${
                    isToday(cell)
                      ? "bg-primary text-on-primary rounded-tl-lg"
                      : cell.isWeekend
                      ? "text-error"
                      : cell.hasConflict
                      ? "text-error font-semibold"
                      : isCompLocked
                      ? "text-on-surface-variant"
                      : hasAnnotations
                      ? "text-on-surface"
                      : cell.isCurrentMonth
                      ? "text-on-surface"
                      : "text-on-surface-variant"
                  }`}>
                    <span className={`text-label-md font-bold leading-tight ${
                      isToday(cell) ? "" : "group-hover:text-primary"
                    }`}>
                      {cell.day}
                    </span>
                    {cell.hasConflict && !isToday(cell) ? (
                      <span className="material-symbols-outlined text-[11px] text-error">warning</span>
                    ) : isCompLocked ? (
                      <span className="material-symbols-outlined text-[11px] text-outline" title="Ngày nghỉ bù — khóa">lock</span>
                    ) : primaryAnnotation ? (
                      <span className="rounded-full bg-surface-container-high px-1.5 py-0.5 text-label-sm font-semibold text-on-surface-variant truncate max-w-[80px]" title={primaryAnnotation.label}>
                        {primaryAnnotation.label}
                      </span>
                    ) : null}
                  </div>

                  {/* Events */}
                  <div className="flex-1 flex flex-col gap-0.5 p-0.5 min-h-0 overflow-hidden z-20">
                    {cell.annotations.map((annotation) => {
                      if (annotation.tone === "compLeave" || annotation.isCompensation) return null;
                      const annotationTone = TONE[annotation.tone ?? "neutral"] ?? TONE.neutral;
                      return (
                        <div
                          key={`${cell.dateStr}-${annotation.label}`}
                          className={`flex items-center gap-1 px-1.5 py-0.5 rounded border-l-2 min-h-[22px] max-h-[22px] overflow-hidden ${annotationTone.bg} ${annotationTone.border}`}
                          title={annotation.description ?? annotation.label}
                        >
                          <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${annotationTone.dot}`} />
                          <span className={`text-label-sm font-bold leading-tight truncate ${annotationTone.text}`}>
                            {annotation.label}
                          </span>
                        </div>
                      );
                    })}
                    {visibleSummaries.map((summary) => {
                      const st = t[summary.tone] ?? t.neutral;
                      return (
                        <div
                          key={`month-summary-${cell.dateStr}-${summary.shiftTypeId}`}
                          onMouseEnter={(e) => setTooltip({ x: e.clientX, y: e.clientY, item: cell.items.find(i => i.shiftTypeId === summary.shiftTypeId) ?? cell.items[0] })}
                          onMouseLeave={() => setTooltip(null)}
                          onMouseMove={(e) => setTooltip((t) => t ? { ...t, x: e.clientX, y: e.clientY } : t)}
                          className={`flex items-center gap-1 rounded border-l-2 px-1.5 py-0.5 min-h-[24px] max-h-[24px] overflow-hidden ${st.bg} ${st.border}`}
                          title={`${summary.label}: ${summary.count} nhân sự`}
                        >
                          <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${st.dot}`} aria-hidden="true" />
                          <span className={`truncate text-label-sm font-bold leading-tight ${st.text}`}>
                            {summary.label}
                          </span>
                        </div>
                      );
                    })}

                    {/* Overflow */}
                    {hasMore && (
                      <button
                        type="button"
                        onClick={(e) => handleOverflowClick(e, cell.items)}
                        aria-label={`Xem thêm ${hiddenStaffCount} nhân sự khác trong ngày ${cell.day}`}
                        className="flex items-center justify-center gap-1 px-1.5 py-0.5 rounded text-label-sm font-medium text-on-surface-variant hover:bg-surface-container-high transition-colors min-h-[22px] max-h-[22px] w-full"
                      >
                        <span className="material-symbols-outlined text-[20px]" aria-hidden="true">expand_more</span>
                        +{hiddenStaffCount} nhân sự khác
                      </button>
                    )}

                    {/* Empty cell + hint */}
                    {!hasMore && cell.items.length === 0 && cell.annotations.length === 0 && !cell.isCompensation && cell.isCurrentMonth && (
                      <button
                        type="button"
                        className="flex items-center justify-center min-h-[24px] text-body-sm text-on-surface-variant/60 hover:text-primary/80 transition-colors w-full"
                        onClick={() => onAddClick?.(cell.date)}
                        aria-label={`Thêm lịch cho ngày ${cell.day}`}
                      >
                        <span className="material-symbols-outlined text-[16px]" aria-hidden="true">add</span>
                      </button>
                    )}
                  </div>

                    {/* Conflict count badge */}
                    {cell.hasConflict && (() => {
                      const conflictCount = cell.items.filter((i) => i.schedule.hasConflict).length;
                      return (
                        <div
                          className="absolute bottom-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-error-container text-error text-label-sm font-bold"
                          aria-label={`${conflictCount} xung đột trong ngày này`}
                        >
                          <span className="material-symbols-outlined text-label-sm" aria-hidden="true">warning</span>
                          {conflictCount}
                        </div>
                      );
                    })()}

                  {/* Coverage indicator badge - chỉ hiện khi thiếu/cần chú ý */}
                  {!cell.isCompensation && (() => {
                    const cov = coverages[cell.dateStr];
                    if (!cov) return null;
                    const isFull = cov.assigned >= cov.required;
                    const isEmpty = cov.assigned === 0;
                    if (isFull) return null;
                    const missing = cov.required - cov.assigned;
                    const tooltip = isEmpty
                      ? `Chưa phân công ai (yêu cầu ${cov.required})`
                      : `Thiếu ${missing} người (đã có ${cov.assigned}/${cov.required})`;
                    return (
                      <div
                        className={`absolute bottom-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-label-sm font-bold ${
                          isEmpty
                            ? "bg-surface-container-high text-on-surface-variant"
                            : "bg-tertiary-fixed text-on-tertiary-fixed"
                        }`}
                        title={tooltip}
                        aria-label={tooltip}
                      >
                        <span className="material-symbols-outlined text-[10px]" aria-hidden="true">
                          {isEmpty ? "person_off" : "warning"}
                        </span>
                        {cov.assigned}/{cov.required}
                      </div>
                    );
                  })()}
                </div>
              );
            })}
          </div>
          </div>
          )}
        </div>

        {/* ── Sidebar (collapsible, mobile: drawer, desktop: inline) ──── */}
        {sidebarOpen && (
          isMobile ? (
            <>
                <div
                  className="fixed inset-0 bg-black/50 z-40"
                  onClick={() => setSidebarOpen(false)}
                  aria-hidden="true"
                />
                <aside id="calendar-sidebar" className="fixed right-0 top-0 bottom-0 z-50 w-80 max-w-[calc(100vw-32px)] bg-surface-container-low border-l border-outline-variant overflow-y-auto flex flex-col gap-3 p-3 shadow-2xl">
                  <div className="flex items-center justify-between mb-1">
                    <p className="text-title-md text-on-surface font-semibold">Thống kê tháng</p>
                  <button
                    type="button"
                    onClick={() => setSidebarOpen(false)}
                    className="p-2 rounded-lg hover:bg-surface-container-high text-on-surface-variant focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    aria-label="Đóng thanh bên"
                  >
                    <span aria-hidden="true" className="material-symbols-outlined text-[20px]">close</span>
                  </button>
                </div>
                {/* Legend, Conflicts, Compensation — same as desktop but inside drawer */}
                <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                  <p className="text-label-sm font-bold text-on-surface-variant mb-2 leading-tight">Loại lịch</p>
                  <div className="space-y-1.5">
                    {[
                      { label: "Trực 24/24", color: "bg-primary", id: "duty24" },
                      { label: "Thông tầm", color: "bg-secondary", id: "allDay" },
                      { label: "Dịch vụ", color: "bg-tertiary", id: "serviceClinic" },
                      { label: "Chuyên gia", color: "bg-expert", id: "expertClinic" },
                    ].map((l) => (
                      <div key={l.id} className="flex items-center gap-2">
                        <div aria-hidden="true" className={`w-5 h-3 rounded-sm ${l.color}`} />
                        <span className="text-label-sm font-medium text-on-surface leading-tight">{l.label}</span>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-label-sm font-semibold text-on-surface-variant">Xung đột</p>
                    <span className="px-2 py-0.5 rounded-full bg-error-container text-error text-label-sm font-bold tabular-nums">
                      {cells.filter((c) => c.hasConflict).length} ngày
                    </span>
                  </div>
                  <div className="space-y-1.5">
                    {cells.filter((c) => c.hasConflict).slice(0, 5).map((c) => (
                      <div key={c.dateStr} className="flex items-center gap-2 text-label-sm">
                        <span aria-hidden="true" className="material-symbols-outlined text-error text-[14px]">warning</span>
                        <span className="text-on-surface font-medium tabular-nums">{c.day}/{today.getMonth() + 1}</span>
                        <span className="text-on-surface-variant truncate">{c.items.length} xung đột</span>
                      </div>
                    ))}
                    {cells.filter((c) => c.hasConflict).length === 0 && (
                      <p className="text-label-sm text-on-surface-variant flex items-center gap-2">
                        <span aria-hidden="true" className="material-symbols-outlined text-secondary text-[14px]">check_circle</span>
                        Không có xung đột
                      </p>
                    )}
                  </div>
                </div>
                <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-label-sm font-bold text-on-surface-variant leading-tight">Ngày nghỉ bù</p>
                    <span className="px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface text-label-sm font-bold tabular-nums">
                      {cells.filter((c) => c.isCompensation).length} ngày
                    </span>
                  </div>
                  <div className="space-y-1.5">
                    {cells.filter((c) => c.isCompensation && c.isCurrentMonth).slice(0, 5).map((c) => (
                      <div key={c.dateStr} className="flex items-center justify-between">
                        <span className="text-label-sm text-on-surface">
                          {new Date(c.dateStr).toLocaleDateString("vi-VN", { day: "numeric", month: "short" })}
                        </span>
                        <span className="material-symbols-outlined text-outline text-[14px]">lock</span>
                      </div>
                    ))}
                    {cells.filter((c) => c.isCompensation && c.isCurrentMonth).length === 0 && (
                      <p className="text-label-sm text-on-surface-variant italic">Không có ngày nghỉ bù</p>
                    )}
                  </div>
                </div>
              </aside>
            </>
          ) : (
          <aside id="calendar-sidebar" className="hidden lg:flex w-64 shrink-0 border-l border-outline-variant bg-surface-container-low overflow-y-auto flex-col gap-3 p-3">
            {/* Legend */}
            <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
              <p className="text-label-sm font-semibold text-on-surface-variant mb-2">Loại lịch</p>
              <div className="space-y-1.5">
                {[
                  { label: "Trực 24/24", color: "bg-primary",       id: "duty24"       },
                  { label: "Thông tầm",   color: "bg-secondary",  id: "allDay"       },
                  { label: "Dịch vụ",     color: "bg-tertiary",    id: "serviceClinic"},
                  { label: "Chuyên gia",  color: "bg-expert",   id: "expertClinic" },
                ].map((l) => (
                  <div key={l.id} className="flex items-center gap-2">
                    <div aria-hidden="true" className={`w-5 h-3 rounded-sm ${l.color}`} />
                    <span className="text-label-sm text-on-surface">{l.label}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Conflict summary */}
            <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
              <div className="flex items-center justify-between mb-2">
                <p className="text-label-sm font-bold text-on-surface-variant leading-tight">Xung đột</p>
                <span className="px-2 py-0.5 rounded-full bg-error-container text-error text-label-sm font-bold tabular-nums">
                  {cells.filter((c) => c.hasConflict).length} ngày
                </span>
              </div>
              <div className="space-y-1.5">
                {cells
                  .filter((c) => c.hasConflict)
                  .slice(0, 5)
                  .map((c) => (
                    <div key={c.dateStr} className="flex items-center gap-2 text-label-sm">
                      <span aria-hidden="true" className="material-symbols-outlined text-error text-[14px]">warning</span>
                      <span className="text-on-surface font-medium tabular-nums">{c.day}/{today.getMonth() + 1}</span>
                      <span className="text-on-surface-variant truncate">{c.items.length} xung đột</span>
                    </div>
                  ))}
                {cells.filter((c) => c.hasConflict).length === 0 && (
                  <p className="text-label-sm text-on-surface-variant flex items-center gap-2">
                    <span aria-hidden="true" className="material-symbols-outlined text-secondary text-[14px]">check_circle</span>
                    Không có xung đột
                  </p>
                )}
              </div>
            </div>

            {/* Compensation days legend */}
            <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
              <div className="flex items-center justify-between mb-2">
                <p className="text-label-sm font-bold text-on-surface-variant leading-tight">Ngày nghỉ bù</p>
                <span className="px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface text-label-sm font-bold tabular-nums">
                  {cells.filter((c) => c.isCompensation).length} ngày
                </span>
              </div>
              <div className="space-y-1.5">
                {cells
                  .filter((c) => c.isCompensation && c.isCurrentMonth)
                  .slice(0, 5)
                  .map((c) => {
                    const compAnn = c.annotations.find((a) => a.tone === "compLeave" || a.isCompensation);
                    return (
                      <div key={c.dateStr} className="flex items-center justify-between">
                        <span className="text-label-sm text-on-surface">
                          {new Date(c.dateStr).toLocaleDateString("vi-VN", { day: "numeric", month: "short" })}
                        </span>
                        <div className="flex items-center gap-1">
                          <span className="material-symbols-outlined text-[10px] text-outline">lock</span>
                          <span className="text-label-sm text-outline truncate max-w-[100px]" title={compAnn?.description}>
                            {compAnn?.label ?? "Nghỉ bù"}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                {cells.filter((c) => c.isCompensation && c.isCurrentMonth).length === 0 && (
                  <p className="text-label-sm text-on-surface-variant italic">Không có ngày nghỉ bù</p>
                )}
              </div>
            </div>
          </aside>
          ))}
        </div>
      )}

      {/* ── Tooltip ─────────────────────────────────────────────────── */}
      {tooltip && (
        <EventTooltip
          data={tooltip}
          onEdit={onEditSchedule ?? (() => {})}
          onDelete={onDeleteSchedule ?? (() => {})}
          onResolve={onResolveConflict ?? (() => {})}
          onViewDetail={onViewDetail ?? (() => {})}
          onRefresh={onRefresh ?? (() => {})}
          canEdit={!!onEditSchedule}
          onClose={() => setTooltip(null)}
        />
      )}

      {/* ── Overflow Popover ─────────────────────────────────────────── */}
      {overflow && (
        <OverflowPopover
          items={overflow.items}
          anchor={overflow.anchor}
          onDelete={onDeleteSchedule ?? (() => {})}
          onResolve={onResolveConflict ?? (() => {})}
          onViewDetail={onViewDetail ?? (() => {})}
          onRefresh={onRefresh ?? (() => {})}
          canEdit={!!onEditSchedule}
          onClose={() => setOverflow(null)}
        />
      )}
    </section>
  );
});
