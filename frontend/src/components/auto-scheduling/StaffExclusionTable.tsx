"use client";

import { useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Badge } from "@/components/ui/Badge";
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

  const bulkExcludeVisible = (exclude: boolean) => {
    const visibleIds = new Set(filteredStaff.map((s) => s.id));
    const next = exclude
      ? Array.from(new Set([...excludedIds, ...visibleIds]))
      : excludedIds.filter((id) => !visibleIds.has(id));
    onExclusionsChange(next);
  };

  const filteredStaff = staff.filter((s) => {
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

  const excludedCount = excludedIds.length;
  const includedCount = staff.length - excludedCount;
  const visibleExcludedCount = filteredStaff.filter((s) => excludedIds.includes(s.id)).length;

  if (loading) {
    return (
      <div className="p-4 space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-14 bg-surface-container-low rounded-xl animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="p-4 space-y-4">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        {/* Search */}
        <div className="relative w-full sm:w-72">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]" aria-hidden="true">search</span>
          <input
            className="h-10 w-full pl-10 pr-4 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface placeholder:text-on-surface-variant/50 focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
            placeholder="Tìm theo tên, mã NV..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {/* Role filter */}
          <div className="relative">
            <select
              className="h-10 pl-3 pr-10 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer transition-all"
              value={filterRole}
              onChange={(e) => setFilterRole(e.target.value as typeof filterRole)}
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="ADMIN">Admin</option>
              <option value="MANAGER">Quản lý</option>
              <option value="STAFF">Nhân sự</option>
            </select>
            <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px] pointer-events-none" aria-hidden="true">expand_more</span>
          </div>

          {/* Excluded filter toggle */}
          <button
            type="button"
            onClick={() => setShowOnlyExcluded(!showOnlyExcluded)}
            aria-pressed={showOnlyExcluded}
            className={`flex items-center gap-2 h-10 px-4 rounded-lg border text-label-sm font-medium transition-all cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 ${
              showOnlyExcluded
                ? "border-error bg-error-container text-error"
                : "border-outline-variant bg-surface-container-low text-on-surface-variant hover:border-primary hover:bg-surface-container-lowest"
            }`}
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">filter_alt_off</span>
            Đã loại trừ
          </button>
        </div>
      </div>

      {/* Summary + bulk actions */}
      <div className="flex items-center gap-3 flex-wrap">
        <Badge tone="success" size="sm">
          <span className="material-symbols-outlined text-[12px]">group</span>
          {includedCount} tham gia
        </Badge>
        <Badge tone="error" size="sm">
          <span className="material-symbols-outlined text-[12px]">group_remove</span>
          {excludedCount} loại trừ
        </Badge>

        <span className="hidden sm:block h-5 w-px bg-outline-variant" aria-hidden="true" />

        {filteredStaff.length > 0 && (
          <>
            <button
              type="button"
              onClick={() => bulkExcludeVisible(true)}
              className="text-label-sm font-medium text-on-surface-variant hover:text-error transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 rounded px-1.5 py-0.5"
            >
              Loại trừ tất cả ({filteredStaff.length})
            </button>
            {visibleExcludedCount > 0 && (
              <button
                type="button"
                onClick={() => bulkExcludeVisible(false)}
                className="text-label-sm font-medium text-on-surface-variant hover:text-secondary transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 rounded px-1.5 py-0.5"
              >
                Bỏ loại trừ hiện đang chọn ({visibleExcludedCount})
              </button>
            )}
          </>
        )}

        {excludedCount > 0 && (
          <button
            type="button"
            onClick={() => onExclusionsChange([])}
            className="text-label-sm font-medium text-primary hover:underline cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 rounded px-1.5 py-0.5"
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
          size="compact"
        />
      ) : (
        <div className="rounded-xl border border-outline-variant bg-surface-container-lowest overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left" aria-label="Danh sách nhân sự tham gia xếp lịch">
              <thead>
                <tr className="bg-surface-container-low border-b border-outline-variant">
                  <th scope="col" className="p-4 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant">
                    Nhân sự
                  </th>
                  <th scope="col" className="p-4 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant">
                    Vai trò
                  </th>
                  <th scope="col" className="p-4 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-center">
                    Tham gia
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/30">
                {filteredStaff.map((s) => {
                  const isExcluded = excludedIds.includes(s.id);
                  return (
                    <tr
                      key={s.id}
                      className={`hover:bg-surface-container-low transition-colors ${isExcluded ? "bg-error-container/5" : ""}`}
                    >
                      <td className="p-4">
                        <div className="flex items-center gap-3">
                          <span className={`w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold shrink-0 ${getAvatarColor(s.id)}`}>
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
                      <td className="p-4">
                        <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
                          <span className="material-symbols-outlined text-[16px] text-on-surface-variant" aria-hidden="true">
                            {s.roles?.includes("ADMIN") ? "admin_panel_settings" : s.roles?.includes("MANAGER") ? "manage_accounts" : "badge"}
                          </span>
                          {getRoleLabel(s.roles)}
                        </div>
                      </td>
                      <td className="p-4 text-center">
                        <Toggle
                          checked={!isExcluded}
                          onChange={(include) => toggleExclusion(s.id, !include)}
                          label={isExcluded ? "Đã loại khỏi xếp lịch" : "Tham gia xếp lịch"}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-3 border-t border-outline-variant bg-surface-container-low flex items-center justify-between gap-3 flex-wrap">
            <p className="text-label-sm text-on-surface-variant">
              Hiển thị <strong>{filteredStaff.length}</strong> / {staff.length} nhân sự
              {search && ` · Tìm thấy "${search}"`}
            </p>
            <p className="text-label-xs text-on-surface-variant">
              Bật/tắt toggle để thêm/bớt khỏi xếp lịch tự động
            </p>
          </div>
        </div>
      )}
    </div>
  );
}