import type { AllocationStat } from "@/types/schedule";

type StaffLoadTableProps = {
  loads: AllocationStat[];
  className?: string;
};

export function StaffLoadTable({ loads, className = "" }: StaffLoadTableProps) {
  return (
    <section className={`flex flex-col rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden ${className}`}>
      <div className="p-5 border-b border-outline-variant bg-surface-bright">
        <h3 className="font-title-lg text-on-surface">Thong ke phan bo (Tuan)</h3>
        <p className="text-label-sm text-on-surface-variant mt-1">
          Tai nguyen nhan su theo khoa
        </p>
      </div>

      <div className="p-4 flex flex-col gap-4">
        {loads.map((load) => (
          <article className="space-y-2" key={load.department}>
            <div className="flex items-center justify-between gap-4">
              <p className="font-label-md text-on-surface">{load.department}</p>
              <div className="flex items-center gap-3">
                <span className="text-[14px] font-bold text-primary">{load.percentage}%</span>
              </div>
            </div>
            <div className="w-full bg-surface-container-high rounded-full h-2 overflow-hidden">
              <div
                className="h-2 rounded-full bg-primary"
                style={{ width: `${load.percentage}%` }}
              />
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
