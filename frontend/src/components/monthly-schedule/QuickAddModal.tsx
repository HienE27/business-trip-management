"use client";

import { memo, useEffect, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { FormSelect, FormTextarea, Button } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { CompensationDay, Staff } from "@/types/api";
import type { ScheduleTab } from "./types";
import { TAB_OPTIONS } from "./constants";

export type QuickAddModalProps = {
  date: Date | null;
  periodId: number | null;
  defaultShiftTypeId: ScheduleTab;
  staffList: Staff[];
  compensationDays?: CompensationDay[];
  onSuccess: () => void;
  onClose: () => void;
};

export const QuickAddModal = memo(function QuickAddModal({
  date,
  periodId,
  defaultShiftTypeId,
  staffList,
  compensationDays,
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
    try {
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
      await api.post("/schedules", {
        periodId,
        workDate: date.toISOString().slice(0, 10),
        staffId,
        shiftTypeId,
        ...(notes.trim() ? { notes: notes.trim() } : {}),
      });
      onSuccess();
    } catch (err) {
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
