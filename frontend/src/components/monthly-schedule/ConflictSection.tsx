"use client";

import { memo } from "react";
import { ConflictInspector } from "@/components/schedule-summary/ConflictInspector";
import type { ConflictDetail } from "@/types/api";
import type { ConflictItem } from "@/types/schedule";

export type ConflictSectionProps = {
  conflicts: ConflictDetail[];
  selectedConflict: ConflictDetail | null;
  selectedPeriodId: number | null;
  onSelect: (conflict: ConflictDetail) => void;
  onClose: () => void;
  onFocusDate: (date: string) => void;
  onShowConflicts: () => void;
  onResolve: (conflict: ConflictItem) => void;
};

export const ConflictSection = memo(function ConflictSection({
  conflicts,
  selectedConflict,
  selectedPeriodId,
  onSelect,
  onClose,
  onFocusDate,
  onShowConflicts,
  onResolve,
}: ConflictSectionProps) {
  return (
    <ConflictInspector
      conflicts={conflicts}
      emptyLabel="Không có xung đột cho loại lịch đang chọn."
      title="Conflict panel"
      description="Click vào xung đột để focus đúng ngày lịch và mở chi tiết xử lý."
      selectedConflict={selectedConflict}
      onSelect={(conflict) => {
        onSelect(conflict);
        onFocusDate(conflict.workDate.split("T")[0]);
        onShowConflicts();
      }}
      onClose={onClose}
      onResolve={(conflict) => {
        onResolve({
          id: String(conflict.scheduleId),
          type: "SCHEDULE_CONFLICT",
          staffName: conflict.staffName,
          date: new Date(conflict.workDate).toLocaleDateString("vi-VN"),
          severity: "Chặn lưu",
          detail: `Xung đột: ${conflict.conflictReasons.join("; ")}`,
          shiftType: conflict.shiftTypeName,
          periodId: selectedPeriodId ?? undefined,
          workDate: conflict.workDate,
          shiftTypeId: conflict.shiftTypeId,
          originalStaffId: conflict.scheduleId,
        });
      }}
    />
  );
});
