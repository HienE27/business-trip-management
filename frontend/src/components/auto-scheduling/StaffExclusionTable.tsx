"use client";

import { Toggle } from "@/components/auto-scheduling/Toggle";
import type { Staff } from "@/types/api";

const AVATAR_COLORS = [
  "bg-primary-container text-on-primary-container",
  "bg-secondary-container text-on-secondary-container",
  "bg-tertiary-container text-on-tertiary-container",
  "bg-error-container text-on-error-container",
  "bg-surface-container-high text-on-surface",
];

function getAvatarColor(id: number): string {
  return AVATAR_COLORS[id % AVATAR_COLORS.length];
}

function getInitials(fullName: string): string {
  return fullName
    .split(" ")
    .map((w) => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

function getRoleLabel(roles: string[]): string {
  if (!roles || roles.length === 0) return "Nhân viên";
  return roles
    .map((r) => {
      if (r === "ADMIN") return "Quản trị";
      if (r === "MANAGER") return "Quản lý";
      if (r === "STAFF") return "Nhân viên";
      return r;
    })
    .join(", ");
}

type StaffExclusionTableProps = {
  staff: Staff[];
  excludedIds: number[];
  onExclusionsChange: (ids: number[]) => void;
  loading?: boolean;
};

export function StaffExclusionTable({
  staff,
  excludedIds,
  onExclusionsChange,
  loading = false,
}: StaffExclusionTableProps) {
  function toggleExclusion(id: number, excluded: boolean) {
    if (excluded) {
      onExclusionsChange([...excludedIds, id]);
    } else {
      onExclusionsChange(excludedIds.filter((i) => i !== id));
    }
  }

  if (loading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-14 bg-surface-container-low rounded-lg animate-pulse" />
        ))}
      </div>
    );
  }

  if (staff.length === 0) {
    return (
      <p className="text-body-sm text-on-surface-variant py-6 text-center">
        Không có nhân sự nào.
      </p>
    );
  }

  return (
    <div>
      <div className="overflow-x-auto border border-outline-variant/50 rounded-lg">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-outline-variant/50">
              <th className="p-3 w-10 text-center">
                <input
                  className="rounded border-outline-variant text-primary focus:ring-primary"
                  type="checkbox"
                  checked={excludedIds.length === staff.length && staff.length > 0}
                  onChange={(e) => {
                    if (e.target.checked) {
                      onExclusionsChange(staff.map((s) => s.id));
                    } else {
                      onExclusionsChange([]);
                    }
                  }}
                />
              </th>
              <th className="p-3 font-label-sm text-on-surface-variant">Nhân sự</th>
              <th className="p-3 font-label-sm text-on-surface-variant">Vai trò</th>
              <th className="p-3 font-label-sm text-on-surface-variant text-center">
                Loại trừ hoàn toàn
              </th>
              <th className="p-3 font-label-sm text-on-surface-variant">
                Số ca tối đa/tháng
              </th>
              <th className="p-3 text-right" />
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/30">
            {staff.map((s) => {
              const isExcluded = excludedIds.includes(s.id);
              return (
                <tr className="hover:bg-surface transition-colors" key={s.id}>
                  <td className="p-3 text-center">
                    <input
                      className="rounded border-outline-variant text-primary focus:ring-primary"
                      type="checkbox"
                      checked={isExcluded}
                      onChange={(e) => toggleExclusion(s.id, e.target.checked)}
                    />
                  </td>
                  <td className="p-3">
                    <span className="font-medium flex items-center gap-2">
                      <span
                        className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${getAvatarColor(s.id)}`}
                      >
                        {getInitials(s.fullName)}
                      </span>
                      {s.fullName}
                    </span>
                  </td>
                  <td className="p-3 text-on-surface-variant">
                    {getRoleLabel(s.roles)}
                  </td>
                  <td className="p-3 text-center">
                    <Toggle
                      checked={isExcluded}
                      onChange={(v) => toggleExclusion(s.id, v)}
                    />
                  </td>
                  <td className="p-3 text-label-sm text-on-surface-variant">
                    {s.maxShiftsPerMonth > 0 ? s.maxShiftsPerMonth : "Không giới hạn"}
                  </td>
                  <td className="p-3 text-right">
                    {isExcluded && (
                      <span className="text-label-xs text-error bg-error-container px-2 py-0.5 rounded-full">
                        Đã loại trừ
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex items-center justify-between text-label-sm text-on-surface-variant">
        <span>
          {excludedIds.length > 0
            ? `Đã chọn ${excludedIds.length} / ${staff.length} nhân sự`
            : `Hiển thị ${staff.length} nhân sự`}
        </span>
        {excludedIds.length > 0 && (
          <button
            className="text-primary hover:underline text-label-sm"
            onClick={() => onExclusionsChange([])}
            type="button"
          >
            Bỏ chọn tất cả
          </button>
        )}
      </div>
    </div>
  );
}
