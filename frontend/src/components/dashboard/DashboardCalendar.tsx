"use client";

import React, { useState, useMemo, useRef, useEffect, useCallback } from "react";
import type { Schedule } from "@/types/api";
import type { ScheduleTone } from "@/types/schedule";

// ─── Color tokens ────────────────────────────────────────────────────────────
const TONE: Record<ScheduleTone, {
  bg: string;
  text: string;
  border: string;
  dot: string;
}> = {
  duty24:       { bg: "bg-red-50",         text: "text-red-700",       border: "border-l-red-500",       dot: "bg-red-500"       },
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

const MAX_VISIBLE_GROUPS = 2;
const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] as const;

const SHIFT_FULL_LABEL: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK dịch vụ",
  L04: "PK chuyên gia",
};

const SHIFT_ORDER: Record<string, number> = {
  L01: 1,
  L02: 2,
  L03: 3,
  L04: 4,
};

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
  isCompensation?: boolean;
  locked?: boolean;
  coverage?: { required: number; assigned: number };
  coverageShiftTypeId?: string;
};

type CalendarCell = {
  day: number;
  isWeekend: boolean;
  isCurrentMonth: boolean;
  hasConflict: boolean;
  isCompensation: boolean;
  items: CalendarItem[];
  annotations: CalendarAnnotation[];
  dateStr: string;
  date: Date;
};

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
};

export type CalendarViewMode = "month" | "week";

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

function summarizeItems(items: CalendarItem[]) {
  const map = new Map<string, { shiftTypeId: string; label: string; count: number; tone: ScheduleTone }>();
  for (const item of items) {
    const current = map.get(item.shiftTypeId) ?? {
      shiftTypeId: item.shiftTypeId,
      label: SHIFT_FULL_LABEL[item.shiftTypeId] ?? item.shiftLabel,
      count: 0,
      tone: item.tone,
    };
    current.count += 1;
    map.set(item.shiftTypeId, current);
  }
  return Array.from(map.values()).sort((a, b) => (SHIFT_ORDER[a.shiftTypeId] ?? 99) - (SHIFT_ORDER[b.shiftTypeId] ?? 99));
}

function formatFullDate(date: Date) {
  return date.toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" });
}

// ─── Data computation ─────────────────────────────────────────────────────────
function buildCalendar(schedules: Schedule[], annotations: CalendarAnnotation[] = [], year: number, month: number) {
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

  const isCompDay = (anns: CalendarAnnotation[]) =>
    anns.some((a) => a.tone === "compLeave" || a.isCompensation);

  for (const d of prevDays) {
    const m = prevMonth - 1;
    const y = m < 0 ? prevYear - 1 : prevYear;
    const mm = m < 0 ? 11 : m;
    const dateStr = fmt(y, mm, d);
    const dow = new Date(y, mm, d).getDay();
    const anns = annotationMap.get(dateStr) ?? [];
    cells.push({
      day: d,
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: false,
      hasConflict: false,
      isCompensation: isCompDay(anns),
      items: [],
      annotations: anns,
      dateStr,
      date: new Date(y, mm, d),
    });
  }

  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = fmt(year, month, d);
    const daySchedules = scheduleMap.get(dateStr) ?? [];
    const hasConflict  = daySchedules.some((s) => s.hasConflict);
    const anns = annotationMap.get(dateStr) ?? [];
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
      isCompensation: isCompDay(anns),
      items,
      annotations: anns,
      dateStr,
      date: new Date(year, month, d),
    });
  }

  for (let d = 1; d <= remaining; d++) {
    const nm = month === 11 ? 0 : month + 1;
    const ny = month === 11 ? year + 1 : year;
    const dateStr = fmt(ny, nm, d);
    const dow = new Date(ny, nm, d).getDay();
    const anns = annotationMap.get(dateStr) ?? [];
    cells.push({
      day: d,
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: false,
      hasConflict: false,
      isCompensation: isCompDay(anns),
      items: [],
      annotations: anns,
      dateStr,
      date: new Date(ny, nm, d),
    });
  }

  const monthName = new Date(year, month, 1).toLocaleDateString("vi-VN", { month: "long", year: "numeric" });
  return {
    month: monthName.charAt(0).toUpperCase() + monthName.slice(1),
    cells,
    today: new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate()),
  };
}

// ─── Tooltip ─────────────────────────────────────────────────────────────────
type TooltipData = {
  x: number;
  y: number;
  item: CalendarItem;
};

function EventTooltip({ data, onEdit, onDelete, onResolve, onViewDetail, canEdit }: {
  data: TooltipData;
  onEdit: (s: Schedule) => void;
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  onViewDetail: (s: Schedule) => void;
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
      <div className="flex gap-2 pt-2 border-t border-outline-variant">
        <button
          type="button"
          onClick={() => onViewDetail(s)}
          className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-surface-container-low text-on-surface hover:bg-surface-container-high transition-colors"
        >
          <span className="flex items-center justify-center gap-1.5">
            <span className="material-symbols-outlined text-[16px]">visibility</span>
            Xem chi tiet
          </span>
        </button>
        {canEdit && (
          <button
            type="button"
            onClick={() => onEdit(s)}
            className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-primary text-on-primary hover:bg-primary/90 transition-colors"
          >
            Chinh sua
          </button>
        )}
        {canEdit && s.hasConflict && (
          <button
            type="button"
            onClick={() => onResolve(s)}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium bg-error text-on-error hover:bg-error/90 transition-colors"
          >
            Xu ly
          </button>
        )}
        {canEdit && (
          <button
            type="button"
            onClick={() => onDelete(s)}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium border border-outline-variant text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Xoa
          </button>
        )}
      </div>
    </div>
  );
}

// ─── Overflow Popover ─────────────────────────────────────────────────────────
function OverflowPopover({ items, anchor, onEdit, onDelete, onResolve, onViewDetail, canEdit }: {
  items: CalendarItem[];
  anchor: { x: number; y: number };
  onEdit: (s: Schedule) => void;
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  onViewDetail: (s: Schedule) => void;
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
  const handleViewDetail = (s: Schedule) => { onViewDetail(s); setVisible(false); };

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
          Chi tiết ngày ({items.length})
        </p>
      </div>
      <div className="p-2 space-y-1">
        {items.map((item, i) => {
          const t = TONE[item.tone];
          const s = item.schedule;
          return (
            <div key={`${item.schedule.id}-${i}`} className={`flex items-center gap-2.5 px-3 py-2 rounded-lg border-l-2 ${t.bg} ${t.border}`}>
              <div className={`w-6 h-6 rounded-full ${t.bg} flex items-center justify-center shrink-0`}>
                <span className={`text-[10px] font-bold ${t.text}`}>{item.staffCode}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className={`text-label-sm font-medium ${t.text} truncate`}>{item.staffName}</p>
                <p className="text-[11px] text-on-surface-variant">{item.shiftLabel}</p>
              </div>
              {s.hasConflict && (
                <span key={`conflict-${s.id}`} className="material-symbols-outlined text-error text-[14px] shrink-0" title="Xung đột">warning</span>
              )}
              <div className="flex gap-1 shrink-0">
                <button
                  type="button"
                  onClick={() => handleViewDetail(s)}
                  className="p-1 rounded hover:bg-surface-container-high transition-colors"
                  aria-label="Xem chi tiết"
                >
                  <span className="material-symbols-outlined text-[14px] text-on-surface-variant">visibility</span>
                </button>
                {canEdit && (
                  <React.Fragment key={s.id}>
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
                  </React.Fragment>
                )}
              </div>
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
  coverages = {},
  staffList = [],
  staffFilter: externalStaffFilter,
  specialtyList = [],
  specialtyFilter: externalSpecialtyFilter,
  initialYear,
  initialMonth,
  onEditSchedule,
  onDeleteSchedule,
  onResolveConflict,
  onViewDetail,
  onDayClick,
  onAddClick,
  onStaffFilterChange,
  onSpecialtyFilterChange,
}: DashboardCalendarProps) {
  const [filterType, setFilterType] = useState<string>("all");
  const [internalStaffFilter, setInternalStaffFilter] = useState<number | null>(null);
  const [internalSpecialtyFilter, setInternalSpecialtyFilter] = useState<number | null>(null);
  const staffFilter = externalStaffFilter === undefined ? internalStaffFilter : externalStaffFilter;
  const specialtyFilter = externalSpecialtyFilter === undefined ? internalSpecialtyFilter : externalSpecialtyFilter;
  const [currentYear, setCurrentYear] = useState(() => initialYear ?? new Date().getFullYear());
  const [currentMonth, setCurrentMonth] = useState(() => initialMonth ?? new Date().getMonth());
  const [viewMode, setViewMode] = useState<CalendarViewMode>("month");
  const [tooltip, setTooltip] = useState<TooltipData | null>(null);
  const [overflow, setOverflow] = useState<{ items: CalendarItem[]; anchor: { x: number; y: number } } | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const calendarRef = useRef<HTMLDivElement>(null);

  const filteredSchedules = useMemo(() => {
    let result = filterType === "all" ? schedules : schedules.filter((s) => s.shiftType.id === filterType);
    if (staffFilter !== null) {
      result = result.filter((s) => s.staff.id === staffFilter);
    }
    if (specialtyFilter !== null) {
      result = result.filter((s) => s.staff.specialtyName != null && s.staff.specialtyName.includes(String(specialtyFilter)));
    }
    return result;
  }, [schedules, filterType, staffFilter, specialtyFilter]);

  const { cells, month, today } = useMemo(() => buildCalendar(filteredSchedules, annotations, currentYear, currentMonth), [annotations, filteredSchedules, currentYear, currentMonth]);

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
    openCell(cell, { x: event.clientX, y: event.clientY });
  }, [openCell]);

  const handleCellKeyDown = useCallback((event: React.KeyboardEvent, cell: CalendarCell) => {
    if (event.key !== "Enter" && event.key !== " ") return;
    event.preventDefault();
    openCell(cell);
  }, [openCell]);

  const isToday = (cell: CalendarCell) =>
    cell.date.getTime() === today.getTime();

  const currentWeekStart = useMemo(() => {
    const now = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const dow = (now.getDay() + 6) % 7; // Mon=0
    now.setDate(now.getDate() - dow);
    return now;
  }, [today]);

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
          staffName: s.staff.fullName,
          staffCode: getStaffCode(s.staff.fullName),
          tone: shiftTypeToTone(s.shiftType.id),
          shiftTypeId: s.shiftType.id,
          schedule: s,
        })),
        annotations: annotations.filter((a) => a.date === dateStr),
        isCompensation: annotations.some((a) => a.date === dateStr && (a.tone === "compLeave" || a.isCompensation)),
        hasConflict: daySchedules.some((s) => s.hasConflict),
      };
    });
  }, [currentWeekStart, filteredSchedules, annotations]);

  const weekLabel = useMemo(() => {
    const start = new Date(currentWeekStart);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt = (d: Date) => d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
    return `${fmt(start)} – ${fmt(end)}`;
  }, [currentWeekStart]);

  const navigateMonth = (direction: -1 | 1) => {
    const nm = direction === -1
      ? (currentMonth === 0 ? 11 : currentMonth - 1)
      : (currentMonth === 11 ? 0 : currentMonth + 1);
    const ny = direction === -1
      ? (currentMonth === 0 ? currentYear - 1 : currentYear)
      : (currentMonth === 11 ? currentYear + 1 : currentYear);
    setCurrentMonth(nm);
    setCurrentYear(ny);
  };

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
          <h3 className="text-title-lg text-on-surface font-semibold">
            {viewMode === "week" ? weekLabel : month}
          </h3>
          <div className="flex gap-0.5">
            <button type="button" onClick={() => navigateMonth(-1)} className="p-1.5 rounded-md hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label={viewMode === "week" ? "Tuần trước" : "Tháng trước"}>
              <span className="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <button type="button" onClick={() => { const now = new Date(); setCurrentYear(now.getFullYear()); setCurrentMonth(now.getMonth()); }} className="px-2.5 py-1 rounded-md hover:bg-surface-container-high text-on-surface text-label-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset">
              Hom nay
            </button>
            <button type="button" onClick={() => navigateMonth(1)} className="p-1.5 rounded-md hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label={viewMode === "week" ? "Tuần sau" : "Tháng sau"}>
              <span className="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>
        </div>

        {/* Staff filter */}
        {staffList.length > 0 && (
          <div className="relative">
            <select
              className="h-8 pl-3 pr-8 bg-surface-container-low border border-transparent rounded-lg text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all max-w-[180px]"
              value={staffFilter ?? ""}
              onChange={(e) => {
                const val = e.target.value ? Number(e.target.value) : null;
                if (externalStaffFilter === undefined) setInternalStaffFilter(val);
                onStaffFilterChange?.(val);
              }}
            >
              <option value="">Tất cả nhân sự</option>
              {staffList.map((s) => (
                <option key={s.id} value={s.id}>{s.fullName}</option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[16px]">expand_more</span>
          </div>
        )}

        {/* Specialty filter */}
        {specialtyList.length > 0 && (
          <div className="relative">
            <select
              className="h-8 pl-3 pr-8 bg-surface-container-low border border-transparent rounded-lg text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all max-w-[160px]"
              value={specialtyFilter ?? ""}
              onChange={(e) => {
                const val = e.target.value ? Number(e.target.value) : null;
                if (externalSpecialtyFilter === undefined) setInternalSpecialtyFilter(val);
                onSpecialtyFilterChange?.(val);
              }}
            >
              <option value="">Tất cả chuyên khoa</option>
              {specialtyList.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[16px]">expand_more</span>
          </div>
        )}

        <div className="flex items-center gap-2">
          {/* View mode toggle */}
          <div className="flex gap-1 bg-surface-container-low rounded-lg p-0.5">
            {([
              { value: "month" as CalendarViewMode, label: "Tháng", icon: "calendar_view_month" },
              { value: "week" as CalendarViewMode, label: "Tuần", icon: "view_week" },
            ]).map((v) => (
              <button
                key={v.value}
                type="button"
                onClick={() => setViewMode(v.value)}
                aria-pressed={viewMode === v.value}
                className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  viewMode === v.value
                    ? "bg-surface-container-lowest text-on-surface shadow-sm"
                    : "text-on-surface-variant hover:text-on-surface"
                }`}
              >
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{v.icon}</span>
                {v.label}
              </button>
            ))}
          </div>

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
                aria-pressed={filterType === f.value}
                aria-label={`Lọc theo loại lịch ${f.label}`}
                className={`px-2.5 py-1 rounded-md text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  filterType === f.value
                    ? "bg-surface-container-lowest text-on-surface shadow-sm"
                    : "text-on-surface-variant hover:text-on-surface"
                }`}
              >
                {f.color && (
                  <span className={`inline-block w-2 h-2 rounded-full ${f.color} mr-1.5`} aria-hidden="true" />
                )}
                {f.label}
              </button>
            ))}
          </div>
        </div>
      </div>

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
                    i >= 5 ? "text-red-500" : "text-on-surface-variant"
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
                    aria-label={`${formatFullDate(cell.date)}: ${cell.items.length} lịch, ${cell.annotations.length} ghi chú`}
                    className={`
                      border-r border-b border-outline-variant flex flex-col
                      min-h-[200px] cursor-pointer group relative
                      transition-colors hover:bg-surface-container-low
                      ${cell.date.toDateString() === today.toDateString() ? "bg-primary-fixed/10" : ""}
                      ${cell.hasConflict ? "ring-2 ring-inset ring-red-300" : ""}
                    `}
                  >
                    {/* Conflict indicator */}
                    {cell.hasConflict && (
                      <div className="absolute top-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-red-100 text-error text-[10px] font-bold z-10">
                        <span className="material-symbols-outlined text-[10px]">warning</span>
                      </div>
                    )}
                    {/* Compensation indicator */}
                    {cell.isCompensation && (
                      <div className="absolute top-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-surface-container-high text-outline text-[10px] font-bold z-10">
                        <span className="material-symbols-outlined text-[10px]">lock</span>
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
                            <span className={`text-[11px] font-semibold leading-none truncate ${annTone.text}`}>{ann.label}</span>
                          </div>
                        );
                      })}
                      {cell.items.map((item, i) => {
                        const st = t[item.tone] ?? t.neutral;
                        return (
                          <div
                            key={`week-item-${item.schedule.id}-${i}`}
                            onMouseEnter={(e) => handleMouseEnter(e, item)}
                            onMouseLeave={handleMouseLeave}
                            className={`flex items-center gap-1 px-1.5 py-0.5 rounded border-l-2 min-h-[22px] max-h-[22px] overflow-hidden cursor-pointer transition-colors hover:brightness-95 ${st.bg} ${st.border}`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${st.dot}`} />
                            <span className={`text-[11px] font-semibold leading-none truncate ${st.text}`}>{item.shiftLabel}</span>
                            <span className="text-[11px] leading-none text-on-surface-variant shrink-0">·</span>
                            <span className="text-[11px] leading-none text-on-surface font-medium truncate">{item.staffCode}</span>
                          </div>
                        );
                      })}
                      {cell.items.length === 0 && cell.annotations.length === 0 && !cell.isCompensation && (
                        <button
                          type="button"
                          className="flex items-center justify-center min-h-[24px] text-[14px] text-on-surface-variant/40 hover:text-primary/60 transition-colors w-full"
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
          {/* Sidebar — Week summary */}
          {sidebarOpen && (
            <aside className="w-64 xl:w-72 shrink-0 border-l border-outline-variant bg-surface-container-low overflow-y-auto hidden xl:flex flex-col gap-3 p-3">
              <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                <p className="text-label-sm font-semibold text-on-surface-variant mb-2 uppercase tracking-wide">Tổng kết tuần</p>
                <div className="space-y-2">
                  {[
                    { label: "Tổng ca", value: weekCells.reduce((s, c) => s + c.items.length, 0), color: "text-primary" },
                    { label: "Xung đột", value: weekCells.filter((c) => c.hasConflict).length, color: "text-error" },
                    { label: "Ngày nghỉ bù", value: weekCells.filter((c) => c.isCompensation).length, color: "text-outline" },
                  ].map((m) => (
                    <div key={m.label} className="flex justify-between items-center">
                      <span className="text-label-sm text-on-surface-variant">{m.label}</span>
                      <span className={`text-label-md font-bold ${m.color}`}>{m.value}</span>
                    </div>
                  ))}
                </div>
              </div>
              {/* Legend */}
              <div className="bg-surface-container-lowest rounded-lg border border-outline-variant p-3">
                <p className="text-label-sm font-semibold text-on-surface-variant mb-2 uppercase tracking-wide">Loai lich</p>
                <div className="space-y-1.5">
                  {[
                    { label: "Trực 24/24", color: "bg-red-500" },
                    { label: "Thông tầm", color: "bg-emerald-500" },
                    { label: "Dịch vụ", color: "bg-amber-500" },
                    { label: "Chuyên gia", color: "bg-violet-500" },
                  ].map((l) => (
                    <div key={l.label} className="flex items-center gap-2">
                      <div className={`w-5 h-3 rounded-sm ${l.color}`} />
                      <span className="text-label-sm text-on-surface">{l.label}</span>
                    </div>
                  ))}
                </div>
              </div>
            </aside>
          )}
        </div>
      )}

      {/* ── Calendar Grid ──────────────────────────────────────────── */}
      {viewMode === "month" && (
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
            {cells.map((cell) => {
              const summaries = summarizeItems(cell.items);
              const visibleSummaries = summaries.slice(0, MAX_VISIBLE_GROUPS);
              const visibleStaffCount = visibleSummaries.reduce((sum, item) => sum + item.count, 0);
              const hiddenStaffCount = Math.max(0, cell.items.length - visibleStaffCount);
              const hasMore = hiddenStaffCount > 0;
              const t = TONE;

              const hasAnnotations = cell.annotations.length > 0;
              const primaryAnnotation = cell.annotations[0] ?? null;
              const isCompLocked = cell.isCompensation;

              return (
                <div
                  key={cell.dateStr}
                  onClick={(event) => handleCellClick(event, cell)}
                  onKeyDown={(event) => handleCellKeyDown(event, cell)}
                  role="button"
                  tabIndex={isCompLocked ? -1 : 0}
                  aria-label={`${formatFullDate(cell.date)}: ${cell.items.length} lịch, ${cell.annotations.length} ghi chú`}
                  className={`
                    border-r border-b border-outline-variant flex flex-col
                    min-h-[120px] cursor-pointer group relative
                    transition-colors hover:bg-surface-container-low
                    ${!cell.isCurrentMonth ? "bg-surface-variant/20" : ""}
                    ${cell.hasConflict ? "ring-2 ring-inset ring-red-300" : ""}
                    ${isCompLocked ? "bg-[repeating-linear-gradient(45deg,transparent,transparent_6px,rgba(0,0,0,0.04)_6px,rgba(0,0,0,0.04)_12px)]" : ""}
                  `}
                >
                  {/* Diagonal stripes overlay for locked comp days */}
                  {isCompLocked && (
                    <div
                      className="absolute inset-0 pointer-events-none z-10 opacity-30"
                      style={{
                        backgroundImage: "repeating-linear-gradient(45deg, #94a3b8 0px, #94a3b8 1px, transparent 1px, transparent 10px)",
                        backgroundSize: "14px 14px",
                      }}
                      aria-hidden="true"
                    />
                  )}

                  {/* Day number */}
                  <div className={`shrink-0 px-1.5 py-1 flex items-center justify-between z-20 ${
                    isToday(cell)
                      ? "bg-primary text-on-primary rounded-tl-sm"
                      : cell.isWeekend
                      ? "text-red-500"
                      : cell.hasConflict
                      ? "text-red-600"
                      : isCompLocked
                      ? "text-slate-500"
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
                    ) : isCompLocked ? (
                      <span className="material-symbols-outlined text-[11px] text-slate-400" title="Ngày nghỉ bù — khóa">lock</span>
                    ) : primaryAnnotation ? (
                      <span className="rounded-full bg-slate-200 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-slate-600">
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
                          <span className={`text-[11px] font-semibold leading-none truncate ${annotationTone.text}`}>
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
                          className={`flex items-center gap-1 rounded border-l-2 px-1.5 py-0.5 min-h-[24px] max-h-[24px] overflow-hidden ${st.bg} ${st.border}`}
                          title={`${summary.label}: ${summary.count} nhân sự`}
                        >
                          <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${st.dot}`} aria-hidden="true" />
                          <span className={`truncate text-[11px] font-semibold leading-none ${st.text}`}>
                            {summary.label} ({summary.count})
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
                        className="flex items-center justify-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-medium text-on-surface-variant hover:bg-surface-container-high transition-colors min-h-[22px] max-h-[22px] w-full"
                      >
                        <span className="material-symbols-outlined text-[12px]" aria-hidden="true">expand_more</span>
                        +{hiddenStaffCount} nhân sự khác
                      </button>
                    )}

                    {/* Empty cell + hint */}
                    {!hasMore && cell.items.length === 0 && cell.annotations.length === 0 && !cell.isCompensation && cell.isCurrentMonth && (
                      <button
                        type="button"
                        className="flex items-center justify-center min-h-[24px] text-[14px] text-on-surface-variant/40 hover:text-primary/60 transition-colors w-full"
                        onClick={() => onAddClick?.(cell.date)}
                        aria-label={`Thêm lịch cho ngày ${cell.day}`}
                      >
                        <span className="material-symbols-outlined text-[16px]" aria-hidden="true">add</span>
                      </button>
                    )}
                  </div>

                    {/* Conflict count badge */}
                    {cell.hasConflict && (
                      <div
                        className="absolute bottom-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full bg-red-100 text-error text-[10px] font-bold"
                        aria-label={`${cell.items.length} xung đột trong ngày này`}
                      >
                        <span className="material-symbols-outlined text-[10px]" aria-hidden="true">warning</span>
                        {cell.items.length}
                      </div>
                    )}

                  {/* Coverage indicator badge */}
                  {!cell.isCompensation && (() => {
                    const cov = coverages[cell.dateStr];
                    if (!cov) return null;
                    const isFull = cov.assigned >= cov.required;
                    const isEmpty = cov.assigned === 0;
                    if (isFull && cell.items.length > 0) return null;
                    return (
                      <div
                        className={`absolute bottom-1 right-1 flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                          isFull ? "bg-secondary-container text-on-secondary-container" :
                          isEmpty ? "bg-surface-container-high text-on-surface-variant" :
                          "bg-amber-50 text-amber-700"
                        }`}
                        aria-label={`Phủ ${cov.assigned} trên ${cov.required} ca cần thiết`}
                      >
                        <span className="material-symbols-outlined text-[10px]" aria-hidden="true">group</span>
                        {cov.assigned}/{cov.required}
                      </div>
                    );
                  })()}
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
      )}

      {/* ── Tooltip ─────────────────────────────────────────────────── */}
      {tooltip && (
        <EventTooltip
          data={tooltip}
          onEdit={onEditSchedule ?? (() => {})}
          onDelete={onDeleteSchedule ?? (() => {})}
          onResolve={onResolveConflict ?? (() => {})}
          onViewDetail={onViewDetail ?? (() => {})}
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
          onViewDetail={onViewDetail ?? (() => {})}
          canEdit={!!onEditSchedule}
        />
      )}
    </section>
  );
}
