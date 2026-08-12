"use client";

import { memo, useEffect, useMemo, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { FormSelect, FormTextarea, Button } from "@/components/ui";
import { StaffSearchCombobox } from "@/components/ui/StaffSearchCombobox";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { CompensationDay, Holiday, LeaveRequest, Schedule, Staff } from "@/types/api";
import type { ScheduleTab } from "./types";
import { TAB_OPTIONS } from "./constants";

export type QuickAddModalProps = {
  date: Date | null;
  periodId: number | null;
  defaultShiftTypeId: ScheduleTab;
  staffList: Staff[];
  schedules?: Schedule[];
  /** National holidays — selected date will show an advisory warning. */
  holidays?: Holiday[];
  compensationDays?: CompensationDay[];
  /** Approved leave requests — used to mark staff unavailable in the staff selector. */
  leaveRequests?: LeaveRequest[];
  /** ISO date string (YYYY-MM-DD) lower bound of the active period. */
  periodStart?: string;
  /** ISO date string (YYYY-MM-DD) upper bound of the active period. */
  periodEnd?: string;
  onOptimisticAdd?: (tempSchedule: Schedule) => void;
  onCommit?: (tempId: number, realSchedule: Schedule) => void;
  onRollback?: (tempId: number) => void;
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
  schedules = [],
  holidays,
  compensationDays,
  leaveRequests = [],
  onOptimisticAdd,
  onCommit,
  onRollback,
  onSuccess,
  onClose,
  periodStart,
  periodEnd,
}: QuickAddModalProps) {
  const [shiftTypeId, setShiftTypeId] = useState(defaultShiftTypeId);
  const [staffId, setStaffId] = useState<number | "">("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Helper: convert Date to local YYYY-MM-DD string (avoids UTC offset shift)
  const toLocalDateStr = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  };

  const dateKey = date ? toLocalDateStr(date) : null;

  // Safety net: detect out-of-range dates that slipped through
  // the parent guard. This can happen if the period changes while
  // the modal is open or if the calendar navigation allowed an
  // out-of-bounds date click.
  // Compare local YYYY-MM-DD strings to avoid UTC timezone shifts.
  const dateOutOfRange = Boolean(
    date && (
      (periodStart && toLocalDateStr(date) < periodStart) ||
      (periodEnd && toLocalDateStr(date) > periodEnd)
    )
  );

  // Reset form state AND set date-out-of-range error in a single effect.
  // Splitting into two useEffect hooks causes a race: the second effect
  // runs after the first and overwrites the error back to null.
  useEffect(() => {
    setShiftTypeId(defaultShiftTypeId);
    setStaffId("");
    setNotes("");
    if (dateOutOfRange) {
      setError("Ngày làm việc phải nằm trong kỳ lịch.");
    } else {
      setError(null);
    }
  }, [dateKey, defaultShiftTypeId, dateOutOfRange]);

  // When dateOutOfRange flips true, surface the error immediately
  // so the user doesn't have to click submit to discover the problem.
  useEffect(() => {
    if (dateOutOfRange) {
      setError("Ngày làm việc phải nằm trong kỳ lịch.");
    }
  }, [dateOutOfRange]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!date || !periodId || staffId === "") return;

    setSubmitting(true);
    setError(null);

    if (dateOutOfRange) {
      setError("Ngày làm việc phải nằm trong kỳ lịch.");
      setSubmitting(false);
      return;
    }

    // Client-side guard: refuse to send a request when the
    // picked staff member is on a compensation day for this
    // date. The backend would reject it anyway, and the
    // optimistic insert would have to be rolled back.
    if (compensationDays && staffId) {
      const dateStr = toLocalDateStr(date);
      const isCompDay = compensationDays.some(
        (cd) => cd.staffId === Number(staffId) && cd.compensationDate === dateStr
      );
      if (isCompDay) {
        setError("Ngày này là ngày nghỉ bù của nhân sự này. Không thể xếp lịch.");
        setSubmitting(false);
        return;
      }
    }

    // Client-side guard: refuse to send a request when the
    // picked date is a national or registered holiday.
    if (holidays) {
      const dateStr = toLocalDateStr(date);
      const holiday = holidays.find((h) => h.holidayDate === dateStr);
      if (holiday) {
        setError(
          `Ngày ${date.toLocaleDateString("vi-VN")} là ngày lễ: ${holiday.name}. Không thể xếp lịch vào ngày nghỉ lễ.`
        );
        setSubmitting(false);
        return;
      }
    }

    // Client-side guard: refuse to send a request when the
    // picked staff member is on approved leave for this date.
    if (leaveRequests.length > 0 && staffId) {
      const dateStr = toLocalDateStr(date);
      const onLeave = leaveRequests.some(
        (lr) =>
          lr.staffId === Number(staffId) &&
          dateStr >= lr.startDate &&
          dateStr <= lr.endDate
      );
      if (onLeave) {
        setError("Nhân sự có ngày nghỉ phép được duyệt trong ngày này. Không thể xếp lịch.");
        setSubmitting(false);
        return;
      }
    }

    const staff = staffList.find((s) => s.id === Number(staffId));
    const tempId = nextTempId();
    const workDateStr = toLocalDateStr(date);
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

  const existingScheduleStaffIds = useMemo(() => {
    if (!dateKey) return [];
    return schedules
      .filter((s) => s.workDate === dateKey && s.shiftType.id === shiftTypeId)
      .map((s) => s.staff.id);
  }, [schedules, dateKey, shiftTypeId]);

  /** Staff IDs that have a conflicting schedule type on this date.
   * L01 ↔ L02: same staff, same date → conflict.
   * L03 ↔ L04: same staff, same date → conflict.
   * When selecting L01/L02, cross-block other type; when selecting L03/L04, cross-block other type.
   */
  const existingConflictStaffIds = useMemo(() => {
    if (!dateKey || !["L01", "L02", "L03", "L04"].includes(shiftTypeId)) return [];
    const conflictMap: Record<string, string[]> = {
      L01: ["L02"],
      L02: ["L01"],
      L03: ["L04"],
      L04: ["L03"],
    };
    const conflictingTypes = conflictMap[shiftTypeId] ?? [];
    return schedules
      .filter((s) => s.workDate === dateKey && conflictingTypes.includes(s.shiftType.id))
      .map((s) => s.staff.id);
  }, [schedules, dateKey, shiftTypeId]);

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
              className="flex items-center gap-2 rounded-lg border border-red-300 bg-red-100 text-red-800"
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

          <StaffSearchCombobox
            value={staffId}
            onChange={(id) => setStaffId(id)}
            staffList={staffList}
            workDate={dateKey ?? ""}
            compensationDays={compensationDays}
            existingScheduleStaffIds={existingScheduleStaffIds}
            existingConflictStaffIds={existingConflictStaffIds}
            leaveRequests={leaveRequests}
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
              disabled={staffId === "" || !periodId || dateOutOfRange}
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