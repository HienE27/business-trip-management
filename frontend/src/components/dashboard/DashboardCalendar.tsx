"use client";

import { useState, useMemo, useRef, useEffect, useCallback } from "react";
import type { Schedule } from "@/types/api";
import type { ScheduleTone } from "@/types/schedule";

// ─── Color tokens ────────────────────────────────────────────────────────────
const TONE: Record<ScheduleTone, {
  bg: string;
  text: string;
  border: string;
  dot: string;
}> = {
  duty24:       { bg: "bg-blue-50",        text: "text-blue-700",       border: "border-l-blue-500",       dot: "bg-blue-500"       },
  allDay:       { bg: "bg-emerald-50",     text: "text-emerald-700",    border: "border-l-emerald-500",    dot: "bg-emerald-500"    },
  serviceClinic:{ bg: "bg-amber-50",       text: "text-amber-700",      border: "border-l-amber-500",      dot: "bg-amber-500"      },
  expertClinic: { bg: "bg-violet-50",     text: "text-violet-700",     border: "border-l-violet-500",     dot: "bg-violet-500"    },
  compLeave:    { bg: "bg-slate-100",     text: "text-slate-600",      border: "border-l-slate-400",      dot: "bg-slate-400"     },
  warning:      { bg: "bg-orange-50",      text: "text-orange-700",     border: "border-l-orange-500",     dot: "bg-orange-500"    },
  conflict:     { bg: "bg-red-50",        text: "text-red-700",       border: "border-l-red-500",        dot: "bg-red-500"       },
  neutral:      { bg: "bg-gray-50",       text: "text-gray-600",      border: "border-l-gray-400",       dot: "bg-gray-400"      },
  empty:        { bg: "",                  text: "",                   border: "",                         dot: ""                 },
};

const SHIFT_SHORT: Record<string, string> = {
  L01: "24/24",
  L02: "TT",
  L03: "DV",
  L04: "CG",
};

const MAX_VISIBLE = 4;
const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] as const;

// ─── Types ────────────────────────────────────────────────────────────────────
type CalendarItem = {
  shiftLabel: string;   // "24/24"
  staffName: string;    // "Nguyen Van A"
  staffCode: string;    // "NV1"
  tone: ScheduleTone;
  shiftTypeId: string;
  schedule: Schedule;
};

export type CalendarAnnotationTone = "compLeave" | "warning" | "neutral";

export type CalendarAnnotation = {
  date: string;
  label: string;
  tone?: CalendarAnnotationTone;
  description?: string;
};

type CalendarCell = {
  day: number;
  isWeekend: boolean;
  isCurrentMonth: boolean;
  hasConflict: boolean;
  items: CalendarItem[];
  annotations: CalendarAnnotation[];
  dateStr: string;
  date: Date;
};

type DashboardCalendarProps = {
  schedules?: Schedule[];
  annotations?: CalendarAnnotation[];
  onEditSchedule?: (schedule: Schedule) => void;
  onDeleteSchedule?: (schedule: Schedule) => void;
  onResolveConflict?: (schedule: Schedule) => void;
  onDayClick?: (date: Date, items: CalendarItem[]) => void;
};

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[parts.length - 2]?.charAt(0) ?? "") + (parts[parts.length - 1]?.charAt(0) ?? "");
}

function getStaffCode(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 3).toUpperCase();
  const last = parts[parts.length - 1];
  return last.slice(0, 3).toUpperCase();
}

function shiftTypeToTone(id: string): ScheduleTone {
  switch (id) {
    case "L01": return "duty24";
    case "L02": return "allDay";
    case "L03": return "serviceClinic";
    case "L04": return "expertClinic";
    default:    return "neutral";
  }
}

// ─── Data computation ─────────────────────────────────────────────────────────
function buildCalendar(schedules: Schedule[], annotations: CalendarAnnotation[] = []) {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();

  const firstDay = new Date(year, month, 1);
  const lastDay  = new Date(year, month + 1, 0);
  const startDayOfWeek = (firstDay.getDay() + 6) % 7;
  const daysInMonth = lastDay.getDate();

  const prevMonth = month === 0 ? 12 : month;
  const prevYear  = month === 0 ? year - 1 : year;
  const prevMonthLastDay = new Date(prevYear, prevMonth, 0).getDate();

  const prevDays: number[] = [];
  for (let i = startDayOfWeek - 1; i >= 0; i--) prevDays.push(prevMonthLastDay - i);

  const totalCells = prevDays.length + daysInMonth;
  const remaining  = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);

  const scheduleMap = new Map<string, Schedule[]>();
  for (const s of schedules) {
    const key = s.workDate.split("T")[0];
    if (!scheduleMap.has(key)) scheduleMap.set(key, []);
    scheduleMap.get(key)!.push(s);
  }

  const annotationMap = new Map<string, CalendarAnnotation[]>();
  for (const annotation of annotations) {
    const key = annotation.date;
    if (!annotationMap.has(key)) annotationMap.set(key, []);
    annotationMap.get(key)!.push(annotation);
  }

  const fmt = (y: number, m: number, d: number) =>
    `${y}-${String(m + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

  const cells: CalendarCell[] = [];

  for (const d of prevDays) {
    const m = prevMonth - 1;
    const y = m < 0 ? prevYear - 1 : prevYear;
    const mm = m < 0 ? 11 : m;
    const dateStr = fmt(y, mm, d);
    const dow = new Date(y, mm, d).getDay();
    cells.push({
      day: d,
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: false,
      hasConflict: false,
      items: [],
      annotations: annotationMap.get(dateStr) ?? [],
      dateStr,
      date: new Date(y, mm, d),
    });
  }

  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = fmt(year, month, d);
    const daySchedules = scheduleMap.get(dateStr) ?? [];
    const hasConflict  = daySchedules.some((s) => s.hasConflict);
    const items: CalendarItem[] = daySchedules.map((s) => ({
      shiftLabel:  SHIFT_SHORT[s.shiftType.id] ?? s.shiftType.id,
      staffName:   s.staff.fullName,
      staffCode:   getStaffCode(s.staff.fullName),
      tone:        shiftTypeToTone(s.shiftType.id),
      shiftTypeId: s.shiftType.id,
      schedule:    s,
    }));
    cells.push({
      day: d,
      isWeekend: false,
      isCurrentMonth: true,
      hasConflict,
      items,
      annotations: annotationMap.get(dateStr) ?? [],
      dateStr,
      date: new Date(year, month, d),
    });
  }

  for (let d = 1; d <= remaining; d++) {
    const nm = month === 11 ? 0 : month + 1;
    const ny = month === 11 ? year + 1 : year;
    const dateStr = fmt(ny, nm, d);
    const dow = new Date(ny, nm, d).getDay();
    cells.push({
      day: d,
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: false,
      hasConflict: false,
      items: [],
      annotations: annotationMap.get(dateStr) ?? [],
      dateStr,
      date: new Date(ny, nm, d),
    });
  }

  const monthName = now.toLocaleDateString("vi-VN", { month: "long", year: "numeric" });
  return {
    month: monthName.charAt(0).toUpperCase() + monthName.slice(1),
    cells,
    today: new Date(now.getFullYear(), now.getMonth(), now.getDate()),
  };
}

// ─── Tooltip ─────────────────────────────────────────────────────────────────
type TooltipData = {
  x: number;
  y: number;
  item: CalendarItem;
};

function EventTooltip({ data, onEdit, onDelete, onResolve, canEdit }: {
  data: TooltipData;
  onEdit: (s: Schedule) => void;
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  canEdit: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const vp = window.innerWidth;
    if (rect.right > vp - 8) el.style.left = "auto";
    if (rect.bottom > window.innerHeight - 8) el.style.top = "auto";
  }, []);

  const s = data.item.schedule;
  const t = TONE[data.item.tone];

  return (
    <div
      ref={ref}
      className="fixed z-[100] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl p-4 w-64 pointer-events-auto"
      style={{ left: data.x + 8, top: data.y + 8 }}
      role="tooltip"
    >
      {/* Header */}
      <div className="flex items-center gap-2 mb-3">
        <div className={`w-8 h-8 rounded-full ${t.bg} flex items-center justify-center`}>
          <span className={`material-symbols-outlined text-sm ${t.text}`}>schedule</span>
        </div>
        <div>
          <p className={`text-label-lg font-semibold ${t.text}`}>{s.shiftType.name}</p>
          <p className="text-[11px] text-on-surface-variant">{data.item.shiftLabel}</p>
        </div>
        {s.hasConflict && (
          <span className="ml-auto material-symbols-outlined text-error text-[18px]" title="Xung đột">warning</span>
        )}
      </div>

      {/* Info */}
      <div className="space-y-1.5 mb-3 text-label-sm">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-[14px] text-on-surface-variant w-4">person</span>
          <span className="text-on-surface">{s.staff.fullName}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-[14px] text-on-surface-variant w-4">event</span>
          <span className="text-on-surface">
            {new Date(s.workDate).toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" })}
          </span>
        </div>
        {s.notes && (
          <div className="flex items-start gap-2">
            <span className="material-symbols-outlined text-[14px] text-on-surface-variant w-4 mt-0.5">notes</span>
            <span className="text-on-surface-variant">{s.notes}</span>
          </div>
        )}
      </div>

      {/* Actions */}
      {canEdit && (
        <div className="flex gap-2 pt-2 border-t border-outline-variant">
          <button
            type="button"
            onClick={() => onEdit(s)}
            className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-primary text-on-primary hover:bg-primary/90 transition-colors"
          >
            Chinh sua
          </button>
          {s.hasConflict && (
            <button
              type="button"
              onClick={() => onResolve(s)}
              className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-error text-on-error hover:bg-error/90 transition-colors"
            >
              Xu ly xung dot
            </button>
          )}
          <button
            type="button"
            onClick={() => onDelete(s)}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium border border-outline-variant text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Xoa
          </button>
        </div>
      )}
    </div>
  );
}

// ─── Overflow Popover ─────────────────────────────────────────────────────────
function OverflowPopover({ items, anchor, onEdit, onDelete, onResolve, canEdit }: {
  items: CalendarItem[];
  anchor: { x: number; y: number };
  onEdit: (s: Schedule) => void;
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  canEdit: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setVisible(false);
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  if (!visible) return null;

  const handleEdit = (s: Schedule) => { onEdit(s); setVisible(false); };
  const handleDelete = (s: Schedule) => { onDelete(s); setVisible(false); };
  const handleResolve = (s: Schedule) => { onResolve(s); setVisible(false); };

  return (
    <div
      ref={ref}
      className="fixed z-[90] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl w-72 max-h-80 overflow-y-auto"
      style={{ left: Math.min(anchor.x, window.innerWidth - 300), top: Math.min(anchor.y, window.innerHeight - 350) }}
      role="dialog"
      aria-label="Danh sách lịch trong ngày"
    >
      <div className="p-3 border-b border-outline-variant sticky top-0 bg-surface-container-lowest">
        <p className="text-label-md font-semibold text-on-surface">
          Tat ca lich ({items.length})
        </p>
      </div>
      <div className="p-2 space-y-1">
        {items.map((item, i) => {
          const t = TONE[item.tone];
          const s = item.schedule;
          return (
            <div key={i} className={`flex items-center gap-2.5 px-3 py-2 rounded-lg border-l-2 ${t.bg} ${t.border}`}>
              <div className={`w-6 h-6 rounded-full ${t.bg} flex items-center justify-center shrink-0`}>
                <span className={`text-[10px] font-bold ${t.text}`}>{item.staffCode}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className={`text-label-sm font-medium ${t.text} truncate`}>{item.staffName}</p>
                <p className="text-[11px] text-on-surface-variant">{item.shiftLabel}</p>
              </div>
              {s.hasConflict && (
                <span className="material-symbols-outlined text-error text-[14px] shrink-0" title="Xung đột">warning</span>
              )}
              {canEdit && (
                <div className="flex gap-1 shrink-0">
                  <button
                    type="button"
                    onClick={() => handleEdit(s)}
                    className="p-1 rounded hover:bg-surface-container-high transition-colors"
                    aria-label="Chỉnh sửa"
                  >
                    <span className="material-symbols-outlined text-[14px] text-on-surface-variant">edit</span>
                  </button>
                  {s.hasConflict && (
                    <button
                      type="button"
                      onClick={() => handleResolve(s)}
                      className="p-1 rounded hover:bg-error-container/30 transition-colors"
                      aria-label="Xử lý xung đột"
                    >
                      <span className="material-symbols-outlined text-[14px] text-error">warning</span>
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => handleDelete(s)}
                    className="p-1 rounded hover:bg-error-container/30 transition-colors"
                    aria-label="Xóa"
                  >
                    <span className="material-symbols-outlined text-[14px] text-error">delete</span>
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Main Calendar ────────────────────────────────────────────────────────────
export function DashboardCalendar({
  schedules = [],
  annotations = [],
  onEditSchedule,
  onDeleteSchedule,
  onResolveConflict,
  onDayClick,
}: DashboardCalendarProps) {
  const [filterType, setFilterType] = useState<string>("all");
  const [tooltip, setTooltip] = useState<TooltipData | null>(null);
  const [overflow, setOverflow] = useState<{ items: CalendarItem[]; anchor: { x: number; y: number } } | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const calendarRef = useRef<HTMLDivElement>(null);

  const filteredSchedules = useMemo(() => {
    if (filterType === "all") return schedules;
    return schedules.filter((s) => s.shiftType.id === filterType);
  }, [schedules, filterType]);

  const { cells, month, today } = useMemo(() => buildCalendar(filteredSchedules, annotations), [annotations, filteredSchedules]);

  // Close tooltip on scroll
  useEffect(() => {
    const handleScroll = () => setTooltip(null);
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const handleMouseEnter = useCallback((e: React.MouseEvent, item: CalendarItem) => {
    setTooltip({ x: e.clientX, y: e.clientY, item });
  }, []);

  const handleMouseLeave = useCallback(() => setTooltip(null), []);

  const handleOverflowClick = useCallback((e: React.MouseEvent, items: CalendarItem[]) => {
    e.stopPropagation();
    setOverflow({ items, anchor: { x: e.clientX, y: e.clientY } });
  }, []);

  const handleCellClick = useCallback((cell: CalendarCell) => {
    onDayClick?.(cell.date, cell.items);
  }, [onDayClick]);

  const isToday = (cell: CalendarCell) =>
    cell.date.getTime() === today.getTime();

  return (
    <section className="flex flex-col h-full" ref={calendarRef}>
      {/* ── Toolbar ─────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between gap-3 px-4 py-3 border-b border-outline-variant bg-surface-container-low shrink-0">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setSidebarOpen((v) => !v)}
            className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary hidden xl:flex"
            aria-label={sidebarOpen ? "Ẩn sidebar" : "Hiện sidebar"}
          >
            <span className="material-symbols-outlined text-[20px]">{sidebarOpen ? "visibility_off" : "visibility"}</span>
          </button>
          <h3 className="text-title-lg text-on-surface font-semibold">{month}</h3>
          <div className="flex gap-0.5">
            <button type="button" className="p-1.5 rounded-md hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Tháng trước">
              <span className="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <button type="button" className="px-2.5 py-1 rounded-md hover:bg-surface-container-high text-on-surface text-label-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset">
              Hom nay
            </button>
            <button type="button" className="p-1.5 rounded-md hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Tháng sau">
              <span className="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Filter chips */}
          <div className="flex gap-1 bg-surface-container-low rounded-lg p-0.5">
            {[
              { value: "all", label: "Tất cả" },
              { value: "L01", label: "24/24", color: "bg-blue-500" },
              { value: "L02", label: "TT", color: "bg-emerald-500" },
              { value: "L03", label: "DV", color: "bg-amber-500" },
              { value: "L04", label: "CG", color: "bg-violet-500" },
            ].map((f) => (
              <button
                key={f.value}
                type="button"
                onClick={() => setFilterType(f.value)}
                className={`px-2.5 py-1 rounded-md text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  filterType === f.value
                    ? "bg-surface-container-lowest text-on-surface shadow-sm"
                    : "text-on-surface-variant hover:text-on-surface"
                }`}
              >
                {f.color && (
                  <span className={`inline-block w-2 h-2 rounded-full ${f.color} mr-1.5`} />
                )}
                {f.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* ── Calendar Grid ──────────────────────────────────────────── */}
      <div className="flex-1 flex min-h-0">
        <div className="flex-1 flex flex-col min-w-0">
          {/* Day headers */}
          <div className="grid grid-cols-7 shrink-0">
            {WEEKDAYS.map((d, i) => (
              <div
                key={d}
                className={`py-2 text-center text-label-sm font-semibold border-b border-r border-outline-variant bg-surface-container-low ${
                  i >= 5 ? "text-red-500" : "text-on-surface-variant"
                }`}
              >
                {d}
              </div>
            ))}
          </div>

          {/* Cells */}
          <div className="flex-1 grid grid-cols-7 flex-1 auto-rows-fr" style={{ minHeight: 0 }}>
            {cells.map((cell, idx) => {
              const visible = cell.items.slice(0, MAX_VISIBLE);
              const extra   = cell.items.length - MAX_VISIBLE;
              const hasMore = extra > 0;
              const t = TONE;

              const hasAnnotations = cell.annotations.length > 0;
              const primaryAnnotation = cell.annotations[0] ?? null;

              return (
                <div
                  key={cell.dateStr}
                  onClick={() => handleCellClick(cell)}
                  className={`
                    border-r border-b border-outline-variant flex flex-col
                    min-h-[120px] cursor-pointer group relative
                    transition-colors hover:bg-surface-container-low
                    ${!cell.isCurrentMonth ? "bg-surface-variant/20" : ""}
                    ${cell.hasConflict ? "ring-2 ring-inset ring-red-300" : ""}
                    ${hasAnnotations && cell.isCurrentMonth ? "bg-slate-50/80" : ""}
                  `}
                >
                  {/* Day number */}
                  <div className={`shrink-0 px-1.5 py-1 flex items-center justify-between ${
                    isToday(cell)
                      ? "bg-primary text-on-primary rounded-tl-sm"
                      : cell.isWeekend
                      ? "text-red-500"
                      : cell.hasConflict
                      ? "text-red-600"
                      : hasAnnotations
                      ? "text-slate-700"
                      : cell.isCurrentMonth
                      ? "text-on-surface"
                      : "text-on-surface-variant"
                  }`}>
                    <span className={`text-label-md font-semibold leading-none ${
                      isToday(cell) ? "" : "group-hover:text-primary"
                    }`}>
                      {cell.day}
                    </span>
                    {cell.hasConflict && !isToday(cell) ? (
                      <span className="material-symbols-outlined text-[11px] text-red-500">warning</span>
                    ) : primaryAnnotation ? (
                      <span className="rounded-full bg-slate-200 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-slate-600">
                        {primaryAnnotation.label}
                      </span>
                    ) : null}
                  </div>

                  {/* Events */}
                  <div className="flex-1 flex flex-col gap-0.5 p-0.5 min-h-0 overflow-hidden">
                    {cell.annotations.map((annotation) => {
                      const annotationTone = TONE[annotation.tone ?? "compLeave"] ?? TONE.compLeave;
                      return (
                        <div
                          key={`${cell.dateStr}-${annotation.label}`}
                          className={`flex items-center gap-1 px-1.5 py-0.5 rounded border-l-2 min-h-[22px] max-h-[22px] overflow-hidden ${annotationTone.bg} ${annotationTone.border}`}
                          title={annotation.description ?? annotation.label}
                        >
                          <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${annotationTone.dot}`} />
                          <span className={`text-[11px] font-semibold leading-none truncate ${annotationTone.text}`}>
                            {annotation.label}
                          </span>
                        </div>
                      );
                    })}
                    {visible.map((item, i) => {
                      const st = t[item.tone] ?? t.neutral;
                      const isOpen = tooltip?.item === item;
                      return (
                        <div
                          key={i}
                          onMouseEnter={(e) => handleMouseEnter(e, item)}
                          onMouseLeave={handleMouseLeave}
                          className={`
                            flex items-center gap-1 px-1.5 py-0.5 rounded border-l-2
                            min-h-[22px] max-h-[22px] overflow-hidden cursor-pointer
                            transition-colors hover:brightness-95
                            ${st.bg} ${st.border}
                          `}
                        >
                          {/* Dot + label */}
                          <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${st.dot}`} />
                          <span className={`text-[11px] font-semibold leading-none truncate ${st.text}`}>
                            {item.shiftLabel}
                          </span>
                          <span className="text-[11px] leading-none text-on-surface-variant shrink-0">·</span>
                          <span className="text-[11px] leading-none text-on-surface font-medium truncate">
                            {item.staffCode}
                          </span>
                        </div>
                      );
                    })}

                    {/* Overflow */}
                    {hasMore && (
                      <button
                        type="button"
                        onClick={(e) => handleOverflowClick(e, cell.items)}
                        className="flex items-center justify-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-medium text-on-surface-variant hover:bg-surface-container-high transition-colors min-h-[22px] max-h-[22px] w-full"
                      >
                        <span className="material-symbols-outlined text-[12px]">expand_more</span>
                        +{extra} lich khac
                      </button>
                    )}
                  </div>

                  {/* Conflict count badge */}
                  {cell.hasConflict && (
                    <div className="absolute bottom-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-red-100 text-error text-[10px] font-bold">
                      <span className="material-symbols-outlined text-[10px]">warning</span>
                      {cell.items.length}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* ── Sidebar (collapsible) ──────────────────────────────────── */}
        {sidebarOpen && (
          <aside className="w-64 xl:w-72 shrink-0 border-l border-outline-variant bg-surface-container-low overflow-y-auto hidden xl:flex flex-col gap-3 p-3">
            {/* Legend */}
            <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
              <p className="text-label-sm font-semibold text-on-surface-variant mb-2 uppercase tracking-wide">Loai lich</p>
              <div className="space-y-1.5">
                {[
                  { label: "Trực 24/24", color: "bg-blue-500",      id: "duty24"       },
                  { label: "Thông tầm",   color: "bg-emerald-500",  id: "allDay"       },
                  { label: "Dịch vụ",     color: "bg-amber-500",    id: "serviceClinic"},
                  { label: "Chuyên gia",  color: "bg-violet-500",   id: "expertClinic" },
                ].map((l) => (
                  <div key={l.id} className="flex items-center gap-2">
                    <div className={`w-5 h-3 rounded-sm ${l.color}`} />
                    <span className="text-label-sm text-on-surface">{l.label}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Conflict summary */}
            <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
              <div className="flex items-center justify-between mb-2">
                <p className="text-label-sm font-semibold text-on-surface-variant uppercase tracking-wide">Xung dot</p>
                <span className="px-2 py-0.5 rounded-full bg-red-100 text-error text-[11px] font-bold">
                  {cells.filter((c) => c.hasConflict).length} ngày
                </span>
              </div>
              <div className="space-y-1.5">
                {cells
                  .filter((c) => c.hasConflict)
                  .slice(0, 5)
                  .map((c) => (
                    <div key={c.dateStr} className="flex items-center gap-2 text-label-sm">
                      <span className="material-symbols-outlined text-error text-[14px]">warning</span>
                      <span className="text-on-surface font-medium">{c.day}/{today.getMonth() + 1}</span>
                      <span className="text-on-surface-variant truncate">{c.items.length} xung dot</span>
                    </div>
                  ))}
                {cells.filter((c) => c.hasConflict).length === 0 && (
                  <p className="text-label-sm text-on-surface-variant flex items-center gap-2">
                    <span className="material-symbols-outlined text-emerald-500 text-[14px]">check_circle</span>
                    Không có xung đột
                  </p>
                )}
              </div>
            </div>
          </aside>
        )}
      </div>

      {/* ── Tooltip ─────────────────────────────────────────────────── */}
      {tooltip && (
        <EventTooltip
          data={tooltip}
          onEdit={onEditSchedule ?? (() => {})}
          onDelete={onDeleteSchedule ?? (() => {})}
          onResolve={onResolveConflict ?? (() => {})}
          canEdit={!!onEditSchedule}
        />
      )}

      {/* ── Overflow Popover ─────────────────────────────────────────── */}
      {overflow && (
        <OverflowPopover
          items={overflow.items}
          anchor={overflow.anchor}
          onEdit={onEditSchedule ?? (() => {})}
          onDelete={onDeleteSchedule ?? (() => {})}
          onResolve={onResolveConflict ?? (() => {})}
          canEdit={!!onEditSchedule}
        />
      )}
    </section>
  );
}
