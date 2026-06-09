import type { StaffScheduleRow } from "@/types/schedule";
import { toneStyles } from "./tone-styles";

type ScheduleMatrixProps = {
  staff: string[];
  rows: StaffScheduleRow[];
};

export function ScheduleMatrix({ staff, rows }: ScheduleMatrixProps) {
  return (
    <section className="flex flex-col rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden">
      {/* Header */}
      <div className="p-4 border-b border-outline-variant flex justify-between items-center bg-surface-bright">
        <div>
          <h2 className="text-title-lg text-on-surface">Lịch tháng dạng ma trận</h2>
          <p className="text-label-sm text-on-surface-variant">Hàng = ngày, cột = nhân sự</p>
        </div>
        <div className="flex gap-1 rounded-lg border border-outline-variant bg-surface-container-lowest p-1 shadow-[0_1px_2px_0_rgba(0,0,0,0.05)]">
          <button className="px-4 py-2 rounded text-label-md text-white bg-primary shadow-sm">
            Toàn phòng
          </button>
          <button className="px-4 py-2 rounded text-label-md text-on-surface-variant hover:bg-surface-container-low transition-colors">
            Theo nhân sự
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-x-auto flex-1">
        <table className="w-full min-w-[860px] border-collapse text-left">
          <thead className="sticky top-0 z-20 bg-surface-container-low">
            <tr className="border-b border-outline-variant">
              <th className="px-6 py-3 font-label-sm text-on-surface-variant uppercase tracking-wider w-28">
                Ngày
              </th>
              {staff.map((name) => (
                <th
                  className="px-3 py-3 font-label-sm text-on-surface-variant uppercase tracking-wider text-center min-w-[100px]"
                  key={name}
                >
                  {name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {rows.map((row) => (
              <tr className="hover:bg-surface-container-low transition-colors group" key={row.day}>
                <td className="px-6 py-3 min-h-[60px]">
                  <span className="font-label-md text-on-surface">{row.day}</span>
                  <br />
                  <span className="text-label-sm text-on-surface-variant">{row.weekday}</span>
                </td>
                {staff.map((name) => {
                  const assignment = row.assignments[name];
                  return (
                    <td className="px-3 py-3 min-h-[60px] align-middle" key={name}>
                      <span
                        className={`inline-flex items-center px-2 py-1 rounded-lg text-[11px] font-bold ${toneStyles[assignment.tone]}`}
                      >
                        {assignment.locked ? (
                          <span aria-hidden="true" className="material-symbols-outlined text-[14px] mr-0.5">lock</span>
                        ) : null}
                        {assignment.label}
                      </span>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Legend */}
      <div className="p-4 bg-surface-bright border-t border-outline-variant flex flex-wrap gap-4 items-center text-[14px] text-on-surface">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-primary" />
          <span>Trực 24/24</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-secondary" />
          <span>Thông tầm</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-tertiary" />
          <span>Dịch vụ</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-expert" />
          <span>Chuyên gia</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-sm bg-surface-container-high border border-outline-variant" />
          <span>Khoa/Nghỉ bù</span>
        </div>
      </div>
    </section>
  );
}
