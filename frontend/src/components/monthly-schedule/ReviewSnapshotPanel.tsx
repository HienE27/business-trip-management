"use client";

import { memo } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { SectionCard } from "@/components/ui/SectionCard";
import type { Schedule } from "@/types/api";

export type ReviewSnapshotPanelProps = {
  focusDate: string | null;
  schedules: Schedule[];
};

export const ReviewSnapshotPanel = memo(function ReviewSnapshotPanel({ focusDate, schedules }: ReviewSnapshotPanelProps) {
  return (
    <SectionCard
      title="Review snapshot"
      description={focusDate ? `Đang focus ${new Date(focusDate).toLocaleDateString("vi-VN")}` : "Danh sách nhanh để review lịch và phân công tải cao."}
    >
      <div className="divide-y divide-outline-variant">
        {schedules.length === 0 ? (
          <EmptyState
            className="py-10"
            icon="event_busy"
            title="Không có lịch tại ngày đang focus"
            description="Chọn một cảnh báo hoặc click ngày trên calendar để xem bản review liên quan."
          />
        ) : (
          schedules.slice(0, 8).map((schedule) => (
            <div key={schedule.id} className="flex items-start justify-between gap-3 px-4 py-3">
              <div>
                <p className="text-label-md font-semibold text-on-surface">{schedule.staff.fullName}</p>
                <p className="mt-1 text-body-sm text-on-surface-variant">
                  {new Date(schedule.workDate).toLocaleDateString("vi-VN")} · {schedule.shiftType.name}
                </p>
              </div>
              {schedule.hasConflict && (
                <span className="rounded-full bg-error-container px-3 py-1 text-[11px] font-semibold text-on-error-container">
                  Xung đột
                </span>
              )}
            </div>
          ))
        )}
      </div>
    </SectionCard>
  );
});
