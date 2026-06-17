"use client";

import { memo, useEffect, useId, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
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

export const QuickAddModal = memo(function QuickAddModal({ date, periodId, defaultShiftTypeId, staffList, compensationDays, onSuccess, onClose }: QuickAddModalProps) {
  const id = useId();
  const [shiftTypeId, setShiftTypeId] = useState(defaultShiftTypeId);
  const [staffId, setStaffId] = useState<number | "">("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dateKey = date ? date.toISOString().slice(0, 10) : null;

  useEffect(() => {
    setShiftTypeId(defaultShiftTypeId);
    setStaffId("");
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
      });
      onSuccess();
    } catch (err) {
      setError(getErrorMessage(err, "Không thể tạo lịch. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  };

  const dateLabel = date?.toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" }) ?? "";

  return (
    <Modal open={date !== null} onClose={onClose} title="Thêm lịch nhanh" size="md">
      {date && (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="text-label-md text-on-surface-variant">
            Ngày: <span className="font-semibold text-on-surface">{dateLabel}</span>
          </div>

          {error && (
            <div className="flex items-center gap-2 rounded-lg border border-error/20 bg-error-container px-4 py-2 text-label-sm text-on-error-container" role="alert">
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">error</span>
              {error}
            </div>
          )}

          <div>
            <label className="mb-2 block text-label-sm text-on-surface-variant" htmlFor={`${id}-shift-type`}>
              Loại lịch
            </label>
            <div className="relative">
              <select
                id={`${id}-shift-type`}
                className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
                value={shiftTypeId}
                onChange={(event) => setShiftTypeId(event.target.value as ScheduleTab)}
                required
              >
                {TAB_OPTIONS.map((option) => (
                  <option key={option.id} value={option.id}>{option.label}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline" aria-hidden="true">expand_more</span>
            </div>
          </div>

          <div>
            <label className="mb-2 block text-label-sm text-on-surface-variant" htmlFor={`${id}-staff`}>
              Nhân sự
            </label>
            <div className="relative">
              <select
                id={`${id}-staff`}
                className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
                value={staffId}
                onChange={(event) => setStaffId(event.target.value ? Number(event.target.value) : "")}
                required
              >
                <option value="">Chọn nhân sự…</option>
                {staffList.map((staff) => (
                  <option key={staff.id} value={staff.id}>{staff.fullName}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline" aria-hidden="true">expand_more</span>
            </div>
          </div>

          <ModalFooter>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-outline-variant px-4 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              Hủy
            </button>
            <button
              type="submit"
              aria-label="Tạo lịch"
              disabled={submitting || staffId === "" || !periodId}
              className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {submitting ? (
                <><div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" aria-hidden="true" /><span>Đang tạo…</span></>
              ) : (
                <><span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span><span>Tạo lịch</span></>
              )}
            </button>
          </ModalFooter>
        </form>
      )}
    </Modal>
  );
});
