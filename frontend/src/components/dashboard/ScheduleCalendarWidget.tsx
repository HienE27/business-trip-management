"use client";

import { useState, useEffect, useRef } from "react";
import { DashboardCalendar } from "@/components/dashboard/DashboardCalendar";
import { ScheduleTableView } from "@/components/dashboard/ScheduleTableView";
import { FAB } from "@/components/ui/FAB";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { ConflictResolutionModal } from "@/components/ui/ConflictResolutionModal";
import { useRole, canEditSchedule } from "@/hooks/useRole";
import { api } from "@/lib/api";
import type { CompensationDay, Schedule } from "@/types/api";
import type { ConflictItem } from "@/types/schedule";

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
  periodId: number | null;
  staffList?: { id: number; fullName: string }[];
  defaultShiftTypeId?: string;
  compensationDays?: CompensationDay[];
};

export function QuickScheduleModal({ open, onClose, onSuccess, periodId, staffList = [], defaultShiftTypeId = "L01", compensationDays }: QuickScheduleModalProps) {
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [selectedShiftType, setSelectedShiftType] = useState(defaultShiftTypeId);
  const [selectedStaffId, setSelectedStaffId] = useState<string>("");
  const [workDate, setWorkDate] = useState<string>("");
  const [notes, setNotes] = useState<string>("");

  // Auto-clear message
  useEffect(() => {
    if (!message) return;
    const t = setTimeout(() => setMessage(null), 4000);
    return () => clearTimeout(t);
  }, [message]);

  // Reset form when modal opens
  useEffect(() => {
    if (open) {
      setDone(false);
      setSubmitting(false);
      setMessage(null);
    }
  }, [open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!periodId || !selectedStaffId || !workDate) return;
    if (compensationDays && selectedStaffId) {
      const isCompDay = compensationDays.some(
        (cd) => cd.staffId === Number(selectedStaffId) && cd.compensationDate.startsWith(workDate)
      );
      if (isCompDay) {
        setMessage("Ngày này là ngày nghỉ bù. Không thể xếp lịch.");
        return;
      }
    }
    setSubmitting(true);
    setMessage(null);
    try {
      await api.post("/schedules", {
        periodId,
        workDate,
        staffId: Number(selectedStaffId),
        shiftTypeId: selectedShiftType,
      });
      setDone(true);
      setTimeout(() => {
        setDone(false);
        setSelectedStaffId("");
        setWorkDate("");
        setNotes("");
        onSuccess?.();
        onClose();
      }, 1200);
    } catch {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Tạo nhanh ca trực" size="md">
      {done ? (
        <div className="flex flex-col items-center gap-4 py-8">
          <span aria-hidden="true" className="material-symbols-outlined text-[48px] text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
          <p className="text-title-lg text-on-surface font-semibold">Đã tạo thành công!</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          {message && (
            <div className="rounded-lg border border-error/20 bg-error-container px-3 py-2 text-label-sm text-error">
              {message}
            </div>
          )}
          <div>
            <label className="text-label-sm text-on-surface-variant block mb-2" htmlFor="q-shift-type">
              Loại lịch
            </label>
            <div className="relative">
              <select
                id="q-shift-type"
                name="shift-type"
                className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={selectedShiftType}
                onChange={(e) => setSelectedShiftType(e.target.value)}
                required
              >
                <option value="L01">Trực 24/24</option>
                <option value="L02">Thông tầm</option>
                <option value="L03">Phòng khám dịch vụ</option>
                <option value="L04">Phòng khám chuyên gia</option>
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">expand_more</span>
            </div>
          </div>

          <div>
            <label className="text-label-sm text-on-surface-variant block mb-2" htmlFor="q-staff">
              Nhân sự
            </label>
            <div className="relative">
              <select
                id="q-staff"
                name="staff"
                className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                value={selectedStaffId}
                onChange={(e) => setSelectedStaffId(e.target.value)}
                required
              >
                <option value="">Chọn nhân sự...</option>
                {staffList.map((s) => (
                  <option key={s.id} value={s.id}>{s.fullName}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">expand_more</span>
            </div>
          </div>

          <div>
            <label className="text-label-sm text-on-surface-variant block mb-2" htmlFor="q-date">
              Ngày
            </label>
            <input
              id="q-date"
              name="date"
              type="date"
              className="h-10 w-full cursor-pointer rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
              value={workDate}
              onChange={(e) => setWorkDate(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="text-label-sm text-on-surface-variant block mb-2" htmlFor="q-notes">
              Ghi chú
            </label>
            <textarea
              id="q-notes"
              className="w-full resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
              rows={2}
              placeholder="Ghi chú (nếu có)..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
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
  coverages?: Record<string, { required: number; assigned: number }>;
  staffList?: { id: number; fullName: string }[];
  staffFilter?: number | null;
  specialtyList?: { id: number; name: string }[];
  specialtyFilter?: number | null;
  initialYear?: number;
  initialMonth?: number;
  periodId?: number | null;
  viewMode?: "calendar" | "table";
  showViewToggle?: boolean;
  onRefresh?: () => void;
  onDayClick?: (date: Date, items: unknown[]) => void;
  onAddClick?: (date: Date) => void;
  onStaffFilterChange?: (staffId: number | null) => void;
  onSpecialtyFilterChange?: (specialtyId: number | null) => void;
  onViewDetail?: (schedule: Schedule) => void;
  onViewModeChange?: (view: "calendar" | "table") => void;
  compensationDays?: CompensationDay[];
  /** Khi true: chỉ xem (dashboard), ẩn mọi thao tác CRUD. Khi false/undefined: cho phép sửa/xóa (monthly-schedule). */
  isReadOnly?: boolean;
  /** Khi true: ẩn toolbar filter trên calendar (dashboard read-only). */
  hideFilters?: boolean;
};

export function ScheduleCalendarWidget({ schedules, calendarAnnotations = [], coverages = {}, staffList = [], staffFilter: externalStaffFilter, specialtyList = [], specialtyFilter: externalSpecialtyFilter, initialYear, initialMonth, periodId, viewMode: externalViewMode, showViewToggle = true, isReadOnly = false, hideFilters = false, onRefresh, onDayClick, onAddClick, onStaffFilterChange, onSpecialtyFilterChange, onViewDetail, onViewModeChange, compensationDays }: ScheduleCalendarWidgetProps) {
  const [internalView, setInternalView] = useState<"calendar" | "table">("calendar");
  const view = externalViewMode ?? internalView;
  const scrollYRef = useRef(0);
  const setView = (nextView: "calendar" | "table") => {
    if (view !== nextView) {
      scrollYRef.current = window.scrollY;
    }
    if (externalViewMode === undefined) setInternalView(nextView);
    onViewModeChange?.(nextView);
  };
  useEffect(() => {
    const saved = scrollYRef.current;
    if (saved > 0) {
      scrollYRef.current = 0;
      requestAnimationFrame(() => window.scrollTo(0, saved));
    }
  }, [view]);
  const [quickOpen, setQuickOpen] = useState(false);
  const [editSchedule, setEditSchedule] = useState<Schedule | null>(null);
  const [editing, setEditing] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<Schedule | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [conflictItem, setConflictItem] = useState<ConflictItem | null>(null);
  const role = useRole();
  const canEdit = canEditSchedule(role) && !isReadOnly;

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
    setDeleting(true);
    try {
      await api.delete(`/schedules/${deleteConfirm.id}`);
      setDeleteConfirm(null);
      onRefresh?.();
    } catch {
      setDeleteConfirm(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-end px-4 pt-3 pb-2">
        <div className="flex items-center gap-1 rounded-lg bg-surface-container-low p-1" aria-label="Chọn chế độ xem">
          <button
            type="button"
            onClick={() => setView("calendar")}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all ${
              view === "calendar"
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            <i className="material-symbols-outlined text-[16px] select-none leading-none" aria-hidden="true">calendar_month</i>
            Lịch biểu
          </button>
          <button
            type="button"
            onClick={() => setView("table")}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all ${
              view === "table"
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            <i className="material-symbols-outlined text-[16px] select-none leading-none" aria-hidden="true">table</i>
            Bảng dữ liệu
          </button>
        </div>
      </div>

      <div className="px-3 pb-3">
        {view === "calendar" ? (
          <DashboardCalendar
            schedules={schedules}
            annotations={calendarAnnotations}
            coverages={coverages}
            staffList={staffList}
            staffFilter={externalStaffFilter}
            specialtyList={specialtyList}
            specialtyFilter={externalSpecialtyFilter}
            initialYear={initialYear}
            initialMonth={initialMonth}
            onEditSchedule={canEdit ? (s) => setEditSchedule(s) : undefined}
            onDeleteSchedule={canEdit ? handleDelete : undefined}
            onResolveConflict={canEdit ? (s) => setConflictItem({
              id: String(s.id),
              staffName: s.staff.fullName,
              date: new Date(s.workDate).toLocaleDateString("vi-VN"),
              detail: `Xung đột lịch trực ${s.shiftType.name} ngày ${new Date(s.workDate).toLocaleDateString("vi-VN")}`,
              type: "SCHEDULE_CONFLICT",
              severity: "Chặn lưu",
              shiftType: s.shiftType.name,
              periodId: s.periodId,
              workDate: s.workDate,
              shiftTypeId: s.shiftType.id,
              originalStaffId: s.staff.id,
            }) : undefined}
            onDayClick={onDayClick}
            onAddClick={onAddClick}
            onStaffFilterChange={onStaffFilterChange}
            onSpecialtyFilterChange={onSpecialtyFilterChange}
            onViewDetail={onViewDetail}
            hideFilters={isReadOnly || hideFilters}
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
              type: "SCHEDULE_CONFLICT",
              severity: "Chặn lưu",
              shiftType: s.shiftType.name,
              periodId: s.periodId,
              workDate: s.workDate,
              shiftTypeId: s.shiftType.id,
              originalStaffId: s.staff.id,
            }) : undefined}
            onViewDetail={onViewDetail}
          />
        )}
      </div>

      {!isReadOnly && <FAB actions={fabActions} />}

      {/* Quick Create Modal */}
      {!isReadOnly && (
        <QuickScheduleModal
        open={quickOpen}
        onClose={() => setQuickOpen(false)}
        onSuccess={onRefresh}
        periodId={periodId ?? null}
        staffList={staffList}
        defaultShiftTypeId="L01"
        compensationDays={compensationDays}
      />
      )}

      {/* Edit Modal */}
      {!isReadOnly && (
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
                  disabled={editing}
                  onClick={async () => {
                    if (!editSchedule) return;
                    setEditing(true);
                    try {
                      await api.put(`/schedules/${editSchedule.id}`, {
                        periodId: editSchedule.periodId,
                        workDate: editSchedule.workDate,
                        staffId: editSchedule.staff.id,
                        shiftTypeId: editSchedule.shiftType.id,
                      });
                      setEditSchedule(null);
                      onRefresh?.();
                    } catch {
                      setEditing(false);
                      setEditSchedule(null);
                    } finally {
                      setEditing(false);
                    }
                  }}
                  className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md hover:bg-primary/90 transition-colors disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  {editing ? (
                    <><div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />Đang xử lý…</>
                  ) : (
                    <>Lưu thay đổi</>
                  )}
                </button>
              </ModalFooter>
            </div>
          )}
        </Modal>
      )}

      {/* Delete Confirm Modal */}
      {!isReadOnly && (
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
                disabled={deleting}
                onClick={confirmDelete}
                className="px-4 py-2 rounded-lg bg-error text-on-error text-label-md hover:bg-error/90 transition-colors disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                {deleting ? (
                  <><div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />Đang xóa…</>
                ) : (
                  <>Xóa</>
                )}
              </button>
            </ModalFooter>
          </div>
        </Modal>
      )}

      {/* Conflict Resolution Modal */}
      {!isReadOnly && (
        <ConflictResolutionModal
          open={!!conflictItem}
          onClose={() => setConflictItem(null)}
          conflict={conflictItem}
          onRefresh={onRefresh}
        />
      )}
    </div>
  );
}
