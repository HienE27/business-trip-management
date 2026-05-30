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
        <thead className="bg-[#f8fafc] text-left text-xs font-medium uppercase text-[#667085]">
          <tr className="h-11 border-b border-[#dfe4ea]">
            {headers.map((header) => (
              <th className="px-4" key={header}>
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr className="h-12 border-b border-[#edf1f5] last:border-0 hover:bg-[#f8fafc]" key={row.join("-")}>
              {row.map((cell, index) => (
                <td className="px-4 text-[#364152]" key={`${cell}-${index}`}>
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
