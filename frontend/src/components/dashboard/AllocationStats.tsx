import type { AllocationStat } from "@/types/schedule";

type AllocationStatsProps = {
  stats: AllocationStat[];
  className?: string;
};

const BAR_COLORS: Record<string, string> = {
  primary: "bg-primary",
  secondary: "bg-secondary",
  error: "bg-error",
};

export function AllocationStats({ stats, className = "" }: AllocationStatsProps) {
  return (
    <section
      className={`rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-4 ${className}`}
    >
      <h3 className="font-title-lg text-on-surface mb-4">Thống kê phân bổ (Tuần)</h3>
      <div className="flex flex-col gap-4">
        {stats.map((stat) => (
          <div key={stat.department}>
            <div className="flex justify-between font-label-sm text-label-sm text-on-surface mb-1">
              <span>{stat.department}</span>
              <span>{stat.percentage}%</span>
            </div>
            <div className="w-full bg-surface-container-high rounded-full h-2">
              <div
                className={`h-2 rounded-full ${BAR_COLORS[stat.color] || "bg-primary"}`}
                style={{ width: `${stat.percentage}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
