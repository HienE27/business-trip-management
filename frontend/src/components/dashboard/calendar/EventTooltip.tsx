"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { Schedule, Staff, ShiftType } from "@/types/api";
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
  onRefresh?: () => void;
  onClose: () => void;
  canEdit: boolean;
};

export function EventTooltip({ data, onEdit, onDelete, onResolve, onViewDetail, onRefresh, onClose, canEdit }: EventTooltipProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [editMode, setEditMode] = useState(false);
  const [editStaffId, setEditStaffId] = useState<number | null>(null);
  const [editShiftTypeId, setEditShiftTypeId] = useState<string | null>(null);
  const [editNotes, setEditNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [staffList, setStaffList] = useState<Staff[]>([]);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [loadingDropdowns, setLoadingDropdowns] = useState(false);

  // Load dropdown data when entering edit mode
  useEffect(() => {
    if (!editMode) return;
    setEditStaffId(data.item.schedule.staff?.id ?? null);
    setEditShiftTypeId(data.item.schedule.shiftType?.id ?? null);
    setEditNotes(data.item.schedule.notes ?? "");
    if (staffList.length === 0 || shiftTypes.length === 0) {
      setLoadingDropdowns(true);
      Promise.all([
        api.get<Staff[]>("/staff/active"),
        api.get<ShiftType[]>("/shift-types/active"),
      ]).then(([staffRes, shiftRes]) => {
        setStaffList(staffRes ?? []);
        setShiftTypes(shiftRes ?? []);
      }).catch(() => { /* silent */ })
        .finally(() => setLoadingDropdowns(false));
    }
  }, [editMode]);

  const startEdit = useCallback(() => {
    setEditMode(true);
    setEditError(null);
  }, []);

  const cancelEdit = useCallback(() => {
    setEditMode(false);
    setEditError(null);
    setEditNotes("");
  }, []);

  const handleSave = useCallback(async () => {
    if (editStaffId === null || editShiftTypeId === null || saving) return;
    setSaving(true);
    setEditError(null);
    try {
      await api.updateSchedule(data.item.schedule.id, {
        periodId: data.item.schedule.periodId,
        workDate: data.item.schedule.workDate,
        staffId: editStaffId,
        shiftTypeId: editShiftTypeId,
        notes: editNotes || undefined,
      });
      setEditMode(false);
      onRefresh?.();
      onClose();
    } catch {
      setEditError("Không thể lưu. Vui lòng thử lại.");
    } finally {
      setSaving(false);
    }
  }, [editStaffId, editShiftTypeId, saving, data.item.schedule, onRefresh, onClose]);

  const isDirty = data.item.schedule
    && (editStaffId !== data.item.schedule.staff?.id
      || editShiftTypeId !== data.item.schedule.shiftType?.id
      || editNotes !== (data.item.schedule.notes ?? ""));

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const vp = window.innerWidth;
    const vh = window.innerHeight;
    const TIP_W = 280;
    const TIP_H = 300; // Estimated height to avoid layout shift

    // Calculate position based on click point, adjusted for viewport boundaries
    let left = data.x + 12;
    let top = data.y + 12;

    // Flip to left side if too close to right edge
    if (left + TIP_W > vp - 16) {
      left = data.x - TIP_W - 12;
    }
    // Flip above if too close to bottom
    if (top + TIP_H > vh - 16) {
      top = data.y - TIP_H - 12;
    }
    // Ensure minimum boundaries
    left = Math.max(16, left);
    top = Math.max(16, top);

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

  const tooltipWidth = editMode ? 300 : 256;

  return (
    <div
      ref={ref}
      className="fixed z-[100] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl p-4 w-64 max-w-[calc(100vw-32px)]"
      style={isMobile ? {
        left: Math.max(16, (window.innerWidth - tooltipWidth) / 2),
        top: Math.min(Math.max(16, data.y - 60), window.innerHeight - 380),
      } : undefined}
      role="dialog"
      aria-label={`Chi tiết ca trực ngày ${new Date(s.workDate).toLocaleDateString("vi-VN")}`}
    >
      {/* Header */}
      <div className="flex items-center gap-2 mb-3">
        <div className={`w-8 h-8 rounded-full ${t.bg} flex items-center justify-center`}>
          <span className={`material-symbols-outlined text-sm ${t.text}`}>schedule</span>
        </div>
        <div>
          <p className={`text-label-lg font-semibold ${t.text}`}>{s.shiftType.name}</p>
          <p className="text-label-sm text-on-surface-variant">{data.item.shiftLabel}</p>
        </div>
        {s.hasConflict && !editMode && (
          <span className="ml-auto material-symbols-outlined text-red-800 text-[18px]" title="Xung đột">warning</span>
        )}
        {editMode && (
          <button
            type="button"
            onClick={cancelEdit}
            className="ml-auto p-1 rounded hover:bg-surface-container-low transition-colors"
            aria-label="Hủy chỉnh sửa"
            title="Hủy"
          >
            <span className="material-symbols-outlined text-[16px] text-on-surface-variant">close</span>
          </button>
        )}
      </div>

      {/* Error */}
      {editError && (
        <div className="mb-3 rounded-lg border border-red-300 bg-red-100 text-red-800 px-3 py-2 text-label-sm text-red-800">
          {editError}
        </div>
      )}

      {/* Edit form */}
      {editMode ? (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant font-medium">Nhân sự</label>
            <div className="relative">
              <select
                value={editStaffId ?? ""}
                onChange={(e) => setEditStaffId(e.target.value ? Number(e.target.value) : null)}
                disabled={saving || loadingDropdowns}
                className="w-full h-9 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface-container-lowest text-body-sm text-on-surface appearance-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 cursor-pointer"
              >
                <option value="">-- Chọn nhân sự --</option>
                {staffList.map((st) => (
                  <option key={st.id} value={st.id}>{st.fullName}</option>
                ))}
              </select>
              <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[16px] pointer-events-none">expand_more</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant font-medium">Loại lịch</label>
            <div className="relative">
              <select
                value={editShiftTypeId ?? ""}
                onChange={(e) => setEditShiftTypeId(e.target.value || null)}
                disabled={saving || loadingDropdowns}
                className="w-full h-9 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface-container-lowest text-body-sm text-on-surface appearance-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 cursor-pointer"
              >
                <option value="">-- Chọn loại lịch --</option>
                {shiftTypes.map((st) => (
                  <option key={st.id} value={st.id}>{st.name}</option>
                ))}
              </select>
              <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[16px] pointer-events-none">expand_more</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-label-sm text-on-surface-variant font-medium" htmlFor={`tooltip-notes-${data.item.schedule.id}`}>
              Ghi chú
            </label>
            <textarea
              id={`tooltip-notes-${data.item.schedule.id}`}
              value={editNotes}
              onChange={(e) => setEditNotes(e.target.value)}
              disabled={saving}
              rows={2}
              className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-sm text-on-surface transition-all focus:ring-2 focus:ring-blue-300 focus:border-blue-300"
              placeholder="Ghi chú (tùy chọn)"
            />
          </div>

          <div className="flex gap-2 pt-1">
            <button
              type="button"
              onClick={cancelEdit}
              disabled={saving}
              className="flex-1 h-9 rounded-lg border border-outline-variant text-label-sm text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-50"
            >
              Hủy
            </button>
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={saving || !isDirty || editStaffId === null || editShiftTypeId === null}
              className="flex-1 h-9 rounded-lg bg-blue-100 text-blue-800 text-label-sm font-semibold hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center justify-center gap-1.5"
            >
              {saving ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  Đang lưu...
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[14px]">save</span>
                  Lưu
                </>
              )}
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* View mode */}
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
              onClick={() => {
                onViewDetail(s);
                // Delay close so modal can open before tooltip disappears
                requestAnimationFrame(() => onClose());
              }}
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
                onClick={startEdit}
                className="flex-1 px-3 py-1.5 rounded-lg text-label-sm font-medium bg-blue-100 text-blue-800 hover:bg-blue-100/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <span className="flex items-center justify-center gap-1.5">
                  <span aria-hidden="true" className="material-symbols-outlined text-[16px]">edit</span>
                  Sửa
                </span>
              </button>
            )}
            {canEdit && s.hasConflict && (
              <button
                type="button"
                onClick={() => {
                  onResolve(s);
                  requestAnimationFrame(() => onClose());
                }}
                className="px-3 py-1.5 rounded-lg text-label-sm font-medium bg-error text-white hover:bg-error/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error"
              >
                Xử lý
              </button>
            )}
            {canEdit && (
              <button
                type="button"
                onClick={() => {
                  onDelete(s);
                  requestAnimationFrame(() => onClose());
                }}
                className="px-3 py-1.5 rounded-lg text-label-sm font-medium border border-outline-variant text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Xóa
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}
