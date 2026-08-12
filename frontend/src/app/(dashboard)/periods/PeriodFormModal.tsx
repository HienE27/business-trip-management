"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";

export type PeriodFormValues = {
  name: string;
  startDate: string;
  endDate: string;
};

export type PeriodFormModalProps = {
  open: boolean;
  editing: boolean;
  values: PeriodFormValues;
  formError: string | null;
  saving: boolean;
  onChange: (next: PeriodFormValues) => void;
  onSave: () => void;
  onCancel: () => void;
};

/**
 * Heavy modal extracted from /periods/page.tsx so it can be lazy-loaded
 * via next/dynamic. The modal bundle (Modal + ModalFooter + form inputs)
 * only ships to the client after the user clicks "Tạo kỳ lịch" /
 * "Chỉnh sửa" — saves ~5 KB on the initial /periods render.
 */
export function PeriodFormModal({
  open,
  editing,
  values,
  formError,
  saving,
  onChange,
  onSave,
  onCancel,
}: PeriodFormModalProps) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={editing ? "Chỉnh sửa kỳ lịch" : "Tạo kỳ lịch mới"}
      size="lg"
    >
      <div className="space-y-4 py-2">
        <div>
          <label className="block font-label-md text-label-md text-on-surface mb-1.5">
            Tên kỳ lịch <span className="text-red-800">*</span>
          </label>
          <input
            type="text"
            className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
            placeholder="VD: Lịch tháng 6/2026"
            value={values.name}
            onChange={(e) => onChange({ ...values, name: e.target.value })}
            maxLength={50}
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Ngày bắt đầu <span className="text-red-800">*</span>
            </label>
            <input
              type="date"
              className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
              value={values.startDate}
              onChange={(e) => onChange({ ...values, startDate: e.target.value })}
            />
          </div>
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Ngày kết thúc <span className="text-red-800">*</span>
            </label>
            <input
              type="date"
              className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
              value={values.endDate}
              onChange={(e) => onChange({ ...values, endDate: e.target.value })}
            />
          </div>
        </div>
        {formError && (
          <div className="p-3 bg-red-100 border border-red-300 rounded-lg text-red-800 text-body-sm">
            {formError}
          </div>
        )}
      </div>
      <ModalFooter>
        <Button variant="ghost" onClick={onCancel}>
          Hủy
        </Button>
        <Button onClick={onSave} loading={saving}>
          {editing ? "Cập nhật" : "Tạo kỳ lịch"}
        </Button>
      </ModalFooter>
    </Modal>
  );
}