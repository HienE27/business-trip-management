"use client";

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
      inCurrentMonth: date.getMonth() === monthIndex,
    } satisfies DayCell;
  });
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

  return (
    <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() - 1, 1))}
          disabled={disabled}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-outline-variant bg-surface text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-50"
          aria-label="Tháng trước"
        >
          <span className="material-symbols-outlined text-[18px]">chevron_left</span>
        </button>
        <div className="text-center">
          <p className="text-label-sm text-on-surface-variant">Lịch tháng</p>
          <h3 className="text-title-lg text-on-surface">{title}</h3>
        </div>
        <button
          type="button"
          onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() + 1, 1))}
          disabled={disabled}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-outline-variant bg-surface text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-50"
          aria-label="Tháng sau"
        >
          <span className="material-symbols-outlined text-[18px]">chevron_right</span>
        </button>
      </div>

      <div className="grid grid-cols-7 gap-2 text-center">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="py-2 text-label-sm text-on-surface-variant">
            {label}
          </div>
        ))}

        {monthGrid.map((cell) => {
          const isSelected = selectedDates.has(cell.iso);
          const isBlocked = blockedDates.has(cell.iso);
          const isHighlighted = highlightedDates.has(cell.iso);

          return (
            <button
              key={cell.iso}
              type="button"
              disabled={disabled || isBlocked}
              onClick={() => onToggleDate(cell.iso)}
              className={[
                "flex aspect-square items-center justify-center rounded-lg border text-body-sm transition-all",
                cell.inCurrentMonth
                  ? "border-outline-variant bg-surface text-on-surface"
                  : "border-outline-variant/50 bg-surface-container-low text-outline",
                isSelected
                  ? "border-primary bg-primary text-on-primary shadow-sm"
                  : "hover:bg-surface-container-low",
                isBlocked ? "cursor-not-allowed border-error/20 bg-error-container/40 text-error line-through opacity-70" : "",
                isHighlighted && !isSelected && !isBlocked ? "border-tertiary bg-tertiary-fixed/50 text-tertiary" : "",
              ].join(" ")}
              title={isBlocked ? "Ngày này đang bị khóa" : cell.iso}
            >
              {cell.dayNumber}
            </button>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3 text-label-sm text-on-surface-variant">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded border border-outline-variant bg-surface" /> Ngày khả dụng
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded border border-primary bg-primary" /> Đã chọn
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded border border-error/20 bg-error-container" /> Bị khóa
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded border border-tertiary bg-tertiary-fixed/50" /> Có lịch liên quan
        </span>
      </div>

      {helperText ? <p className="mt-3 text-label-sm text-on-surface-variant">{helperText}</p> : null}
    </div>
  );
}
