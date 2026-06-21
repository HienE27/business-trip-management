"use client";

import { useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import type { AutoScheduleSummary, SchedulePeriod, ShiftType, Staff } from "@/types/api";

interface AddShiftModalProps {
  open: boolean;
  onClose: () => void;
  shiftTypes: ShiftType[];
  staffList: Staff[];
  selectedPeriod: SchedulePeriod | null;
  onAdd: (shift: AutoScheduleSummary) => void;
}

export function AddShiftModal({
  open,
  onClose,
  shiftTypes,
  staffList,
  selectedPeriod,
  onAdd,
}: AddShiftModalProps) {
  const [shiftDate, setShiftDate] = useState("");
  const [shiftTypeId, setShiftTypeId] = useState("");
  const [staffId, setStaffId] = useState<number | "">("");

  const shiftTypeName = shiftTypes.find((t) => t.id === shiftTypeId)?.name ?? "";
  const staffName = staffList.find((s) => s.id === staffId)?.fullName ?? "";
  const isValid = shiftDate && shiftTypeId && staffId !== "";

  function handleSubmit() {
    if (!isValid) return;
    onAdd({
      workDate: shiftDate,
      shiftTypeId,
      shiftTypeName,
      staffId: staffId as number,
      staffName,
      scheduleId: null,
    });
    setShiftDate("");
    setShiftTypeId("");
    setStaffId("");
    onClose();
  }

  function handleClose() {
    setShiftDate("");
    setShiftTypeId("");
    setStaffId("");
    onClose();
  }

  if (!open) return null;

  return (
    <Modal open={open} onClose={handleClose} title="Thêm ca trực mới">
      <div className="space-y-4">
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="shift-date">
            Ngày làm việc <span className="text-error">*</span>
          </label>
          <input
            id="shift-date"
            type="date"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={shiftDate}
            min={selectedPeriod?.startDate ?? ""}
            max={selectedPeriod?.endDate ?? ""}
            onChange={(e) => setShiftDate(e.target.value)}
          />
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="shift-type">
            Loại lịch <span className="text-error">*</span>
          </label>
          <div className="relative">
            <select
              id="shift-type"
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-low text-label-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={shiftTypeId}
              onChange={(e) => setShiftTypeId(e.target.value)}
            >
              <option value="">-- Chọn loại lịch --</option>
              {shiftTypes.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
          </div>
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="shift-staff">
            Nhân sự <span className="text-error">*</span>
          </label>
          <div className="relative">
            <select
              id="shift-staff"
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-low text-label-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={staffId}
              onChange={(e) => setStaffId(Number(e.target.value))}
            >
              <option value="">-- Chọn nhân sự --</option>
              {staffList.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.fullName}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
          </div>
        </div>
      </div>
      <ModalFooter>
        <button
          type="button"
          onClick={handleClose}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          Hủy
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={!isValid}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
        >
          Thêm ca trực
        </button>
      </ModalFooter>
    </Modal>
  );
}
