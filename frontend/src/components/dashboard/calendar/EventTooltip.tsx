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
  onClose: () => void;
  canEdit: boolean;
};

export function EventTooltip({ data, onEdit, onDelete, onResolve, onViewDetail, onClose, canEdit }: EventTooltipProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const vp = window.innerWidth;
    const vh = window.innerHeight;
    const rect = el.getBoundingClientRect();
    const TIP_W = 256;
    const TIP_H = Math.min(rect.height, 320);

    // Clamp so tooltip stays within viewport
    let left = data.x + 8;
    let top = data.y + 8;
    if (rect.right > vp - 8) left = Math.max(16, vp - TIP_W - 16);
    if (rect.bottom > vh - 8) top = Math.max(16, vh - TIP_H - 16);
    el.style.left = `${left}px`;
    el.style.top = `${top}px`;
  }, [data.x, data.y]);

  // Escape to dismiss
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  const s = data.item.schedule;
  const t = TONE[data.item.tone];
  const isMobile = typeof window !== "undefined" && window.innerWidth < 768;

  return (
    <div
      ref={ref}
      className="fixed z-[100] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl p-4 w-64 max-w-[calc(100vw-32px)]"
      style={isMobile ? {
        left: Math.max(16, (window.innerWidth - 256) / 2),
        top: Math.min(Math.max(16, data.y - 60), window.innerHeight - 320),
      } : undefined}
      role="tooltip"
      aria-label={`Chi tiết ca trực ngày ${new Date(s.workDate).toLocaleDateString("vi-VN")}`}
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
          onClick={() => { onViewDetail(s); onClose(); }}
          className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-surface-container-low text-on-surface hover:bg-surface-container-high transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <span className="flex items-center justify-center gap-1.5">
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">visibility</span>
            Chi tiết
          </span>
        </button>
        {canEdit && (
          <button
            type="button"
            onClick={() => { onEdit(s); onClose(); }}
            className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-primary text-on-primary hover:bg-primary/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            Sửa
          </button>
        )}
        {canEdit && s.hasConflict && (
          <button
            type="button"
            onClick={() => { onResolve(s); onClose(); }}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium bg-error text-on-error hover:bg-error/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error"
          >
            Xử lý
          </button>
        )}
        {canEdit && (
          <button
            type="button"
            onClick={() => { onDelete(s); onClose(); }}
            className="px-3 py-1.5 rounded-lg text-label-sm font-medium border border-outline-variant text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            Xóa
          </button>
        )}
      </div>
    </div>
  );
}
