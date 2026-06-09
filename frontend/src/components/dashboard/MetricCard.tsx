import type { Metric } from "@/types/schedule";

type MetricCardProps = {
  metric: Metric;
};

const TONE_STYLES: Record<string, {
  cornerBg: string;
  labelColor: string;
}> = {
  warning:       { cornerBg: "bg-error/5",      labelColor: "text-error" },
  duty24:        { cornerBg: "bg-primary/5",     labelColor: "text-primary" },
  allDay:        { cornerBg: "bg-secondary/5",   labelColor: "text-secondary" },
  serviceClinic: { cornerBg: "bg-tertiary/5",    labelColor: "text-tertiary" },
  expertClinic:  { cornerBg: "bg-expert/5",      labelColor: "text-expert" },
  neutral:       { cornerBg: "bg-primary/5",     labelColor: "text-primary" },
};

const DEFAULT_STYLE = TONE_STYLES.neutral;

export function MetricCard({ metric }: MetricCardProps) {
  const tone = metric.tone || "neutral";
  const style = TONE_STYLES[tone] || DEFAULT_STYLE;

  return (
    <div className="bg-white rounded-xl border border-outline-variant shadow-[0_1px_3px_0_rgba(0,0,0,0.05),0_1px_2px_0_rgba(0,0,0,0.1)] p-4 relative overflow-hidden">
      {/* Decorative corner — subtle primary tint */}
      <div className={`absolute right-0 top-0 w-16 h-16 rounded-bl-full flex items-start justify-end p-3 ${style.cornerBg}`}>
        <span aria-hidden="true" className="material-symbols-outlined text-[18px] text-primary opacity-40">
          {metric.icon || "event_available"}
        </span>
      </div>

      {/* Label */}
      <span className={`text-label-sm ${style.labelColor} uppercase tracking-wider font-semibold`}>
        {metric.label}
      </span>

      {/* Value */}
      <span className="text-display-lg text-on-surface font-bold leading-tight">
        {metric.value}
      </span>
    </div>
  );
}
