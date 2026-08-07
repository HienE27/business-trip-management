"use client";

import { Button } from "@/components/ui";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import type { SchedulePeriod } from "@/types/api";

interface Props {
  open: boolean;
  onClose: () => void;
  templateName: string;
  templateDesc: string;
  onTemplateNameChange: (v: string) => void;
  onTemplateDescChange: (v: string) => void;
  savingTemplate: boolean;
  selectedPeriod?: SchedulePeriod | null;
  algorithmType: string;
  scheduleCount: number;
  onSave: () => Promise<void>;
}

export function SaveTemplateModal({
  open,
  onClose,
  templateName,
  templateDesc,
  onTemplateNameChange,
  onTemplateDescChange,
  savingTemplate,
  selectedPeriod,
  algorithmType,
  scheduleCount,
  onSave,
}: Props) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Lưu mẫu lịch"
      description="Lưu phương án hiện tại thành mẫu để tái sử dụng cho các kỳ lịch sau."
    >
      <div className="space-y-4">
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="tmpl-name">
            Tên mẫu lịch <span className="text-error">*</span>
          </label>
          <input
            id="tmpl-name"
            type="text"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            placeholder="VD: Mẫu lịch tháng 6/2026"
            value={templateName}
            onChange={(e) => onTemplateNameChange(e.target.value)}
          />
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="tmpl-desc">
            Mô tả
          </label>
          <textarea
            id="tmpl-desc"
            className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            rows={2}
            placeholder="Ghi chú về mẫu lịch (VD: dùng cho tháng có ngày lễ)..."
            value={templateDesc}
            onChange={(e) => onTemplateDescChange(e.target.value)}
          />
        </div>
        <div className="p-3 bg-surface-container-low rounded-lg text-label-sm text-on-surface-variant space-y-1">
          <p><strong className="text-on-surface">Kỳ lịch gốc:</strong> {selectedPeriod?.periodName}</p>
          <p><strong className="text-on-surface">Thuật toán:</strong> {algorithmType}</p>
          <p><strong className="text-on-surface">Số ca:</strong> {scheduleCount}</p>
        </div>
      </div>
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
          disabled={!templateName.trim() || savingTemplate}
          loading={savingTemplate}
          onClick={onSave}
          icon={!savingTemplate ? <span className="material-symbols-outlined text-[16px]">bookmark_add</span> : undefined}
        >
          {savingTemplate ? "Đang lưu..." : "Lưu mẫu lịch"}
        </Button>
      </ModalFooter>
    </Modal>
  );
}
