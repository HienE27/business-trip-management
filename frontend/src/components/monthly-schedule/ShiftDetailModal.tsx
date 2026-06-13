"use client";

import { memo } from "react";
import { Modal } from "@/components/ui/Modal";
import { ShiftDetailInfo } from "@/components/shift-detail/ShiftDetailInfo";
import { ShiftDetailTable } from "@/components/shift-detail/ShiftDetailTable";
import type { Schedule } from "@/types/api";
import { buildShiftDetailViewModel } from "./utils";

export type ShiftDetailModalProps = {
  scheduleId: number | null;
  schedule: Schedule | null;
  loading: boolean;
  onClose: () => void;
};

export const ShiftDetailModal = memo(function ShiftDetailModal({ scheduleId, schedule, loading, onClose }: ShiftDetailModalProps) {
  return (
    <Modal
      open={scheduleId !== null}
      onClose={onClose}
      title={schedule ? `Chi tiết ca trực — ${schedule.shiftType.name}` : "Chi tiết ca trực"}
      description={schedule ? `${schedule.staff.fullName} · ${new Date(schedule.workDate).toLocaleDateString("vi-VN")}` : undefined}
      size="xl"
    >
      {loading ? (
        <div className="flex h-48 items-center justify-center" aria-label="Đang tải chi tiết ca trực">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : schedule ? (
        <div className="space-y-6">
          {(() => {
            const vm = buildShiftDetailViewModel(schedule);
            return (
              <>
                <div className="flex items-center gap-3">
                  <span className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-semibold text-white ${vm.shiftColor}`}>
                    <span className="material-symbols-outlined text-[16px]" aria-hidden="true">emergency</span>
                    {vm.shiftType}
                  </span>
                  {schedule.hasConflict && (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-error/20 bg-error-container px-3 py-1 text-xs font-semibold text-error">
                      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">warning</span>
                      Có xung đột
                    </span>
                  )}
                </div>
                <ShiftDetailInfo shift={vm} />
                <ShiftDetailTable shift={vm} />
              </>
            );
          })()}
        </div>
      ) : (
        <div className="flex h-32 items-center justify-center text-on-surface-variant">
          Không tìm thấy lịch trực.
        </div>
      )}
    </Modal>
  );
});
