"use client";

import { useId, useEffect, useRef } from "react";

const WEEKDAY_LABELS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
const MONTH_LABELS = [
  "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
  "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12",
];

type MonthDateGridProps = {
  month: Date;
  selectedDates: Set<string>;
  onToggleDate: (date: string) => void;
  onMonthChange: (nextMonth: Date) => void;
  blockedDates?: Set<string>;
  highlightedDates?: Set<string>;
  disabled?: boolean;
  helperText?: string;
};

type DayCell = {
  iso: string;
  dayNumber: number;
  date: Date;
  inCurrentMonth: boolean;
};

function toIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getMonthGrid(month: Date): DayCell[] {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const firstDay = new Date(year, monthIndex, 1);
  const startOffset = (firstDay.getDay() + 6) % 7;
  const gridStart = new Date(year, monthIndex, 1 - startOffset);

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    return {
      iso: toIsoDate(date),
      dayNumber: date.getDate(),
      date,
      inCurrentMonth: date.getMonth() === monthIndex,
    } satisfies DayCell;
  });
}

const WEEKDAY_LABELS_FULL = [
  "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật",
] as const;

function formatVietnameseDate(date: Date): string {
  const dow = WEEKDAY_LABELS_FULL[(date.getDay() + 6) % 7];
  return `${dow}, ngày ${date.getDate()}/${date.getMonth() + 1}/${date.getFullYear()}`;
}

export function MonthDateGrid({
  month,
  selectedDates,
  onToggleDate,
  onMonthChange,
  blockedDates = new Set(),
  highlightedDates = new Set(),
  disabled = false,
  helperText,
}: MonthDateGridProps) {
  const monthGrid = getMonthGrid(month);
  const title = `${MONTH_LABELS[month.getMonth()]} ${month.getFullYear()}`;
  const gridId = useId();
  const today = new Date();
  const todayIso = toIsoDate(today);
  const isCurrentMonth = today.getFullYear() === month.getFullYear() && today.getMonth() === month.getMonth();

  // Keyboard navigation: Arrow keys + Home/End + PageUp/PageDown
  const focusDate = useRef<string | null>(null);
  useEffect(() => {
    if (!focusDate.current) return;
    const el = document.getElementById(`${gridId}-${focusDate.current}`);
    el?.focus();
    focusDate.current = null;
  }, [month, gridId]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>, cell: DayCell) => {
    if (disabled) return;
    let nextIso: string | null = null;
    switch (e.key) {
      case "ArrowLeft":
        nextIso = toIsoDate(new Date(cell.date.getTime() - 86400000));
        break;
      case "ArrowRight":
        nextIso = toIsoDate(new Date(cell.date.getTime() + 86400000));
        break;
      case "ArrowUp":
        nextIso = toIsoDate(new Date(cell.date.getTime() - 7 * 86400000));
        break;
      case "ArrowDown":
        nextIso = toIsoDate(new Date(cell.date.getTime() + 7 * 86400000));
        break;
      case "Home": {
        e.preventDefault();
        const firstOfMonth = new Date(cell.date.getFullYear(), cell.date.getMonth(), 1);
        const dow = (firstOfMonth.getDay() + 6) % 7;
        firstOfMonth.setDate(firstOfMonth.getDate() - dow);
        nextIso = toIsoDate(firstOfMonth);
        break;
      }
      case "End": {
        e.preventDefault();
        const lastOfMonth = new Date(cell.date.getFullYear(), cell.date.getMonth() + 1, 0);
        nextIso = toIsoDate(lastOfMonth);
        break;
      }
      case "PageUp":
        e.preventDefault();
        onMonthChange(new Date(cell.date.getFullYear(), cell.date.getMonth() - 1, 1));
        return;
      case "PageDown":
        e.preventDefault();
        onMonthChange(new Date(cell.date.getFullYear(), cell.date.getMonth() + 1, 1));
        return;
      case "t":
      case "T":
        if (isCurrentMonth) {
          e.preventDefault();
          const el = document.getElementById(`${gridId}-${todayIso}`);
          el?.focus();
        }
        return;
    }
    if (nextIso) {
      e.preventDefault();
      const next = new Date(nextIso + "T00:00:00");
      if (next.getMonth() !== month.getMonth() || next.getFullYear() !== month.getFullYear()) {
        onMonthChange(new Date(next.getFullYear(), next.getMonth(), 1));
        focusDate.current = nextIso;
      } else {
        const el = document.getElementById(`${gridId}-${nextIso}`);
        el?.focus();
      }
    }
  };

  return (
    <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() - 1, 1))}
          disabled={disabled}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-outline-variant bg-surface text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
          aria-label="Tháng trước"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-[18px]">chevron_left</span>
        </button>
        <div className="text-center flex-1">
          <p className="text-label-sm text-on-surface-variant">Lịch tháng</p>
          <h3 className="text-title-lg text-on-surface" aria-live="polite">{title}</h3>
        </div>
        <div className="flex items-center gap-1">
          {isCurrentMonth && (
            <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-primary-fixed text-primary text-label-sm font-semibold" aria-label="Đang ở tháng hiện tại">
              <span aria-hidden="true" className="material-symbols-outlined text-[12px]">today</span>
              Hôm nay
            </span>
          )}
          <button
            type="button"
            onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() + 1, 1))}
            disabled={disabled}
            className="flex h-9 w-9 items-center justify-center rounded-lg border border-outline-variant bg-surface text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
            aria-label="Tháng sau"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">chevron_right</span>
          </button>
        </div>
      </div>

      <div
        role="grid"
        aria-labelledby={`${gridId}-title`}
        className="grid grid-cols-7 gap-2 text-center"
      >
        <span id={`${gridId}-title`} className="sr-only">{title}</span>
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} role="columnheader" className="py-2 text-label-sm text-on-surface-variant font-semibold">
            <span className="hidden sm:inline">{WEEKDAY_LABELS_FULL[WEEKDAY_LABELS.indexOf(label)]}</span>
            <span className="sm:hidden">{label}</span>
          </div>
        ))}

        {monthGrid.map((cell) => {
          const isSelected = selectedDates.has(cell.iso);
          const isBlocked = blockedDates.has(cell.iso);
          const isHighlighted = highlightedDates.has(cell.iso);
          const isToday = cell.iso === todayIso;
          const ariaLabel = [
            formatVietnameseDate(cell.date),
            isToday ? "(hôm nay)" : "",
            isSelected ? "(đã chọn)" : "",
            isBlocked ? "(bị khóa)" : "",
            isHighlighted && !isSelected ? "(có lịch liên quan)" : "",
          ].filter(Boolean).join(" ");

          return (
            <button
              key={cell.iso}
              id={`${gridId}-${cell.iso}`}
              type="button"
              role="gridcell"
              aria-selected={isSelected}
              aria-disabled={disabled || isBlocked}
              aria-current={isToday ? "date" : undefined}
              aria-label={ariaLabel}
              disabled={disabled || isBlocked}
              onClick={() => onToggleDate(cell.iso)}
              onKeyDown={(e) => handleKeyDown(e, cell)}
              className={[
                "relative flex aspect-square items-center justify-center rounded-lg border text-body-sm transition-all",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1",
                cell.inCurrentMonth
                  ? "border-outline-variant bg-surface text-on-surface"
                  : "border-outline-variant/50 bg-surface-container-low text-outline",
                isSelected
                  ? "border-primary bg-primary text-on-primary shadow-sm font-bold"
                  : isToday
                  ? "border-primary bg-primary-fixed/40 text-primary font-bold hover:bg-primary-fixed/60"
                  : "hover:bg-surface-container-low",
                isBlocked ? "cursor-not-allowed border-error/30 bg-error-container/40 text-error line-through opacity-70" : "cursor-pointer",
                isHighlighted && !isSelected && !isBlocked ? "border-tertiary bg-tertiary-fixed/50 text-tertiary" : "",
              ].join(" ")}
            >
              {cell.dayNumber}
              {isToday && !isSelected && (
                <span aria-hidden="true" className="absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary" />
              )}
            </button>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3 text-label-sm text-on-surface-variant">
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="h-3 w-3 rounded border border-outline-variant bg-surface" /> Ngày khả dụng
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="h-3 w-3 rounded border border-primary bg-primary" /> Đã chọn
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="h-3 w-3 rounded border border-primary bg-primary-fixed/40" /> Hôm nay
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="h-3 w-3 rounded border border-error/30 bg-error-container" /> Bị khóa
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="h-3 w-3 rounded border border-tertiary bg-tertiary-fixed/50" /> Có lịch liên quan
        </span>
      </div>

      <p className="mt-3 text-label-sm text-on-surface-variant hidden sm:block">
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">←</kbd>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">→</kbd>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">↑</kbd>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">↓</kbd>
        <span className="ml-1">để di chuyển, </span>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">Home</kbd>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">End</kbd>
        <span className="ml-1">đầu/cuối tháng, </span>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">PgUp</kbd>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">PgDn</kbd>
        <span className="ml-1">đổi tháng, </span>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">T</kbd>
        <span className="ml-1">hôm nay, </span>
        <kbd className="px-1 py-0.5 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">Space</kbd>
        <span className="ml-1">chọn</span>
      </p>

      {helperText ? <p className="mt-3 text-label-sm text-on-surface-variant">{helperText}</p> : null}
    </div>
  );
}
