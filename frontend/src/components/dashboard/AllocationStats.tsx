import type { AllocationStat } from "@/types/schedule";

type AllocationStatsProps = {
  stats: AllocationStat[];
  className?: string;
};

export function AllocationStats({ stats, className = "" }: AllocationStatsProps) {
  return (
    <section className={`flex flex-col bg-surface-container-lowest border border-outline-variant rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-4 ${className}`}>
      <h3 className="text-title-lg text-on-surface mb-4">Thong ke phan bo (Tuan)</h3>
      <div className="flex flex-col gap-4">
        {stats.map((stat) => (
          <div key={stat.department}>
            <div className="flex justify-between font-label-sm text-label-sm text-on-surface mb-1.5">
              <span>{stat.department}</span>
              <span className="font-semibold">{stat.percentage}%</span>
            </div>
            <div className="w-full bg-surface-container rounded-full h-2 overflow-hidden">
              <div
                className="h-2 rounded-full bg-primary transition-all duration-500"
                style={{ width: `${stat.percentage}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
