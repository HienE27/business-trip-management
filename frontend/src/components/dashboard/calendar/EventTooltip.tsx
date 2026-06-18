"use client";

import { useEffect, useRef } from "react";
import type { Schedule } from "@/types/api";
import { TONE, type CalendarItem } from "./constants";

export type TooltipData = {
  x: number;
  y: number;
  item: CalendarItem;
};

export type EventTooltipProps = {
  data: TooltipData;
  onEdit: (s: Schedule) => void;
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  onViewDetail: (s: Schedule) => void;
  canEdit: boolean;
};

export function EventTooltip({ data, onEdit, onDelete, onResolve, onViewDetail, canEdit }: EventTooltipProps) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const vp = window.innerWidth;
    if (rect.right > vp - 8) el.style.left = "auto";
    if (rect.bottom > window.innerHeight - 8) el.style.top = "auto";
  }, []);

  const s = data.item.schedule;
  const t = TONE[data.item.tone];

  return (
    <div
      ref={ref}
      className="fixed z-[100] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl p-4 w-64 max-w-[calc(100vw-32px)] pointer-events-auto"
      style={(() => {
        const isMobile = typeof window !== "undefined" && window.innerWidth < 768;
        if (isMobile) {
          const w = 256;
          const left = Math.max(16, (window.innerWidth - w) / 2);
          const top = Math.min(Math.max(16, data.y - 60), window.innerHeight - 320);
          return { left, top };
        }
        return { left: data.x + 8, top: data.y + 8 };
      })()}
      role="tooltip"
    >
      <div className="flex items-center gap-2 mb-3">
        <div className={`w-8 h-8 rounded-full ${t.bg} flex items-center justify-center`}>
          <span className={`material-symbols-outlined text-sm ${t.text}`}>schedule</span>
        </div>
        <div>
          <p className={`text-label-lg font-semibold ${t.text}`}>{s.shiftType.name}</p>
          <p className="text-label-sm text-on-surface-variant">{data.item.shiftLabel}</p>
        </div>
        {s.hasConflict && (
          <span className="ml-auto material-symbols-outlined text-error text-[18px]" title="Xung đột">warning</span>
        )}
      </div>

      <div className="space-y-1.5 mb-3 text-label-sm">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-[14px] text-on-surface-variant w-4">person</span>
          <span className="text-on-surface">{s.staff.fullName}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-[14px] text-on-surface-variant w-4">event</span>
          <span className="text-on-surface">
            {new Date(s.workDate).toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" })}
          </span>
        </div>
        {s.notes && (
          <div className="flex items-start gap-2">
            <span className="material-symbols-outlined text-[14px] text-on-surface-variant w-4 mt-0.5">notes</span>
            <span className="text-on-surface-variant">{s.notes}</span>
          </div>
        )}
      </div>

      <div className="flex gap-2 pt-2 border-t border-outline-variant">
        <button
          type="button"
          onClick={() => onViewDetail(s)}
          className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-surface-container-low text-on-surface hover:bg-surface-container-high transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <span className="flex items-center justify-center gap-1.5">
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">visibility</span>
            Xem chi tiết
          </span>
        </button>
        {canEdit && (
          <button
            type="button"
            onClick={() => onEdit(s)}
            className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-primary text-on-primary hover:bg-primary/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            Chỉnh sửa
          </button>
        )}
        {canEdit && s.hasConflict && (
          <button
            type="button"
            onClick={() => onResolve(s)}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium bg-error text-on-error hover:bg-error/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error"
          >
            Xử lý
          </button>
        )}
        {canEdit && (
          <button
            type="button"
            onClick={() => onDelete(s)}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium border border-outline-variant text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            Xóa
          </button>
        )}
      </div>
    </div>
  );
}
