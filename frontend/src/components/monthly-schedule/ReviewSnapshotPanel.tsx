"use client";

import { memo } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Badge } from "@/components/ui/Badge";
import type { Schedule } from "@/types/api";

export type ReviewSnapshotPanelProps = {
  focusDate: string | null;
  schedules: Schedule[];
};

export const ReviewSnapshotPanel = memo(function ReviewSnapshotPanel({ focusDate, schedules }: ReviewSnapshotPanelProps) {
  const scheduleCount = schedules.length;
  
  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-4 py-4 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-fixed">
            <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">event_note</span>
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="text-title-sm font-semibold text-on-surface">Chi tiết ngày</h3>
              {scheduleCount > 0 && (
                <Badge tone="info" size="sm">{scheduleCount}</Badge>
              )}
            </div>
            <p className="mt-0.5 text-label-xs text-on-surface-variant">
              {focusDate
                ? `Focus ${new Date(focusDate).toLocaleDateString("vi-VN", { weekday: "short", day: "numeric", month: "short" })}`
                : "Chọn ngày trên lịch để xem chi tiết."}
            </p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="divide-y divide-outline-variant/50 max-h-80 overflow-y-auto">
        {schedules.length === 0 ? (
          <div className="py-8">
            <EmptyState
              className="py-6"
              icon="event_busy"
              title="Không có lịch tại ngày đang focus"
              description="Chọn cảnh báo hoặc click ngày trên lịch."
              size="compact"
            />
          </div>
        ) : (
          schedules.slice(0, 10).map((schedule) => (
            <div key={schedule.id} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-surface-container-low transition-colors">
              <div className="min-w-0 flex-1">
                <p className="text-label-md font-medium text-on-surface truncate">
                  {schedule.staff.fullName}
                </p>
                <p className="mt-0.5 text-label-xs text-on-surface-variant">
                  {new Date(schedule.workDate).toLocaleDateString("vi-VN")} · {schedule.shiftType.name}
                </p>
              </div>
              {schedule.hasConflict && (
                <Badge tone="error" size="sm">
                  <span className="material-symbols-outlined text-[10px]">warning</span>
                  Xung đột
                </Badge>
              )}
            </div>
          ))
        )}
      </div>
      
      {schedules.length > 10 && (
        <div className="px-4 py-2 border-t border-outline-variant bg-surface-container-low">
          <p className="text-label-xs text-on-surface-variant text-center">
            Hiển thị 10 / {schedules.length} lịch
          </p>
        </div>
      )}
    </div>
  );
});
