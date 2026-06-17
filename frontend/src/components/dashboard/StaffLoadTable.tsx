import type { AllocationStat } from "@/types/schedule";

type StaffLoadTableProps = {
  loads: AllocationStat[];
  className?: string;
};

export function StaffLoadTable({ loads, className = "" }: StaffLoadTableProps) {
  return (
    <section className={`flex flex-col rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden ${className}`}>
      <div className="p-5 border-b border-outline-variant bg-surface-container-low">
        <h3 className="font-title-lg text-on-surface">Thống kê phân bổ (Tuần)</h3>
        <p className="text-label-sm text-on-surface-variant mt-1">
          Tài nguyên nhân sự theo khoa
        </p>
      </div>

      <div className="p-4 flex flex-col gap-4">
        {loads.map((load) => (
          <article className="space-y-2" key={load.department}>
            <div className="flex items-center justify-between gap-4">
              <p className="font-label-md text-on-surface">{load.department}</p>
              <div className="flex items-center gap-3">
                <span className="text-label-md font-bold text-primary">{load.percentage}%</span>
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
