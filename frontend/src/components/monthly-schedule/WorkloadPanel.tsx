"use client";

import { memo } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { SectionCard } from "@/components/ui/SectionCard";
import type { WorkloadRow } from "./types";

export type WorkloadPanelProps = {
  workloadSnapshot: WorkloadRow[];
  activeStaffCount: number;
};

export const WorkloadPanel = memo(function WorkloadPanel({ workloadSnapshot, activeStaffCount }: WorkloadPanelProps) {
  const maxShifts = Math.max(1, ...workloadSnapshot.map((row) => row.shifts));

  return (
    <SectionCard
      title="Top workload"
      description={`Ảnh chụp nhanh tải công việc của ${activeStaffCount} nhân sự đang hoạt động.`}
    >
      <div className="divide-y divide-outline-variant">
        {workloadSnapshot.length === 0 ? (
          <EmptyState className="py-10" icon="groups" title="Chưa đủ dữ liệu phân bổ" description="Hệ thống cần có lịch được gán cho loại lịch đang xem để tính workload." />
        ) : (
          workloadSnapshot.map((row) => (
            <div key={row.staffId} className="px-4 py-3">
              <div className="flex items-center justify-between gap-3">
                <p className="text-label-md font-semibold text-on-surface">{row.staffName}</p>
                <span className="text-label-sm text-on-surface-variant">{row.shifts} ca</span>
              </div>
              <div className="mt-2 h-2 rounded-full bg-surface-container-high" aria-hidden="true">
                <div className="h-2 rounded-full bg-primary" style={{ width: `${Math.max(8, Math.min(100, (row.shifts / maxShifts) * 100))}%` }} />
              </div>
            </div>
          ))
        )}
      </div>
    </SectionCard>
  );
});
