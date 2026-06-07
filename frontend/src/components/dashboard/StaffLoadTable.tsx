import type { StaffLoad } from "@/types/schedule";

type StaffLoadTableProps = {
  loads: StaffLoad[];
  className?: string;
};

export function StaffLoadTable({ loads, className = "" }: StaffLoadTableProps) {
  return (
    <section className={`flex flex-col rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden ${className}`}>
      {/* Header */}
      <div className="p-5 border-b border-outline-variant bg-surface-bright">
        <h3 className="font-title-lg text-on-surface">Thong ke phan bo (Tuan)</h3>
        <p className="text-label-sm text-on-surface-variant mt-1">
          Tai nguyen nhan su theo khoa
        </p>
      </div>

      {/* Table */}
      <div className="p-4 flex flex-col gap-4">
        {loads.map((load) => (
          <article className="space-y-2" key={load.name}>
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-container text-on-primary-container font-bold text-xs">
                  {load.name.charAt(0)}
                </div>
                <div>
                  <p className="font-label-md text-on-surface">{load.name}</p>
                  <p className="text-label-sm text-on-surface-variant">{load.department}</p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-[14px] text-on-surface-variant">
                  {load.days} ca
                </span>
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
