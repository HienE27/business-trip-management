"use client";

import { memo, useMemo } from "react";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/Badge";
import type { MonthlyPanel, WorkflowContext, WorkflowStatus, WorkflowStepId, WorkflowStepView } from "./types";

/** Icon duy nhất cho mỗi bước — giúp nhận diện nhanh. */
const STEP_ICONS: Record<WorkflowStepId, string> = {
  "auto-schedule": "auto_mode",
  conflicts: "find_in_page",
  review: "planner_review",
  export: "download",
  publish: "rocket_launch",
  notify: "notifications_active",
};

/** Tailwind class cho icon theo status. */
const STATUS_ICON_CONFIG: Record<WorkflowStatus, { bg: string; iconColor: string; ring: string }> = {
  pending: {
    bg: "bg-surface-container-high",
    iconColor: "text-on-surface-variant",
    ring: "ring-1 ring-outline-variant",
  },
  active: {
    bg: "bg-blue-100",
    iconColor: "text-blue-800",
    ring: "ring-2 ring-blue-30040",
  },
  completed: {
    bg: "bg-emerald-100",
    iconColor: "text-emerald-800",
    ring: "ring-1 ring-emerald-200",
  },
  error: {
    bg: "bg-red-100",
    iconColor: "text-red-800",
    ring: "ring-1 ring-red-200",
  },
};

/** Tailwind class cho indicator dot phụ. */
const STATUS_DOT: Record<WorkflowStatus, string> = {
  pending: "bg-surface-variant",
  active: "bg-blue-100 animate-pulse",
  completed: "bg-emerald-500",
  error: "bg-red-500",
};

/** Tailwind class cho connector line giữa các bước. */
const STATUS_LINE: Record<WorkflowStatus, string> = {
  pending: "bg-surface-variant",
  active: "bg-blue-100/40",
  completed: "bg-emerald-500",
  error: "bg-red-500",
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
    { id: "auto-schedule" as WorkflowStepId, title: "Auto", description: "Tự động", status: hasSchedules ? "completed" : "pending", statusLabel: hasSchedules ? "Hoàn tất" : "Chờ" },
    { id: "conflicts" as WorkflowStepId, title: "Xung đột", description: "Kiểm tra", status: context.checkingConflicts || activeFromPanel === "conflicts" ? "active" : hasConflicts ? "error" : hasConflictCheck ? "completed" : "pending", statusLabel: context.checkingConflicts || activeFromPanel === "conflicts" ? "Đang kiểm tra" : hasConflicts ? "Có xung đột" : hasConflictCheck ? "Đã kiểm tra" : "Chờ" },
    { id: "review" as WorkflowStepId, title: "Rà soát", description: "Tổng hợp", status: activeFromPanel === "review" ? "active" : isPublished ? "completed" : hasSchedules && !hasConflicts ? "completed" : "pending", statusLabel: activeFromPanel === "review" ? "Đang rà soát" : isPublished ? "Đã công bố" : hasSchedules && !hasConflicts ? "Đã rà soát" : "Chờ" },
    { id: "export" as WorkflowStepId, title: "Xuất", description: "Báo cáo", status: context.exporting ? "active" : isPublished ? "completed" : "pending", statusLabel: context.exporting ? "Đang xuất" : isPublished ? "Đã xuất" : "Chờ" },
    { id: "publish" as WorkflowStepId, title: "Công bố", description: "Kỳ lịch", status: context.publishing ? "active" : isPublished ? "completed" : context.dryRunData != null && context.dryRunData.canPublish ? "completed" : hasConflicts ? "error" : "pending", statusLabel: context.publishing ? "Đang công bố" : isPublished ? "Đã công bố" : context.dryRunData != null && context.dryRunData.canPublish ? "Sẵn sàng" : hasConflicts ? "Cần xử lý" : "Chờ" },
    { id: "notify" as WorkflowStepId, title: "Thông báo", description: "Gửi", status: context.notifying ? "active" : context.notified ? "completed" : "pending", statusLabel: context.notifying ? "Đang gửi" : context.notified ? "Đã gửi" : "Chờ" },
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
  const errorCount = steps.filter((s) => s.status === "error").length;
  const progress = Math.round((completedCount / steps.length) * 100);

  const statusSummary = useMemo(() => {
    if (props.selectedPeriod?.status === "PUBLISHED") return { text: "Đã công bố", tone: "success" as const };
    if (Boolean(props.conflictData?.hasConflicts) || Boolean(props.dryRunData?.hasConflicts)) return { text: "Còn xung đột", tone: "error" as const };
    if (props.schedules.length > 0) return { text: "Sẵn sàng", tone: "info" as const };
    return { text: "Chưa xếp", tone: "neutral" as const };
  }, [props]);

  function handleClick(stepId: WorkflowStepId) {
    if (stepId === "auto-schedule") {
      const periodId = props.selectedPeriod?.id;
      const target = periodId
        ? `/auto-scheduling?periodId=${periodId}`
        : "/auto-scheduling";
      router.push(target);
      return;
    }
    if (stepId === "export") { props.onExport?.(); return; }
    props.onStepSelect(stepId);
  }

  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-3 py-2 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-blue-100">
              <span className="material-symbols-outlined text-[14px] text-blue-800" aria-hidden="true">account_tree</span>
            </div>
            <span className="text-label-sm font-semibold text-on-surface truncate">Workflow</span>
            {errorCount > 0 && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-red-100 text-[10px] font-bold text-red-800">
                <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                {errorCount}
              </span>
            )}
          </div>
          <Badge tone={statusSummary.tone} size="sm">{statusSummary.text}</Badge>
        </div>
        <div className="mt-1.5 h-1 bg-surface-variant rounded-full overflow-hidden">
          <div className="h-full bg-blue-100 rounded-full transition-all duration-500" style={{ width: `${progress}%` }} />
        </div>
      </div>

      {/* Horizontal step bar */}
      <div className="px-3 py-3 overflow-x-auto">
        <div className="flex items-center min-w-max">
          {steps.map((step, index) => {
            const cfg = STATUS_ICON_CONFIG[step.status];
            const isActive = step.status === "active";
            const isError = step.status === "error";
            const isCompleted = step.status === "completed";
            const stepIcon = STEP_ICONS[step.id];
            // Connector: use the NEXT step's status to decide color
            const nextStep = steps[index + 1];
            const nextStatus = nextStep?.status ?? "pending";
            const connectorLine = STATUS_LINE[nextStatus];

            return (
              <div key={step.id} className="flex items-center">
                {/* Step */}
                <button
                  type="button"
                  onClick={() => handleClick(step.id)}
                  aria-label={`${step.title} — ${step.statusLabel}`}
                  aria-current={isActive ? "step" : undefined}
                  title={`${step.description}: ${step.statusLabel}`}
                  className="flex flex-col items-center gap-1 group cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1 rounded"
                >
                  {/* Main icon circle */}
                  <div className={`relative flex items-center justify-center w-9 h-9 rounded-full ${cfg.bg} ${cfg.ring} transition-all group-hover:scale-110 group-active:scale-95`}>
                    {/* Step number — hidden when active/completed/error */}
                    {step.status === "pending" && (
                      <span className="absolute -top-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-surface-container-highest border border-outline flex items-center justify-center">
                        <span className="text-[8px] font-bold text-on-surface-variant leading-none">{index + 1}</span>
                      </span>
                    )}
                    {/* Pulse ring for active */}
                    {isActive && (
                      <span className="absolute inset-0 rounded-full animate-ping opacity-30 bg-blue-100" />
                    )}
                    <span className={`material-symbols-outlined ${cfg.iconColor} ${isActive || isCompleted || isError ? "" : "opacity-50"}`} style={{ fontSize: "16px" }} aria-hidden="true">
                      {stepIcon}
                    </span>
                    {/* Status dot indicator */}
                    <span className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-surface-container-lowest ${STATUS_DOT[step.status]}`} />
                  </div>

                  {/* Step label */}
                  <div className="flex flex-col items-center gap-0.5">
                    <span className={`text-[10px] font-bold leading-tight text-center whitespace-nowrap transition-colors ${
                      isActive ? "text-blue-800" : isError ? "text-red-800" : isCompleted ? "text-emerald-800" : "text-on-surface-variant group-hover:text-on-surface"
                    }`}>
                      {step.title}
                    </span>
                    <span className={`text-[9px] leading-tight text-center whitespace-nowrap ${
                      isActive ? "text-blue-800/70" : isError ? "text-red-800/70" : isCompleted ? "text-emerald-800/70" : "text-outline"
                    }`}>
                      {step.statusLabel}
                    </span>
                  </div>
                </button>

                {/* Connector line */}
                {index < steps.length - 1 && (
                  <div className={`mx-1 h-0.5 w-7 rounded-full flex-shrink-0 transition-colors ${connectorLine}`} />
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
});
