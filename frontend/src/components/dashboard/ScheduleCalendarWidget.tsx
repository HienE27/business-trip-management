"use client";

import { useState, useEffect, useRef, memo } from "react";
import { useRouter } from "next/navigation";
import { DashboardCalendar } from "@/components/dashboard/DashboardCalendar";
import { ScheduleTableView } from "@/components/dashboard/ScheduleTableView";
import { ScheduleMatrixGrid } from "@/components/dashboard/ScheduleMatrixGrid";
import { MatrixGridWrapper } from "@/components/dashboard/MatrixGridWrapper";
import { FAB } from "@/components/ui/FAB";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button, FormSelect, FormInput, FormTextarea } from "@/components/ui";
import { ConflictResolutionModal } from "@/components/ui/ConflictResolutionModal";
import { useRole, canEditSchedule } from "@/hooks/useRole";
import { useToast } from "@/components/ui/ToastProvider";
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
  const toast = useToast();

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
        ...(notes.trim() ? { notes: notes.trim() } : {}),
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
      toast.error("Không thể tạo ca trực. Vui lòng thử lại.");
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
          <FormSelect
            label="Loại lịch"
            id="q-shift-type"
            value={selectedShiftType}
            onChange={(e) => setSelectedShiftType(e.target.value)}
            options={[
              { value: "L01", label: "Trực 24/24" },
              { value: "L02", label: "Thông tầm" },
              { value: "L03", label: "Phòng khám dịch vụ" },
              { value: "L04", label: "Phòng khám chuyên gia" },
            ]}
            required
          />

          <FormSelect
            label="Nhân sự"
            id="q-staff"
            placeholder="Chọn nhân sự..."
            value={selectedStaffId}
            onChange={(e) => setSelectedStaffId(e.target.value)}
            options={staffList.map((s) => ({ value: String(s.id), label: s.fullName }))}
            required
          />

          <FormInput
            label="Ngày"
            id="q-date"
            type="date"
            value={workDate}
            onChange={(e) => setWorkDate(e.target.value)}
            required
          />

          <FormTextarea
            label="Ghi chú"
            id="q-notes"
            rows={2}
            placeholder="Ghi chú (nếu có)..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />

          <ModalFooter>
            <Button type="button" variant="secondary" onClick={onClose}>
              Hủy
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              disabled={!selectedStaffId || !workDate}
              icon={<span className="material-symbols-outlined" aria-hidden="true">add</span>}
            >
              Tạo lịch
            </Button>
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
  viewMode?: "calendar" | "table" | "matrix";
  showViewToggle?: boolean;
  onRefresh?: () => void;
  onDayClick?: (date: Date, items?: unknown[]) => void;
  onAddClick?: (date: Date, staffId?: number) => void;
  onStaffFilterChange?: (staffId: number | null) => void;
  onSpecialtyFilterChange?: (specialtyId: number | null) => void;
  onViewDetail?: (schedule: Schedule) => void;
  onViewModeChange?: (view: "calendar" | "table" | "matrix") => void;
  selectedTab?: string;
  onFilterTypeChange?: (filter: string) => void;
  compensationDays?: CompensationDay[];
  /** Khi true: chỉ xem (dashboard), ẩn mọi thao tác CRUD. Khi false/undefined: cho phép sửa/xóa (monthly-schedule). */
  isReadOnly?: boolean;
  /** Khi true: ẩn toolbar filter trên calendar (dashboard read-only). */
  hideFilters?: boolean;
  /** Bật nút Sửa trong tooltip (bypass isReadOnly cho inline edit trên dashboard). */
  canEditOverride?: boolean;
};

export const ScheduleCalendarWidget = memo(function ScheduleCalendarWidget({ schedules, calendarAnnotations = [], coverages = {}, staffList = [], staffFilter: externalStaffFilter, specialtyList = [], specialtyFilter: externalSpecialtyFilter, initialYear, initialMonth, periodId, viewMode: externalViewMode, showViewToggle = true, isReadOnly = false, hideFilters = false, canEditOverride = false, onRefresh, onDayClick, onAddClick, onStaffFilterChange, onSpecialtyFilterChange, onViewDetail, onViewModeChange, selectedTab, onFilterTypeChange, compensationDays }: ScheduleCalendarWidgetProps) {
  const [internalView, setInternalView] = useState<"calendar" | "table" | "matrix">("calendar");
  const [matrixViewMode, setMatrixViewMode] = useState<"month" | "week">("month");
  const view = externalViewMode ?? internalView;
  const scrollYRef = useRef(0);
  const setView = (nextView: "calendar" | "table" | "matrix") => {
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
  const canEdit = canEditSchedule(role) && (!isReadOnly || canEditOverride);
  const router = useRouter();
  const toast = useToast();

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
      toast.error("Không thể xóa ca trực. Vui lòng thử lại.");
      setDeleteConfirm(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex flex-col pt-1">
      <div className="flex items-center justify-end px-4 pt-3 pb-2">
        <div
          role="group"
          aria-label="Chọn chế độ xem"
          className="flex items-center gap-1 rounded-lg bg-surface-container-low p-1"
        >
          <button
            type="button"
            onClick={() => setView("calendar")}
            aria-pressed={view === "calendar"}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
              view === "calendar"
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">calendar_month</span>
            Lịch biểu
          </button>
          <button
            type="button"
            onClick={() => setView("table")}
            aria-pressed={view === "table"}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
              view === "table"
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">table</span>
            Bảng dữ liệu
          </button>
          <button
            type="button"
            onClick={() => setView("matrix")}
            aria-pressed={view === "matrix"}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
              view === "matrix"
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">grid_view</span>
            Ma trận
          </button>
        </div>

        {/* Matrix view: Tháng / Tuần toggle */}
        {view === "matrix" && (
          <div
            role="group"
            aria-label="Chế độ xem ma trận"
            className="flex items-center gap-1 rounded-lg bg-surface-container-low p-1"
          >
            <button
              type="button"
              onClick={() => setMatrixViewMode("month")}
              aria-pressed={matrixViewMode === "month"}
              className={`rounded-md px-3 py-1 text-label-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                matrixViewMode === "month"
                  ? "bg-surface-container-lowest text-primary shadow-sm"
                  : "text-on-surface-variant hover:text-on-surface"
              }`}
            >
              Tháng
            </button>
            <button
              type="button"
              onClick={() => setMatrixViewMode("week")}
              aria-pressed={matrixViewMode === "week"}
              className={`rounded-md px-3 py-1 text-label-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                matrixViewMode === "week"
                  ? "bg-surface-container-lowest text-primary shadow-sm"
                  : "text-on-surface-variant hover:text-on-surface"
              }`}
            >
              Tuần
            </button>
          </div>
        )}
      </div>

      <div className="px-3 pb-3">
        <div key={view} className="animate-fade-in">
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
            onRefresh={onRefresh}
            selectedTab={selectedTab}
            onFilterTypeChange={onFilterTypeChange}
            hideFilters={isReadOnly || hideFilters}
          />
        ) : view === "matrix" ? (
          <div className="px-1">
            <MatrixGridWrapper
              schedules={schedules}
              staffList={staffList ?? []}
              year={initialYear ?? new Date().getFullYear()}
              month={initialMonth ?? new Date().getMonth()}
              viewMode={matrixViewMode}
              compensationDays={compensationDays}
              shiftTypeFilter={selectedTab}
              onViewDetail={canEdit ? (s) => setEditSchedule(s) : undefined}
              onCellClick={(date, staffId) => { onAddClick?.(date, staffId); }}
              onRefresh={onRefresh}
              canEdit={canEdit}
            />
          </div>
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

      {/* Edit Modal — redirects to detail page for full editing */}
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
                  <span className="text-label-sm text-on-surface-variant">Ngày</span>
                  <span className="text-label-md text-on-surface font-medium">{new Date(editSchedule.workDate).toLocaleDateString("vi-VN")}</span>
                </div>
              </div>
              <div className="bg-primary-fixed/30 rounded-lg border border-primary/20 px-4 py-3 text-label-sm text-on-primary-fixed-variant">
                <span className="material-symbols-outlined text-[16px] align-text-bottom mr-1">info</span>
                Chỉnh sửa chi tiết tại trang chuyên biệt — nơi có đầy đủ form và ràng buộc.
              </div>
            <ModalFooter>
              <Button type="button" variant="secondary" onClick={() => setEditSchedule(null)}>
                Hủy
              </Button>
              <Button
                type="button"
                variant="primary"
                loading={editing}
                onClick={async () => {
                  if (!editSchedule) return;
                  setEditing(true);
                  try {
                    // Delete the schedule (same as QuickScheduleModal flow — clear slot, re-create)
                    await api.delete(`/schedules/${editSchedule.id}`);
                    toast.success("Đã xóa ca trực. Tạo lại với thông tin mới tại form bên dưới.");
                    setEditSchedule(null);
                    onRefresh?.();
                    // Open quick add pre-filled
                    setQuickOpen(true);
                  } catch {
                    toast.error("Không thể xóa ca trực. Vui lòng thử lại.");
                    setEditing(false);
                  }
                }}
              >
                Xóa & Tạo mới
              </Button>
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
              <Button type="button" variant="secondary" onClick={() => setDeleteConfirm(null)}>
                Hủy
              </Button>
              <Button
                type="button"
                variant="danger"
                loading={deleting}
                onClick={confirmDelete}
              >
                Xóa
              </Button>
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
});
