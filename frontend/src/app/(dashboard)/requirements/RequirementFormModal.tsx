"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
import { SHIFT_COLORS, type ShiftColorSet } from "@/lib/shift-colors";
import type { SchedulePeriod, Specialty } from "@/types/api";

const SHIFT_TYPES = [
  { id: "L01", name: "Lịch trực 24/24" },
  { id: "L02", name: "Lịch thông tầm" },
  { id: "L03", name: "Phòng khám dịch vụ" },
  { id: "L04", name: "Phòng khám chuyên gia" },
];

export type RequirementFormValues = {
  periodId: number | "";
  workDate: string;
  shiftTypeId: string;
  specialtyId: number | "";
  requiredCount: number;
  note: string;
};

export type RequirementFormModalProps = {
  open: boolean;
  editing: boolean;
  values: RequirementFormValues;
  formError: string | null;
  saving: boolean;
  periods: SchedulePeriod[];
  specialties: Specialty[];
  onChange: (next: RequirementFormValues) => void;
  onSave: () => void;
  onCancel: () => void;
};

/**
 * Heavy form modal extracted from /requirements/page.tsx so it can be
 * lazy-loaded via next/dynamic. The modal bundle (Modal + ModalFooter
 * + 4 form selects/inputs/textarea + SHIFT_COLORS lookup) only ships
 * to the client after the user clicks "Thêm" or "Chỉnh sửa".
 */
export function RequirementFormModal({
  open,
  editing,
  values,
  formError,
  saving,
  periods,
  specialties,
  onChange,
  onSave,
  onCancel,
}: RequirementFormModalProps) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={editing ? "Chỉnh sửa yêu cầu nhân sự" : "Thêm yêu cầu nhân sự"}
      size="lg"
    >
      <div className="space-y-4 py-2">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Kỳ lịch <span className="text-error">*</span>
            </label>
            <select
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={values.periodId}
              onChange={(e) =>
                onChange({ ...values, periodId: e.target.value ? Number(e.target.value) : "" })
              }
            >
              <option value="">Chọn kỳ lịch...</option>
              {periods.filter((p) => p.status === "DRAFT").map((p) => (
                <option key={p.id} value={p.id}>{p.periodName}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Ngày <span className="text-error">*</span>
            </label>
            <input
              type="date"
              className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
              value={values.workDate}
              onChange={(e) => onChange({ ...values, workDate: e.target.value })}
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Loại ca <span className="text-error">*</span>
            </label>
            <select
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={values.shiftTypeId}
              onChange={(e) => onChange({ ...values, shiftTypeId: e.target.value })}
            >
              {SHIFT_TYPES.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Chuyên khoa <span className="text-error">*</span>
            </label>
            <select
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={values.specialtyId}
              onChange={(e) =>
                onChange({ ...values, specialtyId: e.target.value ? Number(e.target.value) : "" })
              }
            >
              <option value="">Chọn chuyên khoa...</option>
              {specialties.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className="block font-label-md text-label-md text-on-surface mb-1.5">
            Số nhân sự yêu cầu <span className="text-error">*</span>
          </label>
          <input
            type="number"
            min={1}
            className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
            value={values.requiredCount}
            onChange={(e) =>
              onChange({ ...values, requiredCount: Math.max(1, Number(e.target.value)) })
            }
          />
        </div>
        <div>
          <label className="block font-label-md text-label-md text-on-surface mb-1.5">Ghi chú</label>
          <textarea
            className="w-full p-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all resize-none rounded-lg"
            rows={2}
            placeholder="Ghi chú (tùy chọn)"
            value={values.note}
            onChange={(e) => onChange({ ...values, note: e.target.value })}
          />
        </div>
        {formError && (
          <div className="p-3 bg-error-container border border-error/20 rounded-lg text-on-error-container text-body-sm">
            {formError}
          </div>
        )}
      </div>
      <ModalFooter>
        <Button variant="ghost" onClick={onCancel}>
          Hủy
        </Button>
        <Button onClick={onSave} loading={saving}>
          {editing ? "Cập nhật" : "Tạo mới"}
        </Button>
      </ModalFooter>
    </Modal>
  );
}

// Re-export SHIFT_TYPES for the parent page (still used in colour lookup,
// and the new <select> import is colocated here). Parent should not import
// it from this file — keeping for explicit re-export to keep the bundle
// surface obvious.
export { SHIFT_TYPES };
export type { ShiftColorSet };
