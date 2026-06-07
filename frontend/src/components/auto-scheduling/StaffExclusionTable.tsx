"use client";

import { useState } from "react";
import { Toggle } from "@/components/auto-scheduling/Toggle";

type StaffException = {
  id: string;
  name: string;
  initials: string;
  role: string;
  avatarColor: string;
  fullyExcluded: boolean;
  unavailableDates: string[];
};

const MOCK_STAFF: StaffException[] = [
  { id: "1", name: "BS. Nguyen Van A", initials: "BS", role: "Truong khoa", avatarColor: "bg-primary-container text-on-primary-container", fullyExcluded: true, unavailableDates: [] },
  { id: "2", name: "BS. Tran Thi B", initials: "BS", role: "Bac si dieu tri", avatarColor: "bg-secondary-container text-on-secondary-container", fullyExcluded: false, unavailableDates: ["12/12", "13/12"] },
  { id: "3", name: "DD. Le Van C", initials: "DD", role: "Dieu duong truong", avatarColor: "bg-tertiary-container text-on-tertiary-container", fullyExcluded: false, unavailableDates: [] },
];

type StaffExclusionTableProps = {
  onAdd?: () => void;
};

export function StaffExclusionTable({ onAdd }: StaffExclusionTableProps) {
  const [staff, setStaff] = useState<StaffException[]>(MOCK_STAFF);

  function toggleExclusion(id: string, excluded: boolean) {
    setStaff((prev) => prev.map((s) => (s.id === id ? { ...s, fullyExcluded: excluded } : s)));
  }

  function toggleDate(id: string, date: string) {
    setStaff((prev) =>
      prev.map((s) => {
        if (s.id !== id) return s;
        const dates = s.unavailableDates.includes(date)
          ? s.unavailableDates.filter((d) => d !== date)
          : [...s.unavailableDates, date];
        return { ...s, unavailableDates: dates };
      })
    );
  }

  function removeDate(id: string, date: string) {
    setStaff((prev) => prev.map((s) => (s.id === id ? { ...s, unavailableDates: s.unavailableDates.filter((d) => d !== date) } : s)));
  }

  return (
    <div>
      <div className="overflow-x-auto border border-outline-variant/50 rounded-lg">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-outline-variant/50">
              <th className="p-3 w-10 text-center">
                <input className="rounded border-outline-variant text-primary focus:ring-primary" type="checkbox" />
              </th>
              <th className="p-3 font-label-sm text-on-surface-variant">Nhan su</th>
              <th className="p-3 font-label-sm text-on-surface-variant">Vai tro</th>
              <th className="p-3 font-label-sm text-on-surface-variant text-center">Loai tru hoan toan</th>
              <th className="p-3 font-label-sm text-on-surface-variant">Ngay khong kha dung</th>
              <th className="p-3 text-right">
                <button
                  className="text-primary font-label-sm flex items-center gap-1 hover:underline"
                  onClick={onAdd}
                  type="button"
                >
                  <span className="material-symbols-outlined text-[16px]">add</span>
                  Them ngoai le
                </button>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/30">
            {staff.map((s) => (
              <tr className="hover:bg-surface transition-colors" key={s.id}>
                <td className="p-3 text-center">
                  <input className="rounded border-outline-variant text-primary focus:ring-primary" type="checkbox" />
                </td>
                <td className="p-3">
                  <span className="font-medium flex items-center gap-2">
                    <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${s.avatarColor}`}>
                      {s.initials}
                    </span>
                    {s.name}
                  </span>
                </td>
                <td className="p-3 text-on-surface-variant">{s.role}</td>
                <td className="p-3 text-center">
                  <Toggle checked={s.fullyExcluded} onChange={(v) => toggleExclusion(s.id, v)} />
                </td>
                <td className="p-3">
                  {s.fullyExcluded ? (
                    <span className="text-outline-variant italic text-xs">Vo hieu hoa (Loai tru hoan toan)</span>
                  ) : s.unavailableDates.length > 0 ? (
                    <div className="flex gap-1 flex-wrap">
                      {s.unavailableDates.map((d) => (
                        <span className="inline-flex items-center gap-1 px-2 py-1 bg-surface-container-high rounded text-xs" key={d}>
                          {d}
                          <button className="hover:text-error transition-colors" onClick={() => removeDate(s.id, d)} type="button">
                            <span className="material-symbols-outlined text-[12px]">close</span>
                          </button>
                        </span>
                      ))}
                    </div>
                  ) : (
                    <span className="text-outline-variant italic text-xs">Khong co</span>
                  )}
                </td>
                <td className="p-3 text-right">
                  <button className="text-outline hover:text-primary transition-colors" type="button">
                    <span className="material-symbols-outlined text-[18px]">edit</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-4 flex items-center justify-between text-label-sm text-on-surface-variant">
        <span>Dang hien thi 3 / 24 nhan su</span>
        <div className="flex gap-1">
          <button className="p-1 hover:bg-surface-container rounded transition-colors" type="button">
            <span className="material-symbols-outlined text-[18px]">chevron_left</span>
          </button>
          <button className="p-1 hover:bg-surface-container rounded transition-colors" type="button">
            <span className="material-symbols-outlined text-[18px]">chevron_right</span>
          </button>
        </div>
      </div>
    </div>
  );
}
