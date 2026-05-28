import { StatusBadge } from "./StatusBadge";

type SimpleDataTableProps = {
  headers: string[];
  rows: string[][];
  statusColumn?: number;
};

type BadgeTone = "success" | "warning" | "danger" | "neutral" | "info";

function badgeTone(value: string): BadgeTone {
  if (["Hợp lệ", "Hoàn tất", "Đang làm"].includes(value)) {
    return "success";
  }
  if (["Chặn lưu", "Cần kiểm tra"].includes(value)) {
    return "danger";
  }
  if (["Cảnh báo chuyên gia", "Cần đối chiếu", "Đang chạy", "Nghỉ phép"].includes(value)) {
    return "warning";
  }
  if (["Chờ", "Chờ phân công"].includes(value)) {
    return "neutral";
  }
  return "info";
}

export function SimpleDataTable({ headers, rows, statusColumn }: SimpleDataTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] border-collapse text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium uppercase text-slate-500">
          <tr className="h-11 border-b border-slate-200">
            {headers.map((header) => (
              <th className="px-4" key={header}>
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr className="h-12 border-b border-slate-100 last:border-0 hover:bg-slate-50" key={row.join("-")}>
              {row.map((cell, index) => (
                <td className="px-4 text-slate-700" key={`${cell}-${index}`}>
                  {statusColumn === index ? (
                    <StatusBadge tone={badgeTone(cell)}>{cell}</StatusBadge>
                  ) : (
                    cell
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
