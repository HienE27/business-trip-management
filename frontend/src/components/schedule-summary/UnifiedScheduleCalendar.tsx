import { weekDays, weekDaysRow2 } from "@/data/schedule-summary";
import { toneStyles } from "./tone-styles";

const SHIFT_TONE_BG: Record<string, string> = {
  duty24: "bg-primary-container text-on-primary-container border-primary",
  allDay: "bg-secondary-container text-on-secondary-container border-secondary",
  serviceClinic: "bg-orange-100 text-orange-700 border-orange-600",
  expertClinic: "bg-purple-100 text-purple-700 border-purple-600",
  off: "bg-surface-variant text-on-surface-variant border-outline-variant/30",
};

const SHIFT_TONE_NEUTRAL: Record<string, string> = {
  duty24: "bg-primary-container/50 text-on-primary-container border-primary",
  allDay: "bg-secondary-container/50 text-on-secondary-container border-secondary",
  serviceClinic: "bg-orange-50 text-orange-700 border-orange-600",
  expertClinic: "bg-purple-50 text-purple-700 border-purple-600",
  off: "bg-surface-container-low/30 text-on-surface-variant border-outline-variant/30",
};

function ShiftBadge({ label, tone }: { label: string; tone: string }) {
  return (
    <div
      className={`text-[11px] font-medium px-2 py-1 rounded shadow-sm border-l-2 truncate ${
        SHIFT_TONE_BG[tone] || SHIFT_TONE_BG.duty24
      }`}
      title={label}
    >
      {label}
    </div>
  );
}

function DayColumn({
  day,
  isToday,
  isWeekend,
  isLast,
  isTodayHighlight,
}: {
  day: (typeof weekDays)[number];
  isToday?: boolean;
  isWeekend?: boolean;
  isLast?: boolean;
  isTodayHighlight?: boolean;
}) {
  return (
    <div
      className={`border-r ${isLast ? "" : "border-outline-variant/20"} p-2 flex flex-col gap-1.5 ${
        isTodayHighlight ? "bg-primary/5" : isWeekend ? "bg-surface-container-low/30" : "hover:bg-surface-container-low/30"
      } transition-colors min-h-[140px]`}
    >
      {day.shifts.map((shift, i) => (
        <ShiftBadge key={i} label={shift.label} tone={shift.tone} />
      ))}
    </div>
  );
}

export function UnifiedScheduleCalendar() {
  const todayIndex = weekDays.findIndex((d) => d.isToday);

  return (
    <div className="flex flex-col rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] overflow-hidden">
      {/* Weekly Header Grid */}
      <div className="grid grid-cols-7 border-b border-outline-variant/40 bg-surface-bright">
        {weekDays.map((day, i) => (
          <div
            key={day.dayName}
            className={`p-3 text-center ${i < weekDays.length - 1 ? "border-r border-outline-variant/20" : ""} ${
              day.isToday ? "bg-primary/5 relative" : ""
            }`}
          >
            {day.isToday && (
              <div className="absolute top-0 left-0 w-full h-1 bg-primary" />
            )}
            <div
              className={`font-label-sm uppercase tracking-wider ${
                day.isToday
                  ? "text-primary font-bold"
                  : day.isWeekend
                  ? "text-error"
                  : "text-on-surface-variant"
              }`}
            >
              {day.dayName}
            </div>
            <div
              className={`font-title-lg mt-0.5 ${
                day.isToday ? "text-primary font-bold" : "text-on-surface"
              }`}
            >
              {day.dayNumber}
            </div>
          </div>
        ))}
      </div>

      {/* Calendar Body */}
      <div className="flex-1 overflow-y-auto max-h-[700px]">
        {/* Row 1 */}
        <div className="grid grid-cols-7 border-b border-outline-variant/20">
          {weekDays.map((day, i) => (
            <DayColumn
              key={day.dayName}
              day={day}
              isToday={day.isToday}
              isWeekend={day.isWeekend}
              isLast={i === weekDays.length - 1}
              isTodayHighlight={day.isToday}
            />
          ))}
        </div>

        {/* Row 2 */}
        <div className="grid grid-cols-7 border-b border-outline-variant/20">
          {weekDaysRow2.map((day, i) => (
            <DayColumn
              key={`row2-${day.dayName}`}
              day={day}
              isWeekend={day.isWeekend}
              isLast={i === weekDays.length - 1}
              isTodayHighlight={day.isToday}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
