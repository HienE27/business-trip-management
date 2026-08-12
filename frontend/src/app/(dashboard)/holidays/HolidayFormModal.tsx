"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";

export type HolidayFormValues = {
  name: string;
  holidayDate: string;
  isNational: boolean;
  description: string;
};

export type HolidayFormModalProps = {
  open: boolean;
  editing: boolean;
  values: HolidayFormValues;
  formError: string | null;
  saving: boolean;
  onChange: (next: HolidayFormValues) => void;
  onSave: () => void;
  onCancel: () => void;
};

/**
 * Heavy form modal extracted from /holidays/page.tsx so it can be
 * lazy-loaded via next/dynamic. The modal bundle (Modal + ModalFooter
 * + 3 form fields + textarea) only ships to the client after the
 * user clicks "Thêm" or "Sửa".
 */
export function HolidayFormModal({
  open,
  editing,
  values,
  formError,
  saving,
  onChange,
  onSave,
  onCancel,
}: HolidayFormModalProps) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={editing ? "Sửa ngày lễ" : "Thêm ngày lễ mới"}
      description={editing ? "Cập nhật thông tin ngày lễ." : "Điền thông tin ngày lễ cần thêm."}
    >
      <div className="space-y-4">
        {formError && (
          <div className="rounded-lg border border-red-300 bg-red-100 text-red-800 px-4 py-3 text-sm">
            {formError}
          </div>
        )}
        <div>
          <label className="mb-1.5 block text-label-sm text-on-surface-variant">
            Tên ngày lễ <span className="text-red-800">*</span>
          </label>
          <input
            type="text"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            placeholder="VD: Quốc khánh 2/9"
            value={values.name}
            onChange={(e) => onChange({ ...values, name: e.target.value })}
          />
        </div>
        <div>
          <label className="mb-1.5 block text-label-sm text-on-surface-variant">
            Ngày lễ <span className="text-red-800">*</span>
          </label>
          <input
            type="date"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={values.holidayDate}
            onChange={(e) => onChange({ ...values, holidayDate: e.target.value })}
          />
        </div>
        <div>
          <label className="mb-1.5 block text-label-sm text-on-surface-variant">Loại</label>
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                className="accent-primary size-4"
                checked={values.isNational}
                onChange={(e) => onChange({ ...values, isNational: e.target.checked })}
              />
              <span className="text-label-md text-on-surface">Ngày Quốc khánh / Nghỉ lễ</span>
            </label>
          </div>
          <p className="mt-1 text-label-xs text-on-surface-variant">
            {values.isNational ? "Ngày nghỉ lễ toàn quốc." : "Ngày lễ kỷ niệm hoặc nghỉ bù."}
          </p>
        </div>
        <div>
          <label className="mb-1.5 block text-label-sm text-on-surface-variant">Mô tả</label>
          <textarea
            className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            rows={2}
            placeholder="Ghi chú thêm..."
            value={values.description}
            onChange={(e) => onChange({ ...values, description: e.target.value })}
          />
        </div>
      </div>
      <ModalFooter>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="rounded-lg border border-outline-variant px-4 py-2 text-label-md text-on-surface hover:bg-surface-container-low disabled:opacity-50 transition-colors"
        >
          Hủy
        </button>
        <button
          type="button"
          onClick={onSave}
          disabled={saving}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-blue-100 text-label-md font-semibold text-blue-800 hover:bg-blue-200 disabled:opacity-50 transition-colors"
        >
          <span className="material-symbols-outlined text-[16px]">check</span>
          {saving ? "Đang lưu..." : editing ? "Cập nhật" : "Thêm mới"}
        </button>
      </ModalFooter>
    </Modal>
  );
}
