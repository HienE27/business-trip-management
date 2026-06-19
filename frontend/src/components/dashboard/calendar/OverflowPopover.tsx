"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { Schedule, Staff, ShiftType } from "@/types/api";
import { TONE, type CalendarItem } from "./constants";

export type OverflowPopoverProps = {
  items: CalendarItem[];
  anchor: { x: number; y: number };
  onDelete: (s: Schedule) => void;
  onResolve: (s: Schedule) => void;
  onViewDetail: (s: Schedule) => void;
  onRefresh: () => void;
  canEdit: boolean;
  onClose: () => void;
};

/**
 * Popover hiển thị khi 1 ngày có nhiều hơn MAX_VISIBLE_GROUPS lịch.
 * Hỗ trợ inline edit trực tiếp trên popover: đổi nhân sự hoặc loại lịch mà không cần mở modal riêng.
 * Click ra ngoài → đóng.
 */
export function OverflowPopover({
  items,
  anchor,
  onDelete,
  onResolve,
  onViewDetail,
  onRefresh,
  canEdit,
  onClose,
}: OverflowPopoverProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editStaffId, setEditStaffId] = useState<number | null>(null);
  const [editShiftTypeId, setEditShiftTypeId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [loadingDropdown, setLoadingDropdown] = useState(false);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("mousedown", handleClick);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handleClick);
      document.removeEventListener("keydown", handleKey);
    };
  }, [onClose]);

  const startEdit = async (item: CalendarItem) => {
    setEditingId(item.schedule.id);
    setEditStaffId(item.schedule.staff?.id ?? null);
    setEditShiftTypeId(item.schedule.shiftType?.id ?? null);
    if (activeStaff.length === 0 || shiftTypes.length === 0) {
      setLoadingDropdown(true);
      try {
        const [staffRes, shiftRes] = await Promise.all([
          api.get<Staff[]>("/staff/active"),
          api.get<ShiftType[]>("/shift-types"),
        ]);
        setActiveStaff(staffRes ?? []);
        setShiftTypes(shiftRes ?? []);
      } catch { /* ignore */ }
      setLoadingDropdown(false);
    }
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditStaffId(null);
    setEditShiftTypeId(null);
  };

  const saveEdit = async (item: CalendarItem) => {
    if (editStaffId === null || editShiftTypeId === null || saving) return;
    setSaving(true);
    try {
      await api.updateSchedule(item.schedule.id, {
        staffId: editStaffId,
        shiftTypeId: editShiftTypeId,
        periodId: item.schedule.periodId,
        workDate: item.schedule.workDate,
      });
      setEditingId(null);
      setEditStaffId(null);
      setEditShiftTypeId(null);
      onRefresh();
    } catch { /* ignore */ }
    setSaving(false);
  };

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
          const isEditing = editingId === s.id;
          return (
            <div key={`${item.schedule.id}-${i}`} className={`flex items-center gap-2 px-3 py-2 rounded-lg border-l-2 ${t.bg} ${t.border}`}>
              {isEditing ? (
                <>
                  <select
                    value={editStaffId ?? ""}
                    onChange={(e) => setEditStaffId(Number(e.target.value))}
                    disabled={saving || loadingDropdown}
                    className="flex-1 h-7 px-2 text-[12px] rounded border border-outline bg-surface-container-lowest text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer"
                  >
                    <option value="">-- Nhân sự --</option>
                    {activeStaff.map((st) => (
                      <option key={st.id} value={st.id}>{st.fullName}</option>
                    ))}
                  </select>
                  <select
                    value={editShiftTypeId ?? ""}
                    onChange={(e) => setEditShiftTypeId(e.target.value)}
                    disabled={saving || loadingDropdown}
                    className="flex-1 h-7 px-2 text-[12px] rounded border border-outline bg-surface-container-lowest text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer"
                  >
                    <option value="">-- Loại lịch --</option>
                    {shiftTypes.map((st) => (
                      <option key={st.id} value={st.id}>{st.name}</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => void saveEdit(item)}
                    disabled={saving || editStaffId === null || editShiftTypeId === null}
                    className="p-1 rounded hover:bg-secondary-container/30 transition-colors disabled:opacity-40"
                    aria-label="Lưu"
                    title="Lưu"
                  >
                    <span className="material-symbols-outlined text-[14px] text-secondary">check</span>
                  </button>
                  <button
                    type="button"
                    onClick={cancelEdit}
                    disabled={saving}
                    className="p-1 rounded hover:bg-surface-container-high transition-colors"
                    aria-label="Hủy"
                    title="Hủy"
                  >
                    <span className="material-symbols-outlined text-[14px] text-on-surface-variant">close</span>
                  </button>
                </>
              ) : (
                <>
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
                        <button type="button" onClick={() => void startEdit(item)} className="p-1 rounded hover:bg-surface-container-high transition-colors" aria-label="Chỉnh sửa nhanh" title="Chỉnh sửa nhanh">
                          <span className="material-symbols-outlined text-[14px] text-on-surface-variant">edit</span>
                        </button>
                        {s.hasConflict && (
                          <button type="button" onClick={() => { onResolve(item.schedule); onClose(); }} className="p-1 rounded hover:bg-error-container/30 transition-colors" aria-label="Xử lý xung đột" title="Xử lý xung đột">
                            <span className="material-symbols-outlined text-[14px] text-error">warning</span>
                          </button>
                        )}
                        <button type="button" onClick={() => { onDelete(item.schedule); onClose(); }} className="p-1 rounded hover:bg-error-container/30 transition-colors" aria-label="Xóa" title="Xóa">
                          <span className="material-symbols-outlined text-[14px] text-error">delete</span>
                        </button>
                      </>
                    )}
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
