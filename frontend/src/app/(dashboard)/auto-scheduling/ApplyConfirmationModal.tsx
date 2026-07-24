"use client";

import { Modal } from "@/components/ui/Modal";
import type { AutoScheduleSummary, SchedulePeriod } from "@/types/api";

interface Props {
  open: boolean;
  onClose: () => void;
  selectedPeriod?: SchedulePeriod | null;
  previewResult?: { totalSchedulesCreated: number; coverageRate: number | null; conflictCount: number | null } | null;
  editedPreview: unknown[];
  removedShiftTypes: Set<string>;
  applying: boolean;
  onApply: () => Promise<void>;
}

export function ApplyConfirmationModal({
  open,
  onClose,
  selectedPeriod,
  previewResult,
  editedPreview,
  removedShiftTypes,
  applying,
  onApply,
}: Props) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Xác nhận áp dụng phương án"
      description="Phương án phân công sẽ được ghi đè lên lịch hiện tại. Hành động này không thể hoàn tác."
    >
      {previewResult && (
        <div className="mt-3 p-3 bg-surface-container-low rounded-lg text-label-sm text-on-surface-variant space-y-1">
          <p>Tổng ca: <strong className="text-on-surface">{previewResult.totalSchedulesCreated}</strong></p>
          <p>Tỷ lệ phủ: <strong className="text-on-surface">{previewResult.coverageRate == null ? "—" : `${Math.round(previewResult.coverageRate)}%`}</strong></p>
          <p>Xung đột: <strong className={previewResult.conflictCount == null ? "" : "text-error"}>{previewResult.conflictCount == null ? "—" : previewResult.conflictCount}</strong></p>
          {editedPreview.length > 0 && (
            <p className="text-primary">Có <strong>{editedPreview.length}</strong> ca đã chỉnh sửa thủ công.</p>
          )}
          {removedShiftTypes.size > 0 && (
            <p className="text-primary">Có <strong>{removedShiftTypes.size}</strong> ca đổi loại lịch.</p>
          )}
        </div>
      )}
      <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-outline-variant bg-surface-container-low mt-4 -mx-4 sm:-mx-6 mb-[-16px]">
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          Hủy
        </button>
        <button
          type="button"
          onClick={onApply}
          disabled={applying}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
        >
          <span className="material-symbols-outlined text-[16px]">check</span>
          {applying ? "Đang áp dụng..." : "Xác nhận áp dụng"}
        </button>
      </div>
    </Modal>
  );
}
