import type { Metric } from "@/types/schedule";
import { toneStyles } from "./tone-styles";

type MetricCardProps = {
  metric: Metric;
};

export function MetricCard({ metric }: MetricCardProps) {
  const toneClass = metric.tone
    ? toneStyles[metric.tone]
    : "border-slate-200 bg-white text-slate-950";

  return (
    <div className={`min-h-[116px] rounded-lg border p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)] ${toneClass}`}>
      <p className="text-xs font-medium uppercase leading-4 opacity-70">{metric.label}</p>
      <p className="mt-3 text-2xl font-semibold leading-8">{metric.value}</p>
      <p className="mt-1 text-sm leading-5 opacity-70">{metric.helper}</p>
    </div>
  );
}
