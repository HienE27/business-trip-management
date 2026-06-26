"use client";

import { memo, useMemo } from "react";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/Badge";
import type { MonthlyPanel, WorkflowContext, WorkflowStatus, WorkflowStepId, WorkflowStepView } from "./types";

const STATUS_CONFIG: Record<WorkflowStatus, { icon: string; bg: string; iconColor: string; line: string }> = {
  pending: { icon: "radio_button_unchecked", bg: "bg-surface-container-high", iconColor: "text-on-surface-variant", line: "bg-surface-variant" },
  active: { icon: "play_arrow", bg: "bg-primary", iconColor: "text-on-primary", line: "bg-primary/40" },
  completed: { icon: "check", bg: "bg-secondary-container", iconColor: "text-secondary", line: "bg-secondary/40" },
  error: { icon: "warning", bg: "bg-error-container", iconColor: "text-error", line: "bg-error/40" },
};

function buildSteps(context: WorkflowContext): WorkflowStepView[] {
  const hasConflicts = Boolean(context.conflictData?.hasConflicts);
  const hasConflictCheck = context.conflictData !== null;
  const hasSchedules = context.schedules.length > 0;
  const isPublished = context.selectedPeriod?.status === "PUBLISHED";
  const panelMap: Partial<Record<MonthlyPanel, WorkflowStepId>> = {
    conflicts: "conflicts",
    overview: "review",
    summary: "review",
    workload: "export",
  };
  const activeFromPanel = panelMap[context.selectedPanel];

  return [
    { id: "auto-schedule" as WorkflowStepId, title: "Auto", description: "Tự động", status: hasSchedules ? "completed" : "pending" },
    { id: "conflicts" as WorkflowStepId, title: "Xung đột", description: "Kiểm tra", status: context.checkingConflicts || activeFromPanel === "conflicts" ? "active" : hasConflicts ? "error" : hasConflictCheck ? "completed" : "pending" },
    { id: "review" as WorkflowStepId, title: "Rà soát", description: "Tổng hợp", status: activeFromPanel === "review" ? "active" : hasSchedules && !hasConflicts ? "completed" : "pending" },
    { id: "export" as WorkflowStepId, title: "Xuất", description: "Báo cáo", status: context.exporting ? "active" : isPublished ? "completed" : "pending" },
    { id: "publish" as WorkflowStepId, title: "Công bố", description: "Kỳ lịch", status: context.publishing ? "active" : isPublished ? "completed" : context.dryRunData != null && context.dryRunData.canPublish ? "completed" : hasConflicts ? "error" : "pending" },
    { id: "notify" as WorkflowStepId, title: "Thông báo", description: "Gửi", status: context.notifying ? "active" : context.notified ? "completed" : "pending" },
  ];
}

export type WorkflowStepperProps = WorkflowContext & {
  onStepSelect: (stepId: WorkflowStepId) => void;
  onExport?: () => void;
};

export const WorkflowStepper = memo(function WorkflowStepper(props: WorkflowStepperProps) {
  const router = useRouter();
  const steps = useMemo(() => buildSteps(props), [props]);
  const completedCount = steps.filter((s) => s.status === "completed").length;
  const progress = Math.round((completedCount / steps.length) * 100);

  const statusSummary = useMemo(() => {
    if (props.selectedPeriod?.status === "PUBLISHED") return { text: "Đã công bố", tone: "success" as const };
    if (Boolean(props.conflictData?.hasConflicts) || Boolean(props.dryRunData?.hasConflicts)) return { text: "Còn xung đột", tone: "error" as const };
    if (props.schedules.length > 0) return { text: "Sẵn sàng", tone: "info" as const };
    return { text: "Chưa xếp", tone: "neutral" as const };
  }, [props]);

  function handleClick(stepId: WorkflowStepId) {
    if (stepId === "auto-schedule") { router.push("/auto-scheduling"); return; }
    if (stepId === "export") { props.onExport?.(); return; }
    props.onStepSelect(stepId);
  }

  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-3 py-2 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-primary-fixed">
              <span className="material-symbols-outlined text-[14px] text-primary" aria-hidden="true">account_tree</span>
            </div>
            <span className="text-label-sm font-semibold text-on-surface truncate">Workflow</span>
            <span className="text-label-xs text-on-surface-variant">{completedCount}/{steps.length}</span>
          </div>
          <Badge tone={statusSummary.tone} size="sm">{statusSummary.text}</Badge>
        </div>
        <div className="mt-1.5 h-1 bg-surface-variant rounded-full overflow-hidden">
          <div className="h-full bg-primary rounded-full transition-all duration-500" style={{ width: `${progress}%` }} />
        </div>
      </div>

      {/* Horizontal step bar */}
      <div className="px-3 py-2.5">
        <div className="flex items-center">
          {steps.map((step, index) => {
            const status = STATUS_CONFIG[step.status];
            return (
              <div key={step.id} className="flex items-center min-w-0 first:ml-0 first:mr-auto last:mr-0 last:ml-auto mx-auto">
                {/* Step */}
                <button
                  type="button"
                  onClick={() => handleClick(step.id)}
                  className="flex flex-col items-center gap-0.5 group cursor-pointer"
                >
                  <div className={`w-7 h-7 rounded-full flex items-center justify-center ${status.bg} transition-all group-hover:scale-110`}>
                    <span className={`material-symbols-outlined ${status.iconColor}`} style={{ fontSize: "13px" }} aria-hidden="true">
                      {status.icon}
                    </span>
                  </div>
                  <span className="text-[9px] font-semibold text-on-surface-variant group-hover:text-primary transition-colors leading-tight text-center whitespace-nowrap">
                    {step.title}
                  </span>
                </button>
                {/* Connector line */}
                {index < steps.length - 1 && (
                  <div className={`w-6 h-0.5 mx-0.5 rounded-full ${status.line} flex-shrink-0`} />
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
});
