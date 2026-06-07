import type { Metric } from "@/types/schedule";

type MetricCardProps = {
  metric: Metric;
};

function getIconStyle(tone: Metric["tone"]) {
  if (tone === "warning") return "bg-error-container text-error";
  if (tone === "compLeave") return "bg-secondary-container text-secondary";
  if (tone === "allDay") return "bg-tertiary-fixed text-tertiary";
  if (tone === "serviceClinic") return "bg-primary-fixed text-primary";
  if (tone === "expertClinic") return "bg-primary-fixed text-primary";
  return "bg-primary-fixed text-primary";
}

function getCornerStyle(tone: Metric["tone"]) {
  if (tone === "warning") return "bg-error/5 text-error";
  if (tone === "compLeave") return "bg-secondary/5 text-secondary";
  if (tone === "allDay") return "bg-tertiary/5 text-tertiary";
  if (tone === "serviceClinic") return "bg-primary/5 text-primary";
  if (tone === "expertClinic") return "bg-primary/5 text-primary";
  return "bg-primary/5 text-primary";
}

function getHelperStyle(trend: Metric["trend"]) {
  if (trend === "alert") return "text-error font-bold";
  if (trend === "neutral") return "text-on-surface-variant";
  if (trend === "stable") return "text-secondary font-semibold";
  if (trend === "up") return "bg-secondary-container text-secondary font-semibold";
  return "bg-secondary-container text-secondary font-semibold";
}

function getAccentColor(tone: Metric["tone"]) {
  if (tone === "warning") return "border-l-4 border-l-error";
  if (tone === "compLeave") return "border-l-4 border-l-secondary";
  if (tone === "allDay") return "border-l-4 border-l-tertiary";
  if (tone === "serviceClinic") return "border-l-4 border-l-primary";
  if (tone === "expertClinic") return "border-l-4 border-l-expert";
  return "border-l-4 border-l-primary";
}

export function MetricCard({ metric }: MetricCardProps) {
  const iconStyle = getIconStyle(metric.tone);
  const cornerStyle = getCornerStyle(metric.tone);
  const helperStyle = getHelperStyle(metric.trend);
  const accent = getAccentColor(metric.tone);

  return (
    <div
      className={`relative flex flex-col justify-between rounded-xl border-t border-r border-b border-outline-variant bg-surface-container-lowest p-4 shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] hover:bg-surface-container-low transition-colors gap-2 ${accent}`}
    >
      {/* Decorative corner accent */}
      <div className={`absolute right-0 top-0 w-16 h-16 rounded-bl-full flex items-start justify-end p-3 ${cornerStyle}`}>
        <span aria-hidden="true" className="material-symbols-outlined text-[20px] opacity-30">
          {metric.icon || "event_available"}
        </span>
      </div>

      {/* Label */}
      <h3 className="text-label-sm text-on-surface-variant uppercase tracking-wider">
        {metric.label}
      </h3>

      {/* Value + helper */}
      <div className="flex items-baseline gap-3">
        <span className="text-display-lg text-on-surface">
          {metric.value}
        </span>
        {metric.helper && (
          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] ${helperStyle}`}>
            {metric.trend === "up" ? (
              <span aria-hidden="true" className="material-symbols-outlined text-[12px]">
                arrow_upward
              </span>
            ) : null}
            {metric.helper}
          </span>
        )}
      </div>

      {/* Icon (bottom-right corner in Stitch) */}
      <div
        aria-hidden="true"
        className={`absolute right-3 bottom-3 flex h-9 w-9 items-center justify-center rounded-lg ${iconStyle}`}
      >
        <span className="material-symbols-outlined text-[18px]">
          {metric.icon || "event_available"}
        </span>
      </div>
    </div>
  );
}
