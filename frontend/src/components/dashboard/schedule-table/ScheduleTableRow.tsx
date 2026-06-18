"use client";

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
};

export function ScheduleTableRow({
  schedule: s,
  onEdit,
  onDelete,
  onResolveConflict,
  onViewDetail,
  canEdit = false,
}: ScheduleTableRowProps) {
  const tone = TONE[s.shiftType.id] ?? FALLBACK_TONE;
  const dateObj = new Date(s.workDate + "T00:00:00");
  const dow = WEEKDAY_VN[dateObj.getDay()];
  const dateShort = dateObj.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
  const dateLong = dateObj.toLocaleDateString("vi-VN");

  return (
    <tr className="group hover:bg-primary-fixed/10 transition-colors focus-within:bg-primary-fixed/10">
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
            <span className={`text-label-sm font-bold leading-none ${tone.text}`}>{getInitials(s.staff.fullName)}</span>
          </div>
          <span className="text-label-sm text-on-surface font-semibold truncate" title={s.staff.fullName}>
            {s.staff.fullName}
          </span>
        </div>
      </td>

      <td className="px-3 py-3.5">
        {s.hasConflict ? (
          <button
            type="button"
            onClick={() => onResolveConflict?.(s)}
            className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-error-container border border-error/20 text-error text-label-sm font-bold hover:bg-error transition-colors hover:text-on-error focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error leading-none"
            title="Có xung đột - Click để xử lý"
            aria-label={`Có xung đột - nhấn để xử lý cho ca trực ngày ${dateLong}`}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[14px]">warning</span>
            Xung đột
          </button>
        ) : (
          <div className="inline-flex items-center gap-1 text-label-sm text-secondary font-bold leading-none">
            <span aria-hidden="true" className="material-symbols-outlined text-[14px]">check_circle</span>
            OK
          </div>
        )}
      </td>

      <td className="px-3 py-3.5 max-w-[240px]">
        <span className="text-label-sm text-on-surface font-medium truncate block" title={s.notes ?? ""}>
          {s.notes ?? <span className="text-on-surface-variant font-normal">—</span>}
        </span>
      </td>

      <td className="px-3 py-3.5">
        <div className="flex items-center gap-0.5 opacity-60 group-hover:opacity-100 transition-opacity">
          <button
            type="button"
            onClick={() => onViewDetail?.(s)}
            className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant hover:text-primary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            title="Xem chi tiết"
            aria-label={`Xem chi tiết ca trực ngày ${dateLong}`}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">visibility</span>
          </button>
          {canEdit && (
            <>
              <button
                type="button"
                onClick={() => onEdit?.(s)}
                className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant hover:text-primary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                title="Chỉnh sửa"
                aria-label={`Chỉnh sửa ca trực ngày ${dateLong}`}
              >
                <span aria-hidden="true" className="material-symbols-outlined text-[16px]">edit</span>
              </button>
              <button
                type="button"
                onClick={() => onDelete?.(s)}
                className="p-1.5 rounded-lg hover:bg-error-container text-on-surface-variant hover:text-error transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error"
                title="Xóa"
                aria-label={`Xóa ca trực ngày ${dateLong}`}
              >
                <span aria-hidden="true" className="material-symbols-outlined text-[16px]">delete</span>
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  );
}
