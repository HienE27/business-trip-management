"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
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
      <ModalFooter>
        <Button
          variant="secondary"
          size="md"
          onClick={onClose}
        >
          Hủy
        </Button>
        <Button
          variant="primary"
          size="md"
          disabled={applying}
          loading={applying}
          onClick={onApply}
          icon={!applying ? <span className="material-symbols-outlined text-[16px]">check</span> : undefined}
        >
          {applying ? "Đang áp dụng..." : "Xác nhận áp dụng"}
        </Button>
      </ModalFooter>
    </Modal>
  );
}
