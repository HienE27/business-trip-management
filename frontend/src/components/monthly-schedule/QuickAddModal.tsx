"use client";

import { memo, useEffect, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { FormSelect, FormTextarea, Button } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { CompensationDay, Schedule, Staff } from "@/types/api";
import type { ScheduleTab } from "./types";
import { TAB_OPTIONS } from "./constants";

export type QuickAddModalProps = {
  date: Date | null;
  periodId: number | null;
  defaultShiftTypeId: ScheduleTab;
  staffList: Staff[];
  compensationDays?: CompensationDay[];
  /**
   * Called the instant the user submits a valid form. The parent
   * page should append the temporary schedule to its calendar
   * immediately so the user sees instant feedback. The temporary
   * id is negative and stable for the lifetime of the request,
   * which lets the parent either commit the real schedule (via
   * {@link QuickAddModalProps.onCommit}) or roll the optimistic
   * insert back (via {@link QuickAddModalProps.onRollback}).
   */
  onOptimisticAdd?: (tempSchedule: Schedule) => void;
  /**
   * Replace the temporary schedule (negative id) with the real
   * one returned by the backend. Called on a successful POST.
   */
  onCommit?: (tempId: number, realSchedule: Schedule) => void;
  /**
   * Drop the temporary schedule after a failed POST so the
   * calendar goes back to its prior state.
   */
  onRollback?: (tempId: number) => void;
  /**
   * Fallback refresh callback for callers that don't opt into
   * optimistic updates. Still invoked on success when the other
   * two callbacks are not supplied.
   */
  onSuccess: () => void;
  onClose: () => void;
};

/**
 * Build a negative temporary id from the current timestamp.
 * Using the negative space keeps temp ids visually distinct from
 * real backend ids and lets a single dashboard safely host many
 * in-flight optimistic inserts.
 */
function nextTempId(): number {
  return -Date.now();
}

export const QuickAddModal = memo(function QuickAddModal({
  date,
  periodId,
  defaultShiftTypeId,
  staffList,
  compensationDays,
  onOptimisticAdd,
  onCommit,
  onRollback,
  onSuccess,
  onClose,
}: QuickAddModalProps) {
  const [shiftTypeId, setShiftTypeId] = useState(defaultShiftTypeId);
  const [staffId, setStaffId] = useState<number | "">("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dateKey = date ? date.toISOString().slice(0, 10) : null;

  useEffect(() => {
    setShiftTypeId(defaultShiftTypeId);
    setStaffId("");
    setNotes("");
    setError(null);
  }, [dateKey, defaultShiftTypeId]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!date || !periodId || staffId === "") return;

    setSubmitting(true);
    setError(null);

    // Client-side guard: refuse to send a request when the
    // picked staff member is on a compensation day for this
    // date. The backend would reject it anyway, and the
    // optimistic insert would have to be rolled back.
    if (compensationDays && staffId) {
      const dateStr = date.toISOString().slice(0, 10);
      const isCompDay = compensationDays.some(
        (cd) => cd.staffId === Number(staffId) && cd.compensationDate === dateStr
      );
      if (isCompDay) {
        setError("Ngày này là ngày nghỉ bù của nhân sự này. Không thể xếp lịch.");
        setSubmitting(false);
        return;
      }
    }

    const staff = staffList.find((s) => s.id === Number(staffId));
    const tempId = nextTempId();
    const workDateStr = date.toISOString().slice(0, 10);
    const trimmedNotes = notes.trim();

    // Build the optimistic schedule. Fields the backend hasn't
    // echoed yet (id, createdAt, updatedAt, hasConflict) get
    // sensible placeholders; the parent will overwrite them in
    // onCommit.
    const optimistic: Schedule = {
      id: tempId,
      periodId,
      workDate: workDateStr,
      staff: staff
        ? {
            id: staff.id,
            fullName: staff.fullName,
          }
        : { id: Number(staffId), fullName: "Đang tải…" },
      shiftType: {
        id: shiftTypeId,
        name: TAB_OPTIONS.find((o) => o.id === shiftTypeId)?.label ?? shiftTypeId,
        isOvernight: false,
      },
      hasConflict: false,
      ...(trimmedNotes ? { notes: trimmedNotes } : {}),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    const useOptimistic = Boolean(onOptimisticAdd && onCommit && onRollback);
    if (useOptimistic && onOptimisticAdd) {
      onOptimisticAdd(optimistic);
    }

    try {
      const realSchedule = (await api.post<Schedule>("/schedules", {
        periodId,
        workDate: workDateStr,
        staffId: Number(staffId),
        shiftTypeId,
        ...(trimmedNotes ? { notes: trimmedNotes } : {}),
      })) as Schedule;

      if (useOptimistic && onCommit) {
        onCommit(tempId, realSchedule);
      } else {
        onSuccess();
      }
    } catch (err) {
      if (useOptimistic && onRollback) {
        onRollback(tempId);
      }
      setError(getErrorMessage(err, "Không thể tạo lịch. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  };

  const dateLabel = date?.toLocaleDateString("vi-VN", {
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }) ?? "";

  const staffOptions = staffList.map((s) => ({
    value: String(s.id),
    label: s.fullName,
  }));

  return (
    <Modal open={date !== null} onClose={onClose} title="Thêm lịch nhanh" size="md">
      {date && (
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Date badge */}
          <div className="rounded-lg border border-outline-variant bg-surface-container-low px-4 py-3">
            <span className="text-label-sm text-on-surface-variant">Ngày: </span>
            <span className="text-label-md font-semibold text-on-surface">{dateLabel}</span>
          </div>

          {/* Form-level error */}
          {error && (
            <div
              className="flex items-center gap-2 rounded-lg border border-error/20 bg-error-container px-4 py-2.5 text-label-sm text-on-error-container"
              role="alert"
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">error</span>
              {error}
            </div>
          )}

          <FormSelect
            label="Loại lịch"
            value={shiftTypeId}
            onChange={(e) => setShiftTypeId(e.target.value as ScheduleTab)}
            options={TAB_OPTIONS.map((o) => ({ value: o.id, label: o.label }))}
            required
            disabled={submitting}
          />

          <FormSelect
            label="Nhân sự"
            placeholder="Chọn nhân sự…"
            value={String(staffId)}
            onChange={(e) => setStaffId(e.target.value ? Number(e.target.value) : "")}
            options={staffOptions}
            required
            disabled={submitting}
          />

          <FormTextarea
            label="Ghi chú"
            rows={2}
            maxLength={200}
            showCount
            hint="Tùy chọn, tối đa 200 ký tự"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            disabled={submitting}
          />

          <ModalFooter>
            <Button type="button" variant="secondary" onClick={onClose}>
              Hủy
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              disabled={staffId === "" || !periodId}
              icon={<span className="material-symbols-outlined" aria-hidden="true">add</span>}
            >
              Tạo lịch
            </Button>
          </ModalFooter>
        </form>
      )}
    </Modal>
  );
});