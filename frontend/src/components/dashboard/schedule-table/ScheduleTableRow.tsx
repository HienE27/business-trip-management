"use client";

import { forwardRef, memo } from "react";
import type { Schedule } from "@/types/api";
import { FALLBACK_TONE, TONE, WEEKDAY_VN } from "./constants";
import { getInitials, isSameDay } from "./filterSort";

export type ScheduleTableRowProps = {
  schedule: Schedule;
  onEdit?: (s: Schedule) => void;
  onDelete?: (s: Schedule) => void;
  onResolveConflict?: (s: Schedule) => void;
  onViewDetail?: (s: Schedule) => void;
  canEdit?: boolean;
  style?: React.CSSProperties;
};

export const ScheduleTableRow = memo(
  forwardRef<HTMLTableRowElement, ScheduleTableRowProps>(function ScheduleTableRow(
    { schedule: s, onEdit, onDelete, onResolveConflict, onViewDetail, canEdit = false, style },
    ref
  ) {
    const tone = TONE[s.shiftType.id] ?? FALLBACK_TONE;
    const dateObj = new Date(s.workDate + "T00:00:00");
    const dow = WEEKDAY_VN[dateObj.getDay()];
    const dateShort = dateObj.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });

    return (
      <tr ref={ref} style={style} className="group hover:bg-primary-fixed/10 transition-colors focus-within:bg-primary-fixed/10">
        <td className="px-3 py-3.5 whitespace-nowrap">
          <div className="flex flex-col leading-snug gap-0.5">
            <span className="text-label-sm font-semibold text-on-surface tabular-nums">{dateShort}</span>
            {isSameDay(dateObj) ? (
              <span className="text-label-sm font-bold text-primary">Hôm nay</span>
            ) : (
              <span className="text-label-sm text-on-surface-variant">{dateObj.getFullYear()}</span>
            )}
          </div>
        </td>

        <td className="px-3 py-3.5 whitespace-nowrap">
          <span className={`text-label-sm font-bold tabular-nums ${dow === "CN" || dow === "T7" ? "text-error" : "text-on-surface"}`}>
            {dow}
          </span>
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center gap-1.5">
            <span aria-hidden="true" className={`w-2 h-2 rounded-full shrink-0 ${tone.dot}`} />
            <span className={`px-2 py-1 rounded-md text-label-sm font-bold whitespace-nowrap leading-none ${tone.bg} ${tone.text}`}>
              {tone.label}
            </span>
          </div>
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center gap-2 min-w-0">
            <div aria-hidden="true" className={`w-8 h-8 rounded-full shrink-0 flex items-center justify-center ${tone.bg}`}>
              <span className={`text-[11px] font-bold ${tone.text}`}>{getInitials(s.staff.fullName)}</span>
            </div>
            <div className="min-w-0">
              <p className="text-label-sm font-semibold text-on-surface truncate leading-snug">{s.staff.fullName}</p>
              {s.staff.specialtyName && (
                <p className="text-label-sm text-on-surface-variant truncate leading-snug">{s.staff.specialtyName}</p>
              )}
            </div>
          </div>
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-label-sm text-on-surface-variant">—</span>
          </div>
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center gap-1.5">
            <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold border ${s.hasConflict ? "bg-error-container border-error/20 text-on-error-container" : "bg-secondary-container border-secondary/20 text-on-secondary-container"}`}>
              {s.hasConflict ? (
                <><span className="w-1.5 h-1.5 rounded-full bg-error" aria-hidden="true" />Xung đột</>
              ) : (
                <><span className="w-1.5 h-1.5 rounded-full bg-secondary" aria-hidden="true" />Bình thường</>
              )}
            </span>
          </div>
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 transition-opacity">
            {canEdit && (
              <>
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); onResolveConflict?.(s); }}
                  disabled={!s.hasConflict}
                  className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-tertiary hover:bg-tertiary-container transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                  title="Giải quyết xung đột"
                >
                  <span className="material-symbols-outlined text-[18px]">flash_on</span>
                </button>
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); onEdit?.(s); }}
                  className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-secondary hover:bg-secondary-container transition-colors"
                  title="Chỉnh sửa"
                >
                  <span className="material-symbols-outlined text-[18px]">edit</span>
                </button>
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); onDelete?.(s); }}
                  className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-error hover:bg-error-container transition-colors"
                  title="Xóa"
                >
                  <span className="material-symbols-outlined text-[18px]">delete</span>
                </button>
              </>
            )}
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onViewDetail?.(s); }}
              className="inline-flex items-center justify-center w-8 h-8 rounded-lg text-primary hover:bg-primary-container transition-colors"
              title="Xem chi tiết"
            >
              <span className="material-symbols-outlined text-[18px]">visibility</span>
            </button>
          </div>
        </td>
      </tr>
    );
  })
);
