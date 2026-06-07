import type { WorkflowStep } from "@/types/schedule";

type AutoSchedulingPanelProps = {
  steps: WorkflowStep[];
  className?: string;
};

export function AutoSchedulingPanel({ steps, className = "" }: AutoSchedulingPanelProps) {
  return (
    <section className={`flex flex-col rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-outline-variant bg-surface-bright">
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className="material-symbols-outlined text-primary text-[20px]">
            auto_fix_high
          </span>
          <h3 className="font-title-lg text-on-surface">Tu dong xep lich</h3>
        </div>
        <button className="px-4 py-2 bg-primary text-on-primary rounded-lg text-[14px] font-bold flex items-center gap-2 hover:opacity-90 transition-colors shadow-[0_1px_3px_0_rgba(0,0,0,0.1)]">
          <span aria-hidden="true" className="material-symbols-outlined text-[18px]">play_arrow</span>
          KHOI DONG
        </button>
      </div>

      {/* Steps */}
      <div className="flex flex-col divide-y divide-outline-variant p-4">
        {steps.map((step, index) => (
          <article className="flex items-start gap-4 py-4 first:pt-0" key={step.id}>
            {/* Step number */}
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-container text-on-primary-container font-bold text-sm">
              {index + 1}
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <p className="font-label-md text-on-surface">{step.title}</p>
              {step.description && (
                <p className="mt-1 font-body-sm text-on-surface-variant leading-relaxed">
                  {step.description}
                </p>
              )}
            </div>

            {/* Status icon */}
            <div className="shrink-0">
              <span
                aria-hidden="true"
                className={`material-symbols-outlined text-[18px] ${
                  step.status === "completed"
                    ? "text-secondary fill"
                    : step.status === "active"
                    ? "text-primary"
                    : "text-outline"
                }`}
              >
                {step.status === "completed" ? "check_circle" : step.status === "active" ? "radio_button_checked" : "radio_button_unchecked"}
              </span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
