"use client";

import { useState, useEffect } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button, FormSelect } from "@/components/ui";
import type { AutoScheduleSummary } from "@/types/api";

type ShiftType = { id: string; name: string };

type PreviewEditModalProps = {
  open: boolean;
  onClose: () => void;
  /** Schedule item being edited */
  item: AutoScheduleSummary | null;
  /** Available staff for reassignment */
  staffList: { id: number; fullName: string }[];
  /** Available shift types for this date */
  shiftTypes: ShiftType[];
  onSave: (workDate: string, shiftTypeId: string, staffId: number, requirementId?: number | null) => void;
};

export function PreviewEditModal({
  open,
  onClose,
  item,
  staffList,
  shiftTypes,
  onSave,
}: PreviewEditModalProps) {
  const [selectedStaffId, setSelectedStaffId] = useState<string>("");
  const [selectedShiftTypeId, setSelectedShiftTypeId] = useState<string>("");

  useEffect(() => {
    if (open && item) {
      setSelectedStaffId(String(item.staffId));
      setSelectedShiftTypeId(item.shiftTypeId);
    }
  }, [open, item]);

  const handleSave = () => {
    if (!item || !selectedStaffId || !selectedShiftTypeId) return;
    onSave(item.workDate, selectedShiftTypeId, Number(selectedStaffId), item.requirementId);
    onClose();
  };

  const canSave = selectedStaffId && selectedShiftTypeId && item &&
    (selectedStaffId !== String(item.staffId) || selectedShiftTypeId !== item.shiftTypeId);

  const dateLabel = item
    ? new Date(item.workDate + "T00:00:00").toLocaleDateString("vi-VN", {
        weekday: "long",
        year: "numeric",
        month: "long",
        day: "numeric",
      })
    : "";

  return (
    <Modal open={open} onClose={onClose} title="Chỉnh sửa ca trực" size="sm">
      {item && (
        <div className="space-y-4">
          <div className="rounded-lg bg-surface-container-low p-3">
            <p className="text-label-sm text-on-surface-variant mb-1">Ngày trực</p>
            <p className="text-body-md text-on-surface font-medium">{dateLabel}</p>
          </div>

          <FormSelect
            label="Loại ca trực"
            id="pe-shift-type"
            value={selectedShiftTypeId}
            onChange={(e) => setSelectedShiftTypeId(e.target.value)}
            options={shiftTypes.map((st) => ({ value: st.id, label: st.name }))}
          />

          <FormSelect
            label="Nhân sự"
            id="pe-staff"
            placeholder="Chọn nhân sự..."
            value={selectedStaffId}
            onChange={(e) => setSelectedStaffId(e.target.value)}
            options={staffList.map((s) => ({
              value: String(s.id),
              label: s.fullName,
            }))}
          />

          {canSave && (
            <p className="text-label-xs text-on-surface-variant">
              Thay đổi: {shiftTypes.find((st) => st.id === selectedShiftTypeId)?.name} — {staffList.find((s) => s.id === Number(selectedStaffId))?.fullName}
            </p>
          )}

          <ModalFooter>
            <Button type="button" variant="secondary" onClick={onClose}>
              Hủy
            </Button>
            <Button
              type="button"
              variant="primary"
              onClick={handleSave}
              disabled={!canSave}
              icon={<span className="material-symbols-outlined" aria-hidden="true">save</span>}
            >
              Lưu thay đổi
            </Button>
          </ModalFooter>
        </div>
      )}
    </Modal>
  );
}
