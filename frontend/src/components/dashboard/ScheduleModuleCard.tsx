import type { ScheduleModule } from "@/types/schedule";

type ScheduleModuleCardProps = {
  module: ScheduleModule;
};

function getAccentColor(code: ScheduleModule["code"]) {
  if (code === "M02") return "border-l-primary";
  if (code === "M03") return "border-l-secondary";
  if (code === "M04") return "border-l-tertiary";
  if (code === "M05") return "border-l-expert";
  return "border-l-primary";
}

export function ScheduleModuleCard({ module }: ScheduleModuleCardProps) {
  const accent = getAccentColor(module.code);

  return (
    <article
      className={`group relative flex min-h-[180px] flex-col rounded-lg border-t border-r border-b border-outline-variant border-l-4 bg-surface-container-lowest p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] hover:bg-surface-container-low transition-colors ${accent}`}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-label-sm text-on-surface-variant uppercase tracking-wider">
            {module.code}
          </p>
          <h3 className="mt-2 font-title-lg text-on-surface">
            {module.title}
          </h3>
        </div>
        <span className="rounded-full border border-outline bg-surface-container-low px-3 py-1 text-label-sm text-on-surface-variant">
          {module.priority}
        </span>
      </div>

      {/* Description */}
      <p className="mt-3 flex-1 font-body-sm text-on-surface-variant leading-relaxed">
        {module.description}
      </p>

      {/* Progress bar */}
      <div className="mt-5">
        <div className="h-2 w-full rounded-full bg-surface-variant">
          <div
            className="h-2 rounded-full bg-primary"
            style={{ width: `${module.progress}%` }}
          />
        </div>
        <p className="mt-2 text-label-sm text-on-surface-variant">
          {module.progress}% du lieu hop le
        </p>
      </div>
    </article>
  );
}
