"use client";

import { Button } from "@/components/ui/Button";

type Props = {
  onApplyTemplate: () => void;
  onSaveTemplate?: () => void;
};

/**
 * Hai nút tách biệt cho template actions:
 * - "Áp dụng mẫu" — dùng template đã lưu
 * - "Lưu mẫu" — lưu preview hiện tại thành template mới
 *
 * Trước đây dùng split-button + dropdown, nhưng UX gây nhầm lẫn — nút
 * "Lưu mẫu" quan trọng bị ẩn sau mũi tên expand_more. Hiển thị rõ
 * cả hai để người dùng thấy ngay từ toolbar.
 */
export function TemplateActionsSplitButton({ onApplyTemplate, onSaveTemplate }: Props) {
  return (
    <div className="flex items-center gap-1.5 shrink-0">
      <Button
        variant="ghost"
        size="sm"
        onClick={onApplyTemplate}
        icon={<span className="material-symbols-outlined text-[16px]">download</span>}
        title="Áp dụng mẫu lịch đã lưu cho kỳ hiện tại"
      >
        Áp dụng mẫu
      </Button>
      {onSaveTemplate && (
        <Button
          variant="ghost"
          size="sm"
          onClick={onSaveTemplate}
          icon={<span className="material-symbols-outlined text-[16px]">bookmark_add</span>}
          title="Lưu preview hiện tại thành template mới"
        >
          Lưu mẫu
        </Button>
      )}
    </div>
  );
}
