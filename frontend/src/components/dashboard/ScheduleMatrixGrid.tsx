"use client";

import { memo, useCallback, useMemo, useState } from "react";
import { TONE, SHIFT_FULL_LABEL, SHIFT_SHORT, type CalendarItem } from "./calendar/constants";
import { buildScheduleMatrix, type MatrixRow } from "./calendar/buildScheduleMatrix";
import { EventTooltip, type TooltipData } from "./calendar/EventTooltip";
import type { Schedule, CompensationDay } from "@/types/api";
import type { ScheduleTone } from "@/types/schedule";

export type ScheduleMatrixGridProps = {
  schedules: Schedule[];
  staffList: { id: number; fullName: string }[];
  year: number;
  month: number;
  compensationDays?: CompensationDay[];
  /** Filter shifts by type (L01/L02/L03/L04). Pass "ALL" to show all. */
  shiftTypeFilter?: string;
  /** Called when user clicks a shift chip to open detail/edit */
  onViewDetail?: (schedule: Schedule) => void;
  /** Called when user clicks an empty cell to assign */
  onCellClick?: (date: Date, staffId: number) => void;
  /** Called after inline edit saves to refresh data */
  onRefresh?: () => void;
  /** Override edit button visibility (defaults to true when onViewDetail is provided) */
  canEdit?: boolean;
};

function ShiftChip({
  item,
  compact,
  onClick,
}: {
  item: CalendarItem;
  compact?: boolean;
  onClick?: (e: React.MouseEvent) => void;
}) {
  const tone = TONE[item.tone] ?? TONE.neutral;
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        onClick?.(e);
      }}
      className={`w-full rounded border px-1 py-0.5 text-left transition-all hover:brightness-90 ${tone.bg} ${tone.text} ${tone.border} ${compact ? "text-[10px]" : "text-[11px]"}`}
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
}

export const ScheduleMatrixGrid = memo(function ScheduleMatrixGrid({
  schedules,
  staffList,
  year,
  month,
  compensationDays = [],
  shiftTypeFilter = "ALL",
  onViewDetail,
  onCellClick,
  onRefresh,
  canEdit,
}: ScheduleMatrixGridProps) {
  const matrix = useMemo<MatrixRow[]>(
    () => {
      const all = buildScheduleMatrix(schedules, staffList, year, month, compensationDays);
      if (shiftTypeFilter === "ALL") return all.rows;
      return all.rows.map((row) => {
        const filtered = new Map<number, CalendarItem[]>();
        for (const [staffId, items] of row.cells) {
          const filteredItems = items.filter(
            (item) => item.shiftTypeId === shiftTypeFilter
          );
          filtered.set(staffId, filteredItems);
        }
        return { ...row, cells: filtered };
      });
    },
    [schedules, staffList, year, month, compensationDays, shiftTypeFilter]
  );

  const [tooltip, setTooltip] = useState<TooltipData | null>(null);

  const handleCellClick = useCallback(
    (item: CalendarItem, e: React.MouseEvent) => {
      if (item.schedule) {
        setTooltip({
          x: e.clientX,
          y: e.clientY,
          item,
        });
      }
    },
    []
  );

  if (staffList.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
        <span className="material-symbols-outlined text-5xl text-outline">grid_view</span>
        <p className="text-on-surface-variant">Chưa có nhân sự nào để hiển thị.</p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-[12px]">
          <thead>
            <tr>
              {/* Fixed first column: date header */}
              <th className="sticky left-0 z-20 min-w-[80px] bg-surface-container-low border-b border-r border-outline-variant px-3 py-2.5 text-left font-semibold text-on-surface-variant">
                <div className="flex flex-col items-center gap-0.5">
                  <span className="text-[11px] uppercase tracking-wide">Ngày</span>
                </div>
              </th>
              {staffList.map((staff) => {
                const totalShifts = schedules.filter(
                  (s) =>
                    s.staff.id === staff.id &&
                    new Date(s.workDate).getFullYear() === year &&
                    new Date(s.workDate).getMonth() === month
                ).length;
                return (
                  <th
                    key={staff.id}
                    className="min-w-[100px] border-b border-r border-outline-variant bg-surface-container-low px-2 py-2.5 text-center"
                  >
                    <div className="flex flex-col items-center gap-1">
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-fixed text-[12px] font-bold text-primary">
                        {staff.fullName
                          .split(" ")
                          .slice(-2)
                          .map((p) => p[0])
                          .join("")
                          .toUpperCase()}
                      </div>
                      <span className="text-[11px] font-semibold text-on-surface leading-tight text-center line-clamp-2 max-w-[90px]">
                        {staff.fullName}
                      </span>
                      {totalShifts > 0 && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold text-primary">
                          {totalShifts} ca
                        </span>
                      )}
                    </div>
                  </th>
                );
              })}
              {/* Stats column */}
              <th className="min-w-[60px] border-b border-outline-variant bg-surface-container-low px-2 py-2.5 text-center">
                <span className="text-[11px] font-semibold text-on-surface-variant">Tổng</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {matrix.map((row) => {
              const rowCount = Array.from(row.cells.values()).filter(
                (items) => items.length > 0
              ).length;

              return (
                <tr
                  key={row.dateStr}
                  className={`group ${
                    row.isWeekend ? "bg-surface-container-low/40" : "hover:bg-surface-container-low/30"
                  } transition-colors`}
                >
                  {/* Date label cell */}
                  <td className="sticky left-0 z-20 border-b border-r border-outline-variant bg-surface-container-low px-3 py-2 font-semibold">
                    <div className="flex flex-col">
                      <span className="text-[13px] text-on-surface">{row.dayLabel}</span>
                      <span
                        className={`text-[11px] font-medium ${
                          row.dayOfWeek === "CN"
                            ? "text-error"
                            : row.isWeekend
                            ? "text-tertiary"
                            : "text-on-surface-variant"
                        }`}
                      >
                        {row.dayOfWeek}
                      </span>
                    </div>
                  </td>

                  {/* Schedule cells */}
                  {staffList.map((staff) => {
                    const items = row.cells.get(staff.id) ?? [];
                    const isComp = row.isCompensation?.get(staff.id) ?? false;

                    if (isComp) {
                      return (
                        <td
                          key={`${row.dateStr}-${staff.id}`}
                          className="border-b border-r border-outline-variant p-1 align-top h-16 bg-surface-container-high/60 cursor-default"
                          title="Ngày nghỉ bù — không thể xếp lịch"
                        >
                          <div className="h-full flex flex-col items-center justify-center gap-1">
                            <div className="flex items-center gap-1 rounded-full bg-outline px-2 py-0.5 text-[10px] font-semibold text-white">
                              <span className="material-symbols-outlined text-[12px]">hotel</span>
                              NB
                            </div>
                            <span className="text-[9px] text-outline text-center leading-tight">
                              Nghỉ bù
                            </span>
                          </div>
                        </td>
                      );
                    }

                    if (items.length === 0) {
                      return (
                        <td
                          key={`${row.dateStr}-${staff.id}`}
                          className="border-b border-r border-outline-variant p-1 align-top h-16"
                        >
                          <div className="h-full flex items-center justify-center">
                            {onCellClick ? (
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onCellClick(row.date, staff.id);
                                }}
                                className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center w-7 h-7 rounded-full bg-primary/10 hover:bg-primary/20 text-primary"
                                title={`Thêm ca cho ${staff.fullName} ngày ${row.dayLabel}`}
                              >
                                <span className="material-symbols-outlined text-[14px]">add</span>
                              </button>
                            ) : (
                              <span className="material-symbols-outlined text-[14px] text-outline/40">remove</span>
                            )}
                          </div>
                        </td>
                      );
                    }

                    if (items.length === 1) {
                      return (
                        <td
                          key={`${row.dateStr}-${staff.id}`}
                          className="border-b border-r border-outline-variant p-1 align-top h-16"
                        >
                          <ShiftChip
                            item={items[0]}
                            compact
                            onClick={(e) => handleCellClick(items[0], e)}
                          />
                        </td>
                      );
                    }

                    const visible = items.slice(0, 2);
                    const overflow = items.length - visible.length;
                    return (
                      <td
                        key={`${row.dateStr}-${staff.id}`}
                        className="border-b border-r border-outline-variant p-1 align-top h-16"
                      >
                        <div className="space-y-0.5">
                          {visible.map((item, i) => (
                            <ShiftChip
                              key={i}
                              item={item}
                              compact
                              onClick={(e) => handleCellClick(item, e)}
                            />
                          ))}
                          {overflow > 0 && (
                            <div className="flex items-center justify-center rounded border bg-surface-container-low px-1 py-0.5 text-[10px] text-on-surface-variant font-medium">
                              +{overflow} ca
                            </div>
                          )}
                        </div>
                      </td>
                    );
                  })}

                  {/* Row stats: total assignments this day */}
                  <td className="border-b border-outline-variant px-2 py-2 text-center align-middle">
                    {rowCount > 0 ? (
                      <span className="inline-flex h-6 min-w-[24px] items-center justify-center rounded-full bg-primary/10 px-1.5 text-[11px] font-semibold text-primary">
                        {rowCount}
                      </span>
                    ) : (
                      <span className="material-symbols-outlined text-[14px] text-outline/40">remove</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Inline edit tooltip */}
      {tooltip && (
        <EventTooltip
          data={tooltip}
          onEdit={onViewDetail ?? (() => {})}
          onDelete={onViewDetail ?? (() => {})}
          onResolve={() => {}}
          onViewDetail={(s) => { onViewDetail?.(s); setTooltip(null); }}
          onRefresh={onRefresh}
          onClose={() => setTooltip(null)}
          canEdit={canEdit ?? !!onViewDetail}
        />
      )}
    </div>
  );
});
