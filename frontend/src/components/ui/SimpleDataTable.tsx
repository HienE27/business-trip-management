import { StatusBadge } from "./StatusBadge";

type SimpleDataTableProps = {
  headers: string[];
  rows: string[][];
  statusColumn?: number;
};

type BadgeTone = "success" | "warning" | "danger" | "neutral" | "info";

function badgeTone(value: string): BadgeTone {
  if (["Hoan tat", "Hop le", "Dang lam", "Da phe duyet", "Hoan tat"].includes(value)) return "success";
  if (["Chan luu", "Can kiem tra", "Qua tai", "Chan luu"].includes(value)) return "danger";
  if (["Canh bao", "Can doi chieu", "Dang chay", "Nghi phep", "Canh bao nhe", "Dang cho"].includes(value)) return "warning";
  if (["Cho", "Cho phan cong", "Cho duyet", "Ban nhap", "Cho duyet", "Cho xu ly"].includes(value)) return "neutral";
  return "info";
}

export function SimpleDataTable({ headers, rows, statusColumn }: SimpleDataTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] border-collapse text-left">
        <thead>
          <tr className="border-b border-outline-variant bg-surface-container-low">
            {headers.map((header) => (
              <th
                className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant font-bold"
                key={header}
                scope="col"
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant">
          {rows.length > 0 ? (
            rows.map((row, ri) => (
              <tr
                className="hover:bg-surface-container-low transition-colors"
                key={ri}
              >
                {row.map((cell, ci) => (
                  <td
                    className="px-5 py-3 font-body-md text-body-md text-on-surface"
                    key={ci}
                  >
                    {statusColumn === ci ? (
                      <StatusBadge tone={badgeTone(cell)} showDot>{cell}</StatusBadge>
                    ) : (
                      cell
                    )}
                  </td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td
                className="px-5 py-10 text-center font-body-md text-body-md text-on-surface-variant"
                colSpan={headers.length}
              >
                Chua co du lieu de hien thi.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
