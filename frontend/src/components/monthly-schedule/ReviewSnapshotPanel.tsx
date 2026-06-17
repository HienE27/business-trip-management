"use client";

import { memo } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import type { Schedule } from "@/types/api";

export type ReviewSnapshotPanelProps = {
  focusDate: string | null;
  schedules: Schedule[];
};

export const ReviewSnapshotPanel = memo(function ReviewSnapshotPanel({ focusDate, schedules }: ReviewSnapshotPanelProps) {
  return (
    <div className="rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low">
        <h3 className="text-[15px] font-semibold text-on-surface leading-tight">
          Chi tiết ngày
        </h3>
        <p className="mt-0.5 text-[11px] text-on-surface-variant leading-tight">
          {focusDate
            ? `Focus ${new Date(focusDate).toLocaleDateString("vi-VN")}`
            : "Chọn ngày trên lịch để xem chi tiết."}
        </p>
      </div>

      <div className="divide-y divide-outline-variant/50 max-h-72 overflow-y-auto">
        {schedules.length === 0 ? (
          <EmptyState
            className="py-8"
            icon="event_busy"
            title="Không có lịch tại ngày đang focus"
            description="Chọn cảnh báo hoặc click ngày trên lịch."
          />
        ) : (
          schedules.slice(0, 8).map((schedule) => (
            <div key={schedule.id} className="flex items-start justify-between gap-3 px-4 py-2.5">
              <div className="min-w-0">
                <p className="text-[13px] font-semibold text-on-surface leading-tight truncate">
                  {schedule.staff.fullName}
                </p>
                <p className="mt-0.5 text-[11px] text-on-surface-variant leading-tight">
                  {new Date(schedule.workDate).toLocaleDateString("vi-VN")} · {schedule.shiftType.name}
                </p>
              </div>
              {schedule.hasConflict && (
                <span className="rounded-full bg-error-container px-2 py-0.5 text-[10px] font-semibold text-on-error-container whitespace-nowrap leading-tight">
                  Xung đột
                </span>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
});
