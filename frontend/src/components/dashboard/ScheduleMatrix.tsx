import type { StaffScheduleRow, CalendarAssignment } from "@/types/schedule";
import { toneStyles } from "./tone-styles";

type ScheduleMatrixProps = {
  staff: string[];
  rows: StaffScheduleRow[];
  onCellClick?: (staffName: string, dateStr: string, assignment: CalendarAssignment) => void;
};

export function ScheduleMatrix({ staff, rows, onCellClick }: ScheduleMatrixProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="flex h-14 items-center justify-between border-b border-slate-200 px-4">
        <div>
          <h2 className="text-sm font-semibold">Lịch tháng dạng ma trận</h2>
          <p className="text-xs text-slate-500">Hàng = ngày, cột = nhân sự</p>
        </div>
        <div className="flex rounded-md border border-slate-200 bg-slate-50 p-1 text-xs font-medium">
          <button className="h-7 rounded bg-white px-2 shadow-sm">Toàn phòng</button>
          <button className="h-7 px-2 text-slate-500">Theo nhân sự</button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[860px] border-collapse text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase text-slate-500">
            <tr className="h-11 border-b border-slate-200">
              <th className="w-24 px-4">Ngày</th>
              {staff.map((name) => (
                <th className="px-3" key={name}>
                  {name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr className="h-12 border-b border-slate-100 last:border-0" key={row.day}>
                <td className="px-4">
                  <span className="font-semibold">{row.day}</span>
                  <span className="ml-2 text-xs text-slate-500">{row.weekday}</span>
                </td>
                {staff.map((name) => {
                  const assignment = row.assignments[name];
                  const hasValidScheduleId = assignment && assignment.scheduleId !== undefined && assignment.scheduleId !== null;
                  const isClickable = !!onCellClick && hasValidScheduleId;

                  return (
                    <td className="px-3" key={name}>
                      <span
                        onClick={() => {
                          if (isClickable && row.dateStr) {
                            onCellClick(name, row.dateStr, assignment);
                          }
                        }}
                        className={`inline-flex h-7 items-center rounded-md border px-2 text-xs font-medium ${
                          toneStyles[assignment.tone]
                        } ${isClickable ? "cursor-pointer hover:opacity-80 transition-opacity ring-1 ring-slate-900/10 hover:ring-slate-950/20" : ""}`}
                      >
                        {assignment.locked ? "Khóa: " : ""}
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
    </section>
  );
}
