import { dashboardCalendar } from "@/data/schedule-dashboard";
import { toneStyles } from "./tone-styles";

const TONE_BG: Record<string, string> = {
  duty24: "bg-primary/10",
  allDay: "bg-secondary/10",
  serviceClinic: "bg-tertiary/10",
  expertClinic: "bg-[#8b5cf6]/10",
};

const TONE_BORDER: Record<string, string> = {
  duty24: "border-primary",
  allDay: "border-secondary",
  serviceClinic: "border-tertiary",
  expertClinic: "border-[#8b5cf6]",
};

const TONE_TEXT: Record<string, string> = {
  duty24: "text-primary",
  allDay: "text-secondary",
  serviceClinic: "text-tertiary",
  expertClinic: "text-[#8b5cf6]",
};

const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] as const;

export function DashboardCalendar() {
  return (
    <section className="flex flex-col rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden">
      {/* Header */}
      <div className="p-4 border-b border-outline-variant flex items-center justify-between bg-surface-bright">
        <h3 className="font-title-lg text-on-surface">{dashboardCalendar.month}</h3>
        <div className="flex gap-1">
          <button className="p-1.5 rounded-lg hover:bg-surface-container-low text-on-surface-variant transition-colors">
            <span className="material-symbols-outlined text-sm">chevron_left</span>
          </button>
          <button className="px-3 py-1.5 rounded-lg hover:bg-surface-container-low text-on-surface font-label-md transition-colors">
            Hôm nay
          </button>
          <button className="p-1.5 rounded-lg hover:bg-surface-container-low text-on-surface-variant transition-colors">
            <span className="material-symbols-outlined text-sm">chevron_right</span>
          </button>
        </div>
      </div>

      {/* Calendar Grid */}
      <div className="flex-1 grid grid-cols-7 grid-rows-[auto_repeat(2,1fr)] bg-surface-container-lowest overflow-hidden">
        {/* Day headers */}
        {WEEKDAYS.map((day, i) => (
          <div
            key={day}
            className={`p-2 text-center font-label-sm text-label-sm border-b border-r border-outline-variant bg-slate-50 ${
              i >= 5 ? "text-error" : "text-on-surface-variant"
            }`}
          >
            {day}
          </div>
        ))}

        {/* Prev month days */}
        {dashboardCalendar.prevDays.map((day) => (
          <div
            key={`prev-${day}`}
            className="border-r border-b border-outline-variant p-1 bg-surface-variant/30 min-h-[80px]"
          >
            <span className="font-label-sm text-label-sm text-on-surface-variant p-1">{day}</span>
          </div>
        ))}

        {/* Current month cells */}
        {dashboardCalendar.cells.map((cell, idx) => {
          const isLast = idx === dashboardCalendar.cells.length - 1;
          return (
            <div
              key={cell.day}
              className={`border-r border-b border-outline-variant p-1 min-h-[80px] relative ${
                cell.isWeekend ? "" : ""
              } ${
                cell.hasConflict
                  ? "ring-2 ring-inset ring-error bg-error/5"
                  : cell.isLocked
                  ? "bg-[repeating-linear-gradient(45deg,transparent,transparent_4px,rgba(0,0,0,0.03)_4px,rgba(0,0,0,0.03)_8px)]"
                  : ""
              }`}
            >
              <span
                className={`font-label-sm text-label-sm p-1 block ${
                  cell.isWeekend ? "text-error" : "text-on-surface"
                } ${cell.isLocked ? "text-on-surface-variant" : ""}`}
              >
                {cell.day}
              </span>

              {cell.isLocked ? (
                <div className="bg-surface-variant text-on-surface-variant px-1 py-0.5 rounded-lg text-[10px] font-medium text-center">
                  {cell.lockedLabel}
                </div>
              ) : (
                cell.items.map((item, i) => (
                  <div
                    key={i}
                    className={`border-l-2 ${TONE_BG[item.tone]} ${TONE_BORDER[item.tone]} ${TONE_TEXT[item.tone]} px-1 py-0.5 rounded-r text-[10px] font-medium mb-1 truncate`}
                  >
                    {item.label}
                  </div>
                ))
              )}

              {cell.hasConflict && (
                <span className="absolute top-1 right-1 material-symbols-outlined text-error text-[14px]">
                  error
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Legend */}
      <div className="p-4 bg-surface-bright border-t border-outline-variant flex flex-wrap gap-4 items-center font-label-sm text-label-sm text-on-surface">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-primary" />
          Trực 24/24
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-secondary" />
          Thông tầm
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-tertiary" />
          Dịch vụ
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-[#8b5cf6]" />
          Chuyên gia
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-surface-variant border border-outline-variant" />
          Khóa/Nghỉ bù
        </div>
      </div>
    </section>
  );
}
