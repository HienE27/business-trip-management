"use client";

import { useEffect, useRef, useState } from "react";
import type { Schedule } from "@/types/api";
import { TONE, type CalendarItem } from "./constants";

export type OverflowPopoverProps = {
  items: CalendarItem[];
  anchor: { x: number; y: number };
  onEdit: (s: Schedule) => void;
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  onViewDetail: (s: Schedule) => void;
  canEdit: boolean;
  onClose: () => void;
};

/**
 * Popover hiển thị khi 1 ngày có nhiều hơn MAX_VISIBLE_GROUPS lịch.
 * Click ra ngoài → đóng.
 */
export function OverflowPopover({ items, anchor, onEdit, onDelete, onResolve, onViewDetail, canEdit, onClose }: OverflowPopoverProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [onClose]);

  return (
    <div
      ref={ref}
      className="fixed z-[90] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl w-72 max-h-80 overflow-y-auto"
      style={{ left: Math.min(anchor.x, window.innerWidth - 300), top: Math.min(anchor.y, window.innerHeight - 350) }}
      role="dialog"
      aria-label="Danh sách lịch trong ngày"
    >
      <div className="p-3 border-b border-outline-variant sticky top-0 bg-surface-container-lowest">
        <p className="text-label-md font-semibold text-on-surface">
          Chi tiết ngày ({items.length})
        </p>
      </div>
      <div className="p-2 space-y-1">
        {items.map((item, i) => {
          const t = TONE[item.tone];
          const s = item.schedule;
          return (
            <div key={`${item.schedule.id}-${i}`} className={`flex items-center gap-2.5 px-3 py-2 rounded-lg border-l-2 ${t.bg} ${t.border}`}>
              <div className={`w-6 h-6 rounded-full ${t.bg} flex items-center justify-center shrink-0`}>
                <span className={`text-label-sm font-bold ${t.text}`}>{item.staffCode}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className={`text-label-sm font-medium ${t.text} truncate`}>{item.staffName}</p>
                <p className="text-label-sm text-on-surface-variant">{item.shiftLabel}</p>
              </div>
              {s.hasConflict && (
                <span className="material-symbols-outlined text-error text-[14px] shrink-0" title="Xung đột">warning</span>
              )}
              <div className="flex gap-1 shrink-0">
                <button type="button" onClick={() => { onViewDetail(item.schedule); onClose(); }} className="p-1 rounded hover:bg-surface-container-high transition-colors" aria-label="Xem chi tiết">
                  <span className="material-symbols-outlined text-[14px] text-on-surface-variant">visibility</span>
                </button>
                {canEdit && (
                  <>
                    <button type="button" onClick={() => { onEdit(item.schedule); onClose(); }} className="p-1 rounded hover:bg-surface-container-high transition-colors" aria-label="Chỉnh sửa">
                      <span className="material-symbols-outlined text-[14px] text-on-surface-variant">edit</span>
                    </button>
                    {s.hasConflict && (
                      <button type="button" onClick={() => { onResolve(item.schedule); onClose(); }} className="p-1 rounded hover:bg-error-container/30 transition-colors" aria-label="Xử lý xung đột">
                        <span className="material-symbols-outlined text-[14px] text-error">warning</span>
                      </button>
                    )}
                    <button type="button" onClick={() => { onDelete(item.schedule); onClose(); }} className="p-1 rounded hover:bg-error-container/30 transition-colors" aria-label="Xóa">
                      <span className="material-symbols-outlined text-[14px] text-error">delete</span>
                    </button>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
