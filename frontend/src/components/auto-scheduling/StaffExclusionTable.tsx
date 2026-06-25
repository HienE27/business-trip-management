"use client";

import { useState, useMemo } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Toggle } from "@/components/auto-scheduling/Toggle";
import { getRoleLabel } from "@/lib/roleLabels";
import type { Staff } from "@/types/api";

const AVATAR_COLORS = [
  "bg-primary-fixed text-primary",
  "bg-secondary-container text-on-secondary-container",
  "bg-tertiary-fixed text-on-tertiary-fixed-variant",
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
  const [search, setSearch] = useState("");
  const [filterRole, setFilterRole] = useState<"ALL" | "ADMIN" | "MANAGER" | "STAFF">("ALL");
  const [showOnlyExcluded, setShowOnlyExcluded] = useState(false);

  const toggleExclusion = (id: number, excluded: boolean) => {
    if (excluded) {
      onExclusionsChange([...excludedIds, id]);
    } else {
      onExclusionsChange(excludedIds.filter((i) => i !== id));
    }
  };

  const filteredStaff = useMemo(() => {
    return staff.filter((s) => {
      if (filterRole !== "ALL") {
        if (!s.roles?.includes(filterRole)) return false;
      }
      const isExcluded = excludedIds.includes(s.id);
      if (showOnlyExcluded && !isExcluded) return false;
      if (search.trim()) {
        const kw = search.toLowerCase();
        const matchName = s.fullName.toLowerCase().includes(kw);
        const matchCode = s.staffCode?.toLowerCase().includes(kw);
        if (!matchName && !matchCode) return false;
      }
      return true;
    });
  }, [staff, excludedIds, search, filterRole, showOnlyExcluded]);

  const excludedCount = excludedIds.length;
  const includedCount = staff.length - excludedCount;

  if (loading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-14 bg-surface-container-low rounded-xl animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        {/* Search */}
        <div className="relative w-full sm:w-64">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px]" aria-hidden="true">search</span>
          <input
            className="h-9 w-full pl-9 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
            placeholder="Tìm theo tên, mã NV..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {/* Role filter */}
          <div className="relative">
            <select
              className="h-9 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface-container-low text-label-md text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer transition-all"
              value={filterRole}
              onChange={(e) => setFilterRole(e.target.value as typeof filterRole)}
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="ADMIN">Admin</option>
              <option value="MANAGER">Quản lý</option>
              <option value="STAFF">Nhân sự</option>
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-on-surface-variant text-[16px] pointer-events-none" aria-hidden="true">expand_more</span>
          </div>

          {/* Excluded filter toggle */}
          <button
            type="button"
            onClick={() => setShowOnlyExcluded(!showOnlyExcluded)}
            className={`flex items-center gap-1.5 h-9 px-3 rounded-lg border text-label-sm font-medium transition-colors cursor-pointer ${
              showOnlyExcluded
                ? "border-error bg-error-container text-error"
                : "border-outline-variant bg-surface-container-low text-on-surface-variant hover:border-primary"
            }`}
          >
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">filter_alt_off</span>
            Đã loại trừ
          </button>
        </div>
      </div>

      {/* Summary chips */}
      <div className="flex items-center gap-3 flex-wrap">
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-secondary-container text-secondary text-label-sm font-semibold border border-secondary/20">
          <span className="material-symbols-outlined text-[12px]" aria-hidden="true">group</span>
          {includedCount} tham gia
        </span>
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-error-container text-error text-label-sm font-semibold border border-error/20">
          <span className="material-symbols-outlined text-[12px]" aria-hidden="true">group_remove</span>
          {excludedCount} loại trừ
        </span>
        {excludedCount > 0 && (
          <button
            className="text-label-sm text-primary hover:underline cursor-pointer"
            onClick={() => onExclusionsChange([])}
            type="button"
          >
            Bỏ loại trừ tất cả
          </button>
        )}
      </div>

      {/* Table */}
      {filteredStaff.length === 0 ? (
        <EmptyState
          icon="search_off"
          title="Không tìm thấy nhân sự"
          description={search ? `Không có nhân sự nào phù hợp với "${search}"` : "Danh sách nhân sự đang trống."}
        />
      ) : (
        <div className="border border-outline-variant rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left" aria-label="Danh sách nhân sự tham gia xếp lịch">
              <thead>
                <tr className="bg-surface-container-low border-b border-outline-variant">
                  <th scope="col" className="p-3 w-10 text-center">
                    <span className="sr-only">Chọn</span>
                  </th>
                  <th scope="col" className="p-3 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-left">
                    Nhân sự
                  </th>
                  <th scope="col" className="p-3 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-left">
                    Vai trò
                  </th>
                  <th scope="col" className="p-3 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-center">
                    Trạng thái
                  </th>
                  <th scope="col" className="p-3 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-left">
                    Số ca tối đa/tháng
                  </th>
                  <th scope="col" className="p-3 w-10" />
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/30">
                {filteredStaff.map((s) => {
                  const isExcluded = excludedIds.includes(s.id);
                  return (
                    <tr key={s.id} className={`hover:bg-surface-container-lowest transition-colors ${isExcluded ? "bg-error-container/5" : ""}`}>
                      <td className="p-3 text-center">
                        <input
                          aria-label={`Chọn nhân sự ${s.fullName} tham gia xếp lịch`}
                          className="rounded border-outline-variant text-primary focus:ring-primary cursor-pointer"
                          type="checkbox"
                          checked={!isExcluded}
                          onChange={(e) => toggleExclusion(s.id, !e.target.checked)}
                        />
                      </td>
                      <td className="p-3">
                        <div className="flex items-center gap-3">
                          <span className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${getAvatarColor(s.id)}`}>
                            {getInitials(s.fullName)}
                          </span>
                          <div className="min-w-0">
                            <p className="text-label-md font-medium text-on-surface truncate">{s.fullName}</p>
                            {s.staffCode && (
                              <p className="text-label-xs text-on-surface-variant">Mã: {s.staffCode}</p>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="p-3">
                        <span className="inline-flex items-center gap-1.5 text-label-sm text-on-surface-variant">
                          <span className="material-symbols-outlined text-[14px] text-on-surface-variant" aria-hidden="true">
                            {s.roles?.includes("ADMIN") ? "admin_panel_settings" : s.roles?.includes("MANAGER") ? "manage_accounts" : "badge"}
                          </span>
                          {getRoleLabel(s.roles)}
                        </span>
                      </td>
                      <td className="p-3 text-center">
                        <Toggle
                          checked={isExcluded}
                          onChange={(v) => toggleExclusion(s.id, v)}
                          label={isExcluded ? "Đã loại khỏi xếp lịch" : "Tham gia xếp lịch"}
                        />
                      </td>
                      <td className="p-3 text-label-sm text-on-surface-variant">
                        {s.maxShiftsPerMonth > 0 ? `${s.maxShiftsPerMonth} ca` : "—"}
                      </td>
                      <td className="p-3">
                        {isExcluded && (
                          <span className="inline-flex items-center gap-1 text-label-xs font-semibold text-error bg-error-container px-2 py-0.5 rounded-full">
                            <span className="material-symbols-outlined text-[10px]" aria-hidden="true">block</span>
                            Loại trừ
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-3 border-t border-outline-variant bg-surface-container-low">
            <p className="text-label-sm text-on-surface-variant">
              Hiển thị <strong>{filteredStaff.length}</strong> / {staff.length} nhân sự
              {search && ` · Tìm thấy "${search}"`}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
