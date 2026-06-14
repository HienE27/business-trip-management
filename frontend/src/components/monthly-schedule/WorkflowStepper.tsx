"use client";

import { memo, useMemo } from "react";
import { useRouter } from "next/navigation";
import { SectionCard } from "@/components/ui/SectionCard";
import { WORKFLOW_STEPS } from "./constants";
import type { MonthlyPanel, WorkflowContext, WorkflowStatus, WorkflowStepId, WorkflowStepView } from "./types";

const STATUS_META: Record<WorkflowStatus, { label: string; icon: string; className: string; marker: string }> = {
  pending: {
    label: "Chờ xử lý",
    icon: "radio_button_unchecked",
    className: "border-outline-variant bg-surface hover:bg-surface-container-low",
    marker: "bg-surface-container-high text-on-surface-variant",
  },
  active: {
    label: "Đang xử lý",
    icon: "play_arrow",
    className: "border-primary bg-primary-fixed/40",
    marker: "bg-primary text-on-primary",
  },
  completed: {
    label: "Hoàn tất",
    icon: "check",
    className: "border-secondary/30 bg-secondary-container/40",
    marker: "bg-secondary-container text-on-secondary-container",
  },
  error: {
    label: "Lỗi",
    icon: "error",
    className: "border-error/30 bg-error-container/40",
    marker: "bg-error text-on-error",
  },
};

function buildWorkflowSteps(context: WorkflowContext): WorkflowStepView[] {
  const hasConflicts = Boolean(context.conflictData?.hasConflicts);
  const hasConflictCheck = context.conflictData !== null;
  const hasSchedules = context.schedules.length > 0;
  const isPublished = context.selectedPeriod?.status === "PUBLISHED";
  const activeFromPanel: Partial<Record<MonthlyPanel, WorkflowStepId>> = {
    conflicts: "conflicts",
    overview: "review",
    summary: "review",
  };
  const activeStep = activeFromPanel[context.selectedPanel];

  return WORKFLOW_STEPS.map((step) => {
    let status: WorkflowStatus = "pending";

    if (step.id === "auto-schedule") {
      status = hasSchedules ? "completed" : "pending";
    } else if (step.id === "conflicts") {
      status = context.checkingConflicts || activeStep === step.id ? "active" : hasConflicts ? "error" : hasConflictCheck ? "completed" : "pending";
    } else if (step.id === "review") {
      status = activeStep === step.id ? "active" : hasSchedules && !hasConflicts ? "completed" : "pending";
    } else if (step.id === "publish") {
      status = context.publishing ? "active" : isPublished ? "completed" : hasConflicts ? "error" : "pending";
    } else if (step.id === "notify") {
      status = context.notifying ? "active" : context.notified ? "completed" : "pending";
    }

    return {
      ...step,
      status,
      statusLabel: STATUS_META[status].label,
    };
  });
}

export type WorkflowStepperProps = WorkflowContext & {
  onStepSelect: (stepId: WorkflowStepId) => void;
};

export const WorkflowStepper = memo(function WorkflowStepper(props: WorkflowStepperProps) {
  const router = useRouter();
  const steps = useMemo(() => buildWorkflowSteps(props), [props]);
  const completedCount = steps.filter((step) => step.status === "completed").length;
  const progress = Math.round((completedCount / steps.length) * 100);

  function handleStepClick(stepId: WorkflowStepId) {
    if (stepId === "auto-schedule") {
      router.push("/auto-scheduling");
      return;
    }
    props.onStepSelect(stepId);
  }

  return (
    <SectionCard
      title="Workflow vận hành"
      description="Stepper thể hiện rõ trạng thái từng checkpoint trước khi công bố kỳ lịch."
    >
      <div className="space-y-4 p-5">
        <div>
          <div className="flex items-center justify-between text-label-sm text-on-surface-variant">
            <span>{completedCount}/{steps.length} bước hoàn tất</span>
            <span>{progress}%</span>
          </div>
          <div className="mt-2 h-2 rounded-full bg-surface-container-high" aria-hidden="true">
            <div className="h-2 rounded-full bg-primary transition-all" style={{ width: `${progress}%` }} />
          </div>
        </div>

        <ol className="space-y-3" aria-label="Quy trình lập lịch tháng">
          {steps.map((step, index) => {
            const meta = STATUS_META[step.status];
            const descriptionId = `workflow-${step.id}-description`;
            return (
              <li key={step.id}>
                <button
                  type="button"
                  onClick={() => handleStepClick(step.id)}
                  aria-current={step.status === "active" ? "step" : undefined}
                  aria-describedby={descriptionId}
                  className={`flex w-full items-start gap-3 rounded-lg border px-4 py-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${meta.className}`}
                >
                  <span
                    aria-label={`Bước ${index + 1}: ${step.statusLabel}`}
                    className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[12px] font-bold ${meta.marker}`}
                  >
                    {step.status === "completed" || step.status === "error" ? (
                      <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{meta.icon}</span>
                    ) : (
                      index + 1
                    )}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center justify-between gap-3">
                      <span className="text-label-md font-semibold text-on-surface">{step.title}</span>
                      <span className="rounded-full bg-surface-container-low px-2 py-0.5 text-[11px] font-semibold text-on-surface-variant">
                        {step.statusLabel}
                      </span>
                    </span>
                    <span id={descriptionId} className="mt-1 block text-body-sm leading-5 text-on-surface-variant">
                      {step.description}
                    </span>
                  </span>
                </button>
                {index < steps.length - 1 && (
                  <div className="ml-8 h-3 w-px bg-outline-variant" aria-hidden="true" />
                )}
              </li>
            );
          })}
        </ol>
      </div>
    </SectionCard>
  );
});
