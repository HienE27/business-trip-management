"use client";

import { useState, useEffect, memo, useCallback } from "react";
import { MatrixGridWrapper } from "@/components/dashboard/MatrixGridWrapper";
import { FAB } from "@/components/ui/FAB";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button, FormSelect, FormInput, FormTextarea } from "@/components/ui";
import { useRole, canEditSchedule } from "@/hooks/useRole";
import { api } from "@/lib/api";
import type { CompensationDay, ConflictDetail, Schedule } from "@/types/api";

type QuickScheduleModalProps = {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  periodId: number | null;
  staffList?: { id: number; fullName: string }[];
  defaultShiftTypeId?: string;
  compensationDays?: CompensationDay[];
  periodStart?: string;
  periodEnd?: string;
};

export function QuickScheduleModal({
  open,
  onClose,
  onSuccess,
  periodId,
  staffList = [],
  defaultShiftTypeId = "L01",
  compensationDays,
  periodStart,
  periodEnd,
}: QuickScheduleModalProps) {
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [selectedShiftType, setSelectedShiftType] = useState(defaultShiftTypeId);
  const [selectedStaffId, setSelectedStaffId] = useState<string>("");
  const [workDate, setWorkDate] = useState<string>("");
  const [notes, setNotes] = useState<string>("");

  // Check if selected date is outside the period range
  const dateOutOfRange = Boolean(
    workDate && (
      (periodStart && workDate < periodStart) ||
      (periodEnd && workDate > periodEnd)
    )
  );

  useEffect(() => {
    if (!message) return;
    const t = setTimeout(() => setMessage(null), 4000);
    return () => clearTimeout(t);
  }, [message]);

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

    if (dateOutOfRange) {
      setMessage("Ngày làm việc phải nằm trong kỳ lịch.");
      return;
    }

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
    } catch (err) {
      setSubmitting(false);
      setMessage(err instanceof Error ? err.message : "Không thể tạo ca trực. Vui lòng thử lại.");
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Tạo nhanh ca trực" size="md">
      {done ? (
        <div className="flex flex-col items-center gap-4 py-8">
          <span aria-hidden="true" className="material-symbols-outlined text-[48px] text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
          <p className="text-title-lg text-on-surface font-semibold">Đã tạo thành công</p>
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
          {dateOutOfRange && (
            <div className="rounded-lg border border-error/20 bg-error-container px-3 py-2 text-label-sm text-error">
              Ngày làm việc phải nằm trong kỳ lịch.
            </div>
          )}
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
              disabled={!selectedStaffId || !workDate || dateOutOfRange}
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

type ScheduleMatrixViewProps = {
  schedules?: Schedule[];
  staffList?: { id: number; fullName: string }[];
  initialYear?: number;
  initialMonth?: number;
  periodId?: number | null;
  periodStart?: string;
  periodEnd?: string;
  onRefresh?: () => void;
  onAddClick?: (date: Date, staffId?: number) => void;
  selectedTab?: string;
  onFilterTypeChange?: (filter: string) => void;
  compensationDays?: CompensationDay[];
  isReadOnly?: boolean;
  canEditOverride?: boolean;
  onViewDetail?: (schedule: Schedule) => void;
  onEdit?: (schedule: Schedule) => void;
  onDelete?: (schedule: Schedule) => void;
  onResolve?: (conflict: ConflictDetail) => void;
  /** Hide the month/week toggle and other filter controls */
  hideFilters?: boolean;
};

export const ScheduleMatrixView = memo(function ScheduleMatrixView({
  schedules,
  staffList = [],
  initialYear,
  initialMonth,
  periodId,
  periodStart,
  periodEnd,
  isReadOnly = false,
  canEditOverride = false,
  onRefresh,
  onAddClick,
  selectedTab,
  compensationDays,
  onViewDetail,
  onEdit,
  onDelete,
  onResolve,
}: ScheduleMatrixViewProps) {
  const [matrixViewMode, setMatrixViewMode] = useState<"month" | "week" | "day">("month");
  const [quickOpen, setQuickOpen] = useState(false);
  const role = useRole();
  const canEdit = canEditSchedule(role) && (!isReadOnly || canEditOverride);
  const fabActions = canEdit ? [
    {
      id: "create-shift",
      icon: "schedule",
      label: "Tạo nhanh ca trực",
      onClick: () => setQuickOpen(true),
    },
  ] : [];

  // Stable onCellClick callback to prevent MatrixGridWrapper re-renders
  const stableOnCellClick = useCallback(
    (date: Date, staffId: number) => {
      if (onAddClick) onAddClick(date, staffId);
    },
    [onAddClick]
  );

  return (
    <div className="flex flex-col pt-1">
      <div className="flex items-center justify-end px-4 pt-3 pb-2">
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
          <button
            type="button"
            onClick={() => setMatrixViewMode("day")}
            aria-pressed={matrixViewMode === "day"}
            className={`rounded-md px-3 py-1 text-label-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
              matrixViewMode === "day"
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            Ngày
          </button>
        </div>
      </div>

      <div className="px-3 pb-3">
        <MatrixGridWrapper
          schedules={schedules ?? []}
          staffList={staffList ?? []}
          year={initialYear ?? new Date().getFullYear()}
          month={initialMonth ?? new Date().getMonth()}
          viewMode={matrixViewMode}
          compensationDays={compensationDays}
          shiftTypeFilter={selectedTab}
          onViewDetail={onViewDetail}
          onEdit={onEdit}
          onDelete={onDelete}
          onResolve={onResolve}
          onCellClick={stableOnCellClick}
          onRefresh={onRefresh}
          canEdit={canEdit}
        />
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
          periodStart={periodStart}
          periodEnd={periodEnd}
        />
      )}
    </div>
  );
});
