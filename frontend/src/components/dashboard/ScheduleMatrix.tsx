import type { StaffScheduleRow } from "@/types/schedule";
import { toneStyles } from "./tone-styles";

type ScheduleMatrixProps = {
  staff: string[];
  rows: StaffScheduleRow[];
};

export function ScheduleMatrix({ staff, rows }: ScheduleMatrixProps) {
  return (
    <section className="rounded-lg border border-[#dfe4ea] bg-white shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="flex h-14 items-center justify-between border-b border-[#dfe4ea] px-4">
        <div>
          <h2 className="text-sm font-semibold leading-5 text-[#111418]">Lịch tháng dạng ma trận</h2>
          <p className="text-xs leading-4 text-[#667085]">Hàng = ngày, cột = nhân sự</p>
        </div>
        <div className="flex rounded-lg border border-[#dfe4ea] bg-[#f8fafc] p-1 text-xs font-medium">
          <button className="h-7 rounded-md bg-white px-2 text-[#111418] shadow-[0_1px_2px_rgba(15,23,42,0.05)]">Toàn phòng</button>
          <button className="h-7 px-2 text-[#667085]">Theo nhân sự</button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[860px] border-collapse text-sm">
          <thead className="bg-[#f8fafc] text-left text-xs font-medium uppercase text-[#667085]">
            <tr className="h-11 border-b border-[#dfe4ea]">
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
              <tr className="h-12 border-b border-[#edf1f5] last:border-0 hover:bg-[#f8fafc]" key={row.day}>
                <td className="px-4">
                  <span className="font-semibold text-[#111418]">{row.day}</span>
                  <span className="ml-2 text-xs text-[#667085]">{row.weekday}</span>
                </td>
                {staff.map((name) => {
                  const assignment = row.assignments[name];

                  return (
                    <td className="px-3" key={name}>
                      <span
                        className={`inline-flex h-7 items-center rounded-lg border px-2 text-xs font-medium ${
                          toneStyles[assignment.tone]
                        }`}
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
