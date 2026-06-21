"use client";

import { memo, useMemo } from "react";
import { useRouter } from "next/navigation";
import type { MonthlyPanel, WorkflowContext, WorkflowStatus, WorkflowStepId, WorkflowStepView } from "./types";
import type { ConflictCheckResponse, PublishDryRunResponse } from "@/types/api";

const STATUS_META: Record<WorkflowStatus, { label: string; icon: string; dot: string; line: string }> = {
  pending:  { label: "Chờ",     icon: "radio_button_unchecked", dot: "bg-surface-variant",       line: "bg-surface-variant" },
  active:   { label: "Đang xử lý", icon: "play_arrow",         dot: "bg-primary",              line: "bg-primary/30" },
  completed: { label: "Hoàn tất", icon: "check",               dot: "bg-secondary",            line: "bg-secondary/40" },
  error:    { label: "Lỗi",     icon: "error",                 dot: "bg-error",                line: "bg-error/30" },
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
    { id: "auto-schedule" as WorkflowStepId, title: "Auto", description: "Tự động xếp lịch", status: hasSchedules ? "completed" : "pending", statusLabel: "" },
    { id: "conflicts" as WorkflowStepId, title: "Xung đột", description: "Kiểm tra xung đột", status: context.checkingConflicts || activeFromPanel === "conflicts" ? "active" : hasConflicts ? "error" : hasConflictCheck ? "completed" : "pending", statusLabel: "" },
    { id: "review" as WorkflowStepId, title: "Rà soát", description: "Tổng hợp & báo cáo", status: activeFromPanel === "review" ? "active" : hasSchedules && !hasConflicts ? "completed" : "pending", statusLabel: "" },
    { id: "export" as WorkflowStepId, title: "Xuất báo cáo", description: "Xuất Excel / PDF", status: context.exporting ? "active" : isPublished ? "completed" : "pending", statusLabel: "" },
    { id: "publish" as WorkflowStepId, title: "Công bố", description: "Công bố kỳ lịch", status: context.publishing ? "active" : isPublished ? "completed" : context.dryRunData != null && context.dryRunData.canPublish ? "completed" : hasConflicts ? "error" : "pending", statusLabel: "" },
    { id: "notify" as WorkflowStepId, title: "Thông báo", description: "Gửi thông báo", status: context.notifying ? "active" : context.notified ? "completed" : "pending", statusLabel: "" },
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

  function handleClick(stepId: WorkflowStepId) {
    if (stepId === "auto-schedule") { router.push("/auto-scheduling"); return; }
    if (stepId === "export") { props.onExport?.(); return; }
    props.onStepSelect(stepId);
  }

  return (
    <div className="bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-4 py-2.5 border-b border-outline-variant flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-primary text-[18px]">account_tree</span>
          <span className="text-label-md font-semibold text-on-surface">Workflow</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-label-sm text-on-surface-variant">{completedCount}/{steps.length}</span>
          <div className="w-16 h-1.5 bg-surface-variant rounded-full overflow-hidden">
            <div className="h-1.5 bg-primary rounded-full transition-all" style={{ width: `${progress}%` }} />
          </div>
        </div>
      </div>

      {/* Compact step bar */}
      <div className="px-4 py-3">
        <div className="flex items-center gap-0">
          {steps.map((step, index) => {
            const meta = STATUS_META[step.status];
            const isClickable = step.id !== "auto-schedule" || true;
            return (
              <div key={step.id} className="flex items-center flex-1 last:flex-none">
                {/* Step dot */}
                <button
                  type="button"
                  aria-label={`${step.title} — ${step.description} (${meta.label})`}
                  onClick={() => handleClick(step.id)}
                  title={`${step.title} — ${step.description} (${meta.label})`}
                  className={`relative flex flex-col items-center gap-1 group ${isClickable ? "cursor-pointer" : "cursor-default"}`}
                >
                  <div className={`w-6 h-6 rounded-full flex items-center justify-center ${meta.dot} transition-all group-hover:scale-110`}>
                    <span className="material-symbols-outlined text-[var(--color-on-primary)]" style={{ fontSize: "13px" }}>
                      {meta.icon}
                    </span>
                  </div>
                  <span className="text-[10px] font-semibold text-on-surface-variant group-hover:text-primary transition-colors whitespace-nowrap leading-tight">
                    {step.title}
                  </span>
                </button>
                {/* Connector line */}
                {index < steps.length - 1 && (
                  <div className={`flex-1 h-0.5 mx-1 mb-3 rounded-full ${meta.line} transition-all`} />
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Status summary */}
      <div className="px-4 pb-3">
        <div className="bg-surface-container-low rounded-lg p-2.5">
          <p className="text-[11px] text-on-surface-variant leading-relaxed">
            {isPublished(props) ? (
              <span className="text-secondary font-semibold">Kỳ lịch đã công bố.</span>
            ) : hasConflicts(props) ? (
              <span className="text-error font-semibold">Còn xung đột chưa xử lý.</span>
            ) : hasSchedules(props) ? (
              <span className="text-primary font-semibold">Sẵn sàng công bố.</span>
            ) : (
              <span>Chưa xếp lịch. Chạy auto-scheduling trước.</span>
            )}
          </p>
        </div>
      </div>
    </div>
  );
});

function isPublished(props: WorkflowContext) { return props.selectedPeriod?.status === "PUBLISHED"; }
function hasConflicts(props: WorkflowContext) {
  const c = props.conflictData;
  const d = props.dryRunData;
  return Boolean(c?.hasConflicts || d?.hasConflicts);
}
function hasSchedules(props: WorkflowContext) { return props.schedules.length > 0; }
