"use client";

import { memo, useMemo } from "react";
import { TONE, SHIFT_FULL_LABEL, type CalendarItem } from "./calendar/constants";
import { buildScheduleMatrix, type ScheduleMatrix } from "./calendar/buildScheduleMatrix";
import type { Schedule } from "@/types/api";
import type { ScheduleTone } from "@/types/schedule";

export type ScheduleMatrixGridProps = {
  schedules: Schedule[];
  staffList: { id: number; fullName: string }[];
  year: number;
  month: number;
  /** Called when user clicks a shift chip */
  onViewDetail?: (schedule: Schedule) => void;
  /** Called when user clicks on a cell (empty or with shifts) */
  onCellClick?: (date: Date, staffId?: number) => void;
};

export const ScheduleMatrixGrid = memo(function ScheduleMatrixGrid({
  schedules,
  staffList,
  year,
  month,
  onViewDetail,
  onCellClick,
}: ScheduleMatrixGridProps) {
  const matrix = useMemo<ScheduleMatrix>(
    () => buildScheduleMatrix(schedules, staffList, year, month),
    [schedules, staffList, year, month]
  );

  const { headerCols, rows } = matrix;

  if (headerCols.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
        <span className="material-symbols-outlined text-5xl text-outline">grid_view</span>
        <p className="text-on-surface-variant">Chưa có nhân sự nào để hiển thị.</p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest overflow-hidden">
      {/* Sticky header row: staff names */}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-[12px]">
          <thead>
            <tr>
              {/* Fixed first column: date header */}
              <th className="sticky left-0 z-10 min-w-[80px] bg-surface-container-low border-b border-r border-outline-variant px-3 py-2.5 text-left font-semibold text-on-surface-variant">
                Ngày
              </th>
              {headerCols.map((staff) => (
                <th
                  key={staff.id}
                  className="min-w-[100px] border-b border-r border-outline-variant bg-surface-container-low px-2 py-2.5 text-center font-semibold text-on-surface"
                >
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-[20px]" style={{ lineHeight: 1 }}>
                      {staff.fullName.charAt(0).toUpperCase()}
                    </span>
                    <span className="text-[11px] font-normal text-on-surface-variant leading-tight text-center line-clamp-2">
                      {staff.fullName}
                    </span>
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.dateStr} className={row.isWeekend ? "bg-surface-container-low/50" : ""}>
                {/* Date label cell */}
                <td className="sticky left-0 z-10 border-b border-r border-outline-variant bg-surface-container-low px-3 py-2 font-semibold text-on-surface">
                  <span className="text-[13px]">{row.dayLabel}</span>
                  <span className="ml-1 text-[11px] text-on-surface-variant">{row.dayOfWeek}</span>
                </td>
                {/* Schedule cells */}
                {headerCols.map((staff) => {
                  const items = row.cells.get(staff.id) ?? [];
                  return (
                    <td
                      key={`${row.dateStr}-${staff.id}`}
                      className={`border-b border-r border-outline-variant p-1 align-top h-16 ${
                        items.length > 0 ? "cursor-pointer hover:bg-surface-container-low transition-colors" : ""
                      }`}
                      onClick={() => {
                        if (items.length === 1 && onViewDetail) {
                          onViewDetail(items[0].schedule);
                        } else if (onCellClick) {
                          onCellClick(row.date, staff.id);
                        }
                      }}
                    >
                      {items.length === 0 ? (
                        <div className="h-full flex items-center justify-center opacity-30">
                          <span className="material-symbols-outlined text-[16px] text-outline">add</span>
                        </div>
                      ) : items.length === 1 ? (
                        <ShiftChip
                          item={items[0]}
                          compact
                          onClick={() => onViewDetail?.(items[0].schedule)}
                        />
                      ) : (
                        <div className="space-y-0.5">
                          {items.slice(0, 3).map((item, i) => (
                            <ShiftChip
                              key={i}
                              item={item}
                              compact
                              onClick={() => onViewDetail?.(item.schedule)}
                            />
                          ))}
                          {items.length > 3 && (
                            <p className="text-[10px] text-on-surface-variant text-center">
                              +{items.length - 3} khác
                            </p>
                          )}
                        </div>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
});

type ShiftChipProps = {
  item: {
    shiftLabel: string;
    staffName: string;
    tone: ScheduleTone;
    shiftTypeId: string;
    isOvernight: boolean;
    schedule: Schedule;
  };
  compact?: boolean;
  onClick?: () => void;
};

const ShiftChip = memo(function ShiftChip({ item, compact, onClick }: ShiftChipProps) {
  const tone = TONE[item.tone] ?? TONE.neutral;

  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onClick?.(); }}
      className={`w-full rounded border px-1.5 py-0.5 text-left transition-all hover:brightness-90 ${tone.bg} ${tone.text} ${tone.border} ${compact ? "text-[10px]" : "text-[11px]"}`}
      title={`${item.staffName} · ${SHIFT_FULL_LABEL[item.shiftTypeId] ?? item.shiftLabel}`}
    >
      {compact ? (
        <span className="font-semibold">{item.shiftLabel}</span>
      ) : (
        <div>
          <p className="font-semibold leading-tight">{item.shiftLabel}</p>
          <p className="text-[10px] opacity-75 leading-tight">{item.staffName}</p>
        </div>
      )}
    </button>
  );
});
