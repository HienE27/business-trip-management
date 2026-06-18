"use client";

import { useId, useMemo } from "react";
import {
  CONFLICT_OPTIONS,
  SHIFT_TYPE_OPTIONS,
  type FilterConflict,
} from "./constants";
import type { TableFilters } from "./filterSort";
import { countActiveFilters, getUniqueStaff } from "./filterSort";
import type { Schedule } from "@/types/api";

export type ScheduleTableToolbarProps = {
  schedules: Schedule[];
  filters: TableFilters;
  /** Live search input value (updated on every keystroke, before debounce). */
  searchValue: string;
  onSearchChange: (v: string) => void;
  onTypeChange: (v: string) => void;
  onStaffChange: (v: string) => void;
  onConflictChange: (v: FilterConflict) => void;
  onDateFromChange: (v: string) => void;
  onDateToChange: (v: string) => void;
  onReset: () => void;
  resultCount: number;
  conflictCount: number;
};

export function ScheduleTableToolbar({
  schedules,
  filters,
  searchValue,
  onSearchChange,
  onTypeChange,
  onStaffChange,
  onConflictChange,
  onDateFromChange,
  onDateToChange,
  onReset,
  resultCount,
  conflictCount,
}: ScheduleTableToolbarProps) {
  const uid = useId();
  const allStaff = useMemo(() => getUniqueStaff(schedules), [schedules]);
  const activeCount = countActiveFilters(filters);

  return (
    <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low shrink-0 space-y-3">
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[240px] max-w-md">
          <span aria-hidden="true" className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-[20px] text-on-surface-variant">search</span>
          <label className="sr-only" htmlFor={`${uid}-search`}>Tìm kiếm lịch</label>
          <input
            id={`${uid}-search`}
            type="text"
            value={searchValue}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Tìm theo tên nhân sự, loại ca, mã lịch..."
            className="w-full h-10 pl-9 pr-20 rounded-lg border border-outline-variant bg-surface text-label-md text-on-surface placeholder:text-on-surface-variant/50 outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
          />
          {!searchValue && (
            <kbd aria-hidden="true" className="absolute right-3 top-1/2 -translate-y-1/2 hidden sm:flex items-center gap-0.5 text-label-sm text-on-surface-variant bg-surface-container-highest border border-outline-variant rounded px-1.5 h-5 font-mono">
              Ctrl K
            </kbd>
          )}
          {searchValue && (
            <button
              type="button"
              onClick={() => onSearchChange("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded hover:bg-surface-container-high focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              aria-label="Xóa tìm kiếm"
            >
              <span aria-hidden="true" className="material-symbols-outlined text-[16px] text-on-surface-variant">close</span>
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0" role="status" aria-live="polite">
          <span className="text-label-sm text-on-surface-variant tabular-nums">
            <strong className="font-semibold text-on-surface">{resultCount}</strong> kết quả
          </span>
          {activeCount > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary-fixed text-primary text-label-sm font-semibold">
              <span aria-hidden="true" className="material-symbols-outlined text-[12px]">filter_alt</span>
              {activeCount} bộ lọc
            </span>
          )}
          {conflictCount > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-error-container text-error text-label-sm font-semibold">
              <span aria-hidden="true" className="w-1.5 h-1.5 rounded-full bg-error" />
              {conflictCount} xung đột
            </span>
          )}
        </div>
      </div>

      <div role="group" aria-label="Bộ lọc lịch trực" className="flex flex-wrap items-center gap-2">

        <div className="flex items-center gap-1.5 bg-surface-container-lowest rounded-lg border border-outline-variant p-1">
          <span className="flex items-center gap-1 px-2 text-label-sm text-on-surface-variant font-medium">
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">calendar_today</span>
            Khoảng ngày
          </span>
          <div className="flex items-center gap-1.5">
            <label htmlFor={`${uid}-from`} className="sr-only">Từ ngày</label>
            <input
              id={`${uid}-from`}
              type="date"
              value={filters.dateFrom}
              onChange={(e) => onDateFromChange(e.target.value)}
              className="h-8 pl-2 pr-1 rounded-md border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 cursor-pointer"
              aria-label="Từ ngày"
            />
          </div>
          <span aria-hidden="true" className="text-on-surface-variant">→</span>
          <div className="flex items-center gap-1.5">
            <label htmlFor={`${uid}-to`} className="sr-only">Đến ngày</label>
            <input
              id={`${uid}-to`}
              type="date"
              value={filters.dateTo}
              onChange={(e) => onDateToChange(e.target.value)}
              className="h-8 pl-2 pr-1 rounded-md border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 cursor-pointer"
              aria-label="Đến ngày"
            />
          </div>
        </div>

        <div className="flex items-center gap-1 bg-surface-container-lowest rounded-lg border border-outline-variant p-1" role="group" aria-label="Lọc theo loại ca">
          <span className="px-2 text-label-sm text-on-surface-variant font-medium sr-only">Loại ca</span>
          {SHIFT_TYPE_OPTIONS.map((opt) => {
            const isActive = filters.filterType === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => onTypeChange(opt.value)}
                aria-pressed={isActive}
                aria-label={opt.title}
                title={opt.title}
                className={`flex items-center gap-1.5 h-8 px-2.5 rounded-md text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  isActive ? "bg-primary text-on-primary" : "text-on-surface-variant hover:bg-surface-container-low"
                }`}
              >
                {opt.dot && (
                  <span aria-hidden="true" className={`w-2 h-2 rounded-full ${isActive ? "bg-on-primary" : opt.dot}`} />
                )}
                {opt.label}
              </button>
            );
          })}
        </div>

        <div className="relative">
          <label htmlFor={`${uid}-staff`} className="sr-only">Lọc theo nhân sự</label>
          <select
            id={`${uid}-staff`}
            value={filters.filterStaff}
            onChange={(e) => onStaffChange(e.target.value)}
            className="h-10 pl-3 pr-8 appearance-none rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 cursor-pointer min-w-[180px] max-w-[260px]"
          >
            <option value="all">Tất cả nhân sự</option>
            {allStaff.map(([id, name]) => (
              <option key={id} value={id}>{name}</option>
            ))}
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[16px]">expand_more</span>
        </div>

        <div className="relative">
          <label htmlFor={`${uid}-conflict`} className="sr-only">Lọc theo xung đột</label>
          <select
            id={`${uid}-conflict`}
            value={filters.filterConflict}
            onChange={(e) => onConflictChange(e.target.value as FilterConflict)}
            className="h-10 pl-3 pr-8 appearance-none rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 cursor-pointer"
          >
            {CONFLICT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[16px]">expand_more</span>
        </div>

        {activeCount > 0 && (
          <button
            type="button"
            onClick={onReset}
            className="ml-auto h-10 px-3 rounded-lg border border-outline-variant bg-surface text-label-sm text-error hover:bg-error-container transition-colors shrink-0 flex items-center gap-1.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error"
            aria-label="Xóa tất cả bộ lọc"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">filter_alt_off</span>
            Xóa lọc
          </button>
        )}
      </div>
    </div>
  );
}
