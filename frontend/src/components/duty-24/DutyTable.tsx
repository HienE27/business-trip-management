"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/ui/StatusBadge";

type DutyRow = {
  date: string;
  weekday: string;
  staff: string;
  compDay: string;
  status: string;
  statusTone: "success" | "warning" | "danger" | "neutral" | "info";
};

type DutyTableProps = {
  rows: DutyRow[];
};

function tone(value: string): DutyRow["statusTone"] {
  if (["Hoan tat", "Hop le", "Dang lam", "Da phe duyet"].includes(value)) return "success";
  if (["Chan luu", "Can kiem tra", "Qua tai"].includes(value)) return "danger";
  if (["Canh bao", "Can doi chieu", "Dang chay", "Nghi phep", "Canh bao nhe", "Dang cho"].includes(value)) return "warning";
  if (["Cho", "Cho phan cong", "Cho duyet", "Ban nhap", "Cho duyet", "Cho xu ly"].includes(value)) return "neutral";
  return "info";
}

const HEADERS = ["Ngay truc", "Thu", "Nhan su truc", "Nghi bu", "Trang thai", ""];

export function DutyTable({ rows }: DutyTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[800px] border-collapse text-left">
        <thead>
          <tr className="border-b border-outline-variant bg-surface-container-low">
            {HEADERS.map((h) => (
              <th
                className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider font-bold"
                key={h}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant">
          {rows.length === 0 ? (
            <tr>
              <td className="px-5 py-10 text-center text-on-surface-variant" colSpan={HEADERS.length}>
                Chua co du lieu de hien thi.
              </td>
            </tr>
          ) : (
            rows.map((row, ri) => (
              <tr className="hover:bg-surface-container-low transition-colors group" key={ri}>
                <td className="px-5 py-3 font-body-md text-on-surface font-medium">{row.date}</td>
                <td className="px-5 py-3 font-body-md text-on-surface">{row.weekday}</td>
                <td className="px-5 py-3 font-body-md text-on-surface">{row.staff}</td>
                <td className="px-5 py-3 font-body-md text-on-surface">{row.compDay}</td>
                <td className="px-5 py-3">
                  <StatusBadge showDot tone={tone(row.status)}>{row.status}</StatusBadge>
                </td>
                <td className="px-5 py-3">
                  <Link
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-label-sm text-primary font-medium border border-primary/30 hover:bg-primary/5 transition-colors opacity-0 group-hover:opacity-100"
                    href="/duty-24/shift-detail"
                  >
                    <span className="material-symbols-outlined text-[16px]">visibility</span>
                    Chi tiet
                  </Link>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
