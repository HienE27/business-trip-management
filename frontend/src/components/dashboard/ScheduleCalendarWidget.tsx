"use client";

import { useState } from "react";
import { DashboardCalendar } from "@/components/dashboard/DashboardCalendar";
import { ScheduleTableView } from "@/components/dashboard/ScheduleTableView";
import { FAB } from "@/components/ui/FAB";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { ConflictResolutionModal } from "@/components/ui/ConflictResolutionModal";
import { useRole, canEditSchedule } from "@/hooks/useRole";
import type { Schedule } from "@/types/api";
import type { ConflictItem } from "@/components/ui/ConflictResolutionModal";

type CalendarAnnotation = {
  date: string;
  label: string;
  tone?: "compLeave" | "warning" | "neutral";
  description?: string;
};

type QuickScheduleModalProps = {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
};

export function QuickScheduleModal({ open, onClose, onSuccess }: QuickScheduleModalProps) {
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSubmitting(true);
    await new Promise((r) => setTimeout(r, 800));
    setSubmitting(false);
    setDone(true);
    setTimeout(() => {
      setDone(false);
      onSuccess?.();
      onClose();
    }, 1200);
  };

  return (
    <Modal open={open} onClose={onClose} title="Tạo nhanh ca trực" size="md">
      {done ? (
        <div className="flex flex-col items-center gap-4 py-8">
          <span className="material-symbols-outlined text-[48px] text-secondary fill-icon">check_circle</span>
          <p className="text-title-lg text-on-surface font-semibold">Đã tạo thành công!</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="q-shift-type">
              Loại lịch
            </label>
            <div className="relative">
              <select
                id="q-shift-type"
                className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                required
              >
                <option value="">Chọn loại lịch...</option>
                <option value="L01">Trực 24/24</option>
                <option value="L02">Thông tầm</option>
                <option value="L03">Phòng khám dịch vụ</option>
                <option value="L04">Phòng khám chuyên gia</option>
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">expand_more</span>
            </div>
          </div>

          <div>
            <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="q-staff">
              Nhân sự
            </label>
            <input
              id="q-staff"
              className="h-10 w-full cursor-pointer rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
              placeholder="Nhập tên nhân sự..."
              required
            />
          </div>

          <div>
            <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="q-date">
              Ngay
            </label>
            <input
              id="q-date"
              type="date"
              className="h-10 w-full cursor-pointer rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
              required
            />
          </div>

          <div>
            <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="q-notes">
Ghi chú
              </label>
              <textarea
                id="q-notes"
                className="w-full resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                rows={2}
                placeholder="Ghi chú (nếu có)..."
            />
          </div>

          <ModalFooter>
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md hover:bg-primary/90 transition-colors disabled:opacity-60 flex items-center gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {submitting ? (
                <><div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />Đang xử lý…</>
              ) : (
                <><span className="material-symbols-outlined text-[18px]">add</span>Tạo lịch</>
              )}
            </button>
          </ModalFooter>
        </form>
      )}
    </Modal>
  );
}

type ScheduleCalendarWidgetProps = {
  schedules: Schedule[];
  calendarAnnotations?: CalendarAnnotation[];
  onRefresh?: () => void;
  onDayClick?: (date: Date, items: unknown[]) => void;
};

export function ScheduleCalendarWidget({ schedules, calendarAnnotations = [], onRefresh, onDayClick }: ScheduleCalendarWidgetProps) {
  const [view, setView] = useState<"calendar" | "table">("calendar");
  const [quickOpen, setQuickOpen] = useState(false);
  const [editSchedule, setEditSchedule] = useState<Schedule | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Schedule | null>(null);
  const [conflictItem, setConflictItem] = useState<ConflictItem | null>(null);
  const role = useRole();
  const canEdit = canEditSchedule(role);

  const fabActions = canEdit ? [
    {
      id: "create-shift",
      icon: "schedule",
      label: "Tạo nhanh ca trực",
      onClick: () => setQuickOpen(true),
    },
  ] : [];

  const handleDelete = async (schedule: Schedule) => {
    setDeleteConfirm(schedule);
  };

  const confirmDelete = async () => {
    if (!deleteConfirm) return;
    await new Promise((r) => setTimeout(r, 600));
    setDeleteConfirm(null);
    onRefresh?.();
  };

  return (
    <>
      {/* View toggle */}
      <div className="flex items-center gap-1 p-1 bg-surface-container-low rounded-lg w-fit shrink-0">
        <button
          type="button"
          onClick={() => setView("calendar")}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-label-sm font-medium transition-all ${
            view === "calendar"
              ? "bg-surface-container-lowest text-primary shadow-sm"
              : "text-on-surface-variant hover:text-on-surface"
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">calendar_month</span>
          Calendar
        </button>
        <button
          type="button"
          onClick={() => setView("table")}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-label-sm font-medium transition-all ${
            view === "table"
              ? "bg-surface-container-lowest text-primary shadow-sm"
              : "text-on-surface-variant hover:text-on-surface"
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">table</span>
          Bang
        </button>
      </div>

      {view === "calendar" ? (
        <DashboardCalendar
          schedules={schedules}
          annotations={calendarAnnotations}
          onEditSchedule={canEdit ? (s) => setEditSchedule(s) : undefined}
          onDeleteSchedule={canEdit ? handleDelete : undefined}
          onResolveConflict={canEdit ? (s) => setConflictItem({
            id: String(s.id),
            staffName: s.staff.fullName,
            date: new Date(s.workDate).toLocaleDateString("vi-VN"),
            detail: `Xung đột lịch trực ${s.shiftType.name} ngày ${new Date(s.workDate).toLocaleDateString("vi-VN")}`,
            shiftType: s.shiftType.name,
          }) : undefined}
          onDayClick={onDayClick}
        />
      ) : (
        <ScheduleTableView
          schedules={schedules}
          canEdit={canEdit}
          onEdit={canEdit ? (s) => setEditSchedule(s) : undefined}
          onDelete={canEdit ? handleDelete : undefined}
          onResolveConflict={canEdit ? (s) => setConflictItem({
            id: String(s.id),
            staffName: s.staff.fullName,
            date: new Date(s.workDate).toLocaleDateString("vi-VN"),
            detail: `Xung đột lịch trực ${s.shiftType.name} ngày ${new Date(s.workDate).toLocaleDateString("vi-VN")}`,
            shiftType: s.shiftType.name,
          }) : undefined}
        />
      )}

      <FAB actions={fabActions} />

      {/* Quick Create Modal */}
      <QuickScheduleModal
        open={quickOpen}
        onClose={() => setQuickOpen(false)}
        onSuccess={onRefresh}
      />

      {/* Edit Modal */}
      <Modal
        open={!!editSchedule}
        onClose={() => setEditSchedule(null)}
        title="Chỉnh sửa ca trực"
        description={editSchedule ? `${editSchedule.staff.fullName} — ${new Date(editSchedule.workDate).toLocaleDateString("vi-VN")}` : ""}
        size="md"
      >
        {editSchedule && (
          <div className="space-y-4">
            <div className="bg-surface-container-low rounded-lg p-4 space-y-2">
              <div className="flex justify-between">
                <span className="text-label-sm text-on-surface-variant">Loại lịch</span>
                <span className="text-label-md text-on-surface font-medium">{editSchedule.shiftType.name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-label-sm text-on-surface-variant">Nhân sự</span>
                <span className="text-label-md text-on-surface font-medium">{editSchedule.staff.fullName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-label-sm text-on-surface-variant">Ngay</span>
                <span className="text-label-md text-on-surface font-medium">{new Date(editSchedule.workDate).toLocaleDateString("vi-VN")}</span>
              </div>
            </div>
            <ModalFooter>
              <button
                type="button"
                onClick={() => setEditSchedule(null)}
                className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={() => {
                  setEditSchedule(null);
                  onRefresh?.();
                }}
                className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md hover:bg-primary/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Lưu thay đổi
              </button>
            </ModalFooter>
          </div>
        )}
      </Modal>

      {/* Delete Confirm Modal */}
      <Modal
        open={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        title="Xác nhận xóa ca trực"
        description={deleteConfirm ? `${deleteConfirm.staff.fullName} — ${new Date(deleteConfirm.workDate).toLocaleDateString("vi-VN")}` : ""}
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-label-md text-on-surface-variant leading-relaxed">
            Bạn có chắc muốn xóa ca trực này? Hành động này không thể hoàn tác.
          </p>
          <ModalFooter>
            <button
              type="button"
              onClick={() => setDeleteConfirm(null)}
              className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              Hủy
            </button>
            <button
              type="button"
              onClick={confirmDelete}
              className="px-4 py-2 rounded-lg bg-error text-on-error text-label-md hover:bg-error/90 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              Xóa
            </button>
          </ModalFooter>
        </div>
      </Modal>

      {/* Conflict Resolution Modal */}
      <ConflictResolutionModal
        open={!!conflictItem}
        onClose={() => setConflictItem(null)}
        conflict={conflictItem}
      />
    </>
  );
}
