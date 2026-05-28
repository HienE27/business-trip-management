import type { StaffLoad } from "@/types/schedule";

type StaffLoadTableProps = {
  loads: StaffLoad[];
};

export function StaffLoadTable({ loads }: StaffLoadTableProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold">Cân bằng tải nhân sự</h2>
        <p className="text-xs text-slate-500">Top 5 / 20</p>
      </div>
      <div className="space-y-3">
        {loads.map((load) => {
          const total = load.duty24 + load.allDay + load.clinics;

          return (
            <div key={load.name}>
              <div className="mb-1 flex items-center justify-between text-xs">
                <span className="font-medium">{load.name}</span>
                <span className="text-slate-500">{total} ngày</span>
              </div>
              <div className="flex h-2 overflow-hidden rounded-full bg-slate-100">
                <div className="bg-blue-500" style={{ width: `${load.duty24 * 6}%` }} />
                <div className="bg-emerald-500" style={{ width: `${load.allDay * 6}%` }} />
                <div className="bg-amber-500" style={{ width: `${load.clinics * 6}%` }} />
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
