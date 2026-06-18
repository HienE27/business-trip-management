"use client";

import { type CalendarViewMode } from "./constants";

export type CalendarToolbarProps = {
  viewMode: CalendarViewMode;
  onViewModeChange: (mode: CalendarViewMode) => void;
  monthLabel: string;
  weekLabel: string;
  onPrev: () => void;
  onNext: () => void;
  onToday: () => void;
  activeFilter: string;
  onFilterChange: (filter: string) => void;
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
};

/**
 * Top toolbar của DashboardCalendar: label, nav (prev/next/today), view toggle,
 * filter chips. Tách riêng để DashboardCalendar tập trung vào grid render.
 */
export function CalendarToolbar({
  viewMode,
  onViewModeChange,
  monthLabel,
  weekLabel,
  onPrev,
  onNext,
  onToday,
  activeFilter,
  onFilterChange,
  sidebarOpen,
  onToggleSidebar,
}: CalendarToolbarProps) {
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3 border-b border-outline-variant bg-surface-container-low shrink-0 flex-wrap">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onToggleSidebar}
          className="p-2 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          aria-label={sidebarOpen ? "Ẩn thanh bên" : "Hiện thanh bên"}
          aria-expanded={sidebarOpen}
          aria-controls="calendar-sidebar"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-[20px]">
            {sidebarOpen ? "side_navigation" : "menu_open"}
          </span>
        </button>

        <div className="flex items-center gap-2">
          <h3 className="text-title-lg text-on-surface font-semibold min-w-[180px]" aria-live="polite">
            {viewMode === "week" ? weekLabel : monthLabel}
          </h3>
          <span className="hidden md:inline-flex items-center gap-1 text-label-sm text-on-surface-variant" title="← / → để chuyển tháng/tuần · Home để về hôm nay">
            <kbd className="inline-flex items-center justify-center min-w-[20px] h-5 px-1 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">←</kbd>
            <kbd className="inline-flex items-center justify-center min-w-[20px] h-5 px-1 text-[10px] font-mono bg-surface-container-highest border border-outline-variant rounded">→</kbd>
          </span>
        </div>

        <div className="flex items-center bg-surface-container-low rounded-lg p-0.5 gap-0.5">
          <button
            type="button"
            onClick={onPrev}
            className="p-2 rounded-md hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            aria-label={viewMode === "week" ? "Tuần trước" : "Tháng trước"}
          >
            <span className="material-symbols-outlined text-[18px]">chevron_left</span>
          </button>
          <button
            type="button"
            onClick={onToday}
            className="px-3 py-2 rounded-md hover:bg-surface-container-high text-on-surface text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset whitespace-nowrap"
            aria-label="Quay về hôm nay (phím Home)"
          >
            Hôm nay
          </button>
          <button
            type="button"
            onClick={onNext}
            className="p-2 rounded-md hover:bg-surface-container-high text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            aria-label={viewMode === "week" ? "Tuần sau" : "Tháng sau"}
          >
            <span className="material-symbols-outlined text-[18px]">chevron_right</span>
          </button>
        </div>
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-1 bg-surface-container-low rounded-lg p-0.5">
          {([
            { value: "month" as CalendarViewMode, label: "Tháng", icon: "calendar_month", shortcut: "T" },
            { value: "week" as CalendarViewMode, label: "Tuần", icon: "view_week", shortcut: "W" },
          ]).map((v) => (
            <button
              key={v.value}
              type="button"
              onClick={() => onViewModeChange(v.value)}
              aria-pressed={viewMode === v.value}
              aria-keyshortcuts={v.shortcut}
              title={`${v.label} (phím ${v.shortcut})`}
              className={`flex items-center gap-1.5 px-3 py-2 rounded-md text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary whitespace-nowrap ${
                viewMode === v.value
                  ? "bg-surface-container-lowest text-primary shadow-sm"
                  : "text-on-surface-variant hover:text-on-surface"
              }`}
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{v.icon}</span>
              {v.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-1 bg-surface-container-low rounded-lg p-0.5">
          {[
            { value: "ALL", label: "Tất cả", title: "Hiển thị tất cả loại lịch" },
            { value: "L01", label: "24/24", title: "Lọc theo Trực 24/24", color: "bg-primary", activeColor: "bg-primary text-on-primary" },
            { value: "L02", label: "TT", title: "Lọc theo Thông tầm", color: "bg-secondary", activeColor: "bg-secondary text-on-secondary" },
            { value: "L03", label: "DV", title: "Lọc theo Phòng khám dịch vụ", color: "bg-tertiary", activeColor: "bg-tertiary text-on-tertiary" },
            { value: "L04", label: "CG", title: "Lọc theo Phòng khám chuyên gia", color: "bg-expert", activeColor: "bg-expert text-white" },
          ].map((f) => {
            const isActive = activeFilter === f.value || (f.value === "ALL" && (activeFilter === "all" || !activeFilter));
            return (
              <button
                key={f.value}
                type="button"
                onClick={() => onFilterChange(f.value)}
                aria-pressed={isActive}
                aria-label={f.title}
                title={f.title}
                className={`flex items-center gap-1.5 px-2.5 py-2 rounded-md text-label-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary whitespace-nowrap ${
                  isActive
                    ? f.activeColor ?? "bg-surface-container-lowest text-on-surface shadow-sm"
                    : "text-on-surface-variant hover:text-on-surface"
                }`}
              >
                {f.color && (
                  <span
                    aria-hidden="true"
                    className={`inline-block w-2 h-2 rounded-full ${isActive ? "bg-on-primary" : f.color}`}
                  />
                )}
                {f.label}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
