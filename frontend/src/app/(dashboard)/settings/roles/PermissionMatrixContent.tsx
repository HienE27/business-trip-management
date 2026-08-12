'use client';

/**
 * M01-F05 — Phân quyền hệ thống (UI/UX Pro Max)
 *
 * Hiển thị ma trận vai trò × quyền hệ thống (số lượng lấy từ backend, không
 * hardcode — BUG#6 fix). ADMIN có thể toggle từng cell để cấp/thu hồi quyền.
 *
 * Features:
 * - Collapsible groups với summary
 * - Search/filter permissions
 * - Role summary cards
 * - Dirty state tracking
 * - Keyboard shortcuts
 * - Responsive layout
 *
 * Backend: GET /api/v1/roles/permissions/matrix
 */

import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import dynamic from "next/dynamic";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { RolePermissionMatrix, RoleMatrixPermission } from "@/types/api";
import { useRole } from "@/hooks/useRole";
import { useToast } from "@/components/ui/ToastProvider";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { ROLE_LABELS } from "@/lib/roleLabels";
import { Permission } from "@/lib/permissions";

// Lazy-load the confirm dialog
const ConfirmDialog = dynamic(
  () => import("@/components/ui/ConfirmDialog").then((m) => m.ConfirmDialog),
  { ssr: false },
);

/* ─── Role badge colours ─── */
const ROLE_COLORS: Record<string, { bg: string; text: string; border: string; icon: string }> = {
  ADMIN:   { bg: "bg-error-container",     text: "text-on-error-container",     border: "border-error/40",     icon: "shield" },
  MANAGER: { bg: "bg-primary-container",   text: "text-on-primary-container",   border: "border-primary/40",   icon: "manage_accounts" },
  STAFF:   { bg: "bg-secondary-container", text: "text-on-secondary-container", border: "border-secondary/40", icon: "person" },
};

/* ─── Permission labels (count = backend app_permission registry, BUG#6) ─── */
const PERM_LABELS: Record<string, string> = {
  [Permission.DASHBOARD_VIEW]:            "Xem dashboard tổng quan",
  [Permission.DASHBOARD_AGGREGATE]:      "Xem chỉ số tổng hợp toàn hệ thống",
  [Permission.STAFF_VIEW]:               "Xem thông tin nhân sự",
  [Permission.STAFF_VIEW_ALL]:           "Xem danh sách nhân sự toàn phòng",
  [Permission.STAFF_VIEW_SELF]:          "Xem thông tin cá nhân của chính mình",
  [Permission.STAFF_CREATE]:             "Tạo nhân sự mới",
  [Permission.STAFF_UPDATE]:            "Cập nhật thông tin nhân sự",
  [Permission.STAFF_DELETE]:            "Xóa / vô hiệu hóa nhân sự",
  [Permission.STAFF_REACTIVATE]:        "Kích hoạt lại nhân sự đã vô hiệu hóa",
  [Permission.STAFF_IMPORT]:            "Import nhân sự từ Excel/CSV",
  [Permission.STAFF_EXPORT]:            "Xuất danh sách nhân sự ra Excel/CSV",
  [Permission.ROLE_VIEW]:               "Xem ma trận phân quyền",
  [Permission.ROLE_EDIT]:               "Sửa ma trận phân quyền",
  [Permission.PERIOD_VIEW]:             "Xem kỳ lịch công tác",
  [Permission.PERIOD_CREATE]:           "Tạo kỳ lịch công tác",
  [Permission.PERIOD_UPDATE]:           "Cập nhật kỳ lịch công tác",
  [Permission.PERIOD_DELETE]:           "Xóa kỳ lịch công tác",
  [Permission.PERIOD_PUBLISH]:          "Công bố kỳ lịch công tác",
  [Permission.PERIOD_ARCHIVE]:          "Lưu trữ kỳ lịch công tác",
  [Permission.SCHEDULE_VIEW]:           "Xem lịch trực (cá nhân/toàn hệ thống)",
  [Permission.SCHEDULE_CREATE]:         "Tạo lịch trực",
  [Permission.SCHEDULE_UPDATE]:          "Cập nhật lịch trực",
  [Permission.SCHEDULE_DELETE]:          "Xóa lịch trực",
  [Permission.SCHEDULE_PUBLISH]:         "Công bố lịch trực hàng loạt",
  [Permission.SCHEDULE_EXPORT]:          "Xuất lịch trực ra Excel/PDF",
  [Permission.AUTO_SCHEDULE_VIEW]:       "Xem phương án auto-schedule",
  [Permission.AUTO_SCHEDULE_RUN]:        "Chạy auto-schedule",
  [Permission.AUTO_SCHEDULE_APPLY]:      "Áp dụng phương án auto-schedule",
  [Permission.AUTO_SCHEDULE_CONFIG_VIEW]:"Xem cấu hình auto-schedule",
  [Permission.AUTO_SCHEDULE_CONFIG_EDIT]:"Sửa cấu hình auto-schedule",
  [Permission.LEAVE_VIEW]:              "Xem yêu cầu nghỉ phép",
  [Permission.LEAVE_CREATE]:            "Tạo yêu cầu nghỉ phép",
  [Permission.LEAVE_APPROVE]:           "Duyệt / từ chối yêu cầu nghỉ phép",
  [Permission.LEAVE_CANCEL_SELF]:       "Hủy yêu cầu nghỉ phép của chính mình",
  [Permission.EXCHANGE_VIEW]:            "Xem yêu cầu đổi ca",
  [Permission.EXCHANGE_CREATE]:          "Tạo yêu cầu đổi ca",
  [Permission.EXCHANGE_APPROVE]:         "Duyệt / từ chối yêu cầu đổi ca",
  [Permission.EXCHANGE_CANCEL_SELF]:     "Hủy yêu cầu đổi ca của chính mình",
  [Permission.REPORT_VIEW]:              "Xem báo cáo thống kê",
  [Permission.REPORT_EXPORT]:            "Xuất báo cáo",
  [Permission.HOLIDAY_VIEW]:             "Xem ngày lễ",
  [Permission.HOLIDAY_CREATE]:           "Tạo ngày lễ",
  [Permission.HOLIDAY_UPDATE]:           "Cập nhật ngày lễ",
  [Permission.HOLIDAY_DELETE]:          "Xóa ngày lễ",
  [Permission.NOTIFICATION_VIEW]:        "Xem thông báo",
  [Permission.NOTIFICATION_CREATE]:      "Tạo thông báo",
  [Permission.NOTIFICATION_BROADCAST]:   "Gửi thông báo broadcast tới nhiều người",
  [Permission.NOTIFICATION_MANAGE_SELF]: "Quản lý thông báo của chính mình",
  [Permission.AUDIT_VIEW]:               "Xem lịch sử thao tác",
  [Permission.AUDIT_DELETE]:             "Xóa lịch sử thao tác",
  [Permission.APP_CONFIG_VIEW]:          "Xem cấu hình hệ thống",
  [Permission.APP_CONFIG_EDIT]:          "Sửa cấu hình hệ thống",
  [Permission.DATA_INTEGRITY_RUN]:       "Chạy kiểm tra tính toàn vẹn dữ liệu",
  [Permission.SPECIALTY_MANAGE]:         "Quản lý danh mục chuyên khoa",
  [Permission.SHIFT_TYPE_MANAGE]:         "Quản lý danh mục loại ca",
  [Permission.SCHEDULE_TEMPLATE_MANAGE]:  "Quản lý mẫu lịch trực",
};

/* ─── Permission groups ─── */
const PERM_GROUPS = [
  { label: "Tổng quan",      icon: "dashboard",    keys: [Permission.DASHBOARD_VIEW, Permission.DASHBOARD_AGGREGATE] },
  { label: "Nhân sự",        icon: "groups",       keys: [Permission.STAFF_VIEW, Permission.STAFF_VIEW_ALL, Permission.STAFF_VIEW_SELF, Permission.STAFF_CREATE, Permission.STAFF_UPDATE, Permission.STAFF_DELETE, Permission.STAFF_REACTIVATE, Permission.STAFF_IMPORT, Permission.STAFF_EXPORT] },
  { label: "Phân quyền",     icon: "security",     keys: [Permission.ROLE_VIEW, Permission.ROLE_EDIT] },
  { label: "Kỳ lịch",        icon: "event_note",   keys: [Permission.PERIOD_VIEW, Permission.PERIOD_CREATE, Permission.PERIOD_UPDATE, Permission.PERIOD_DELETE, Permission.PERIOD_PUBLISH, Permission.PERIOD_ARCHIVE] },
  { label: "Lịch trực",     icon: "calendar_month", keys: [Permission.SCHEDULE_VIEW, Permission.SCHEDULE_CREATE, Permission.SCHEDULE_UPDATE, Permission.SCHEDULE_DELETE, Permission.SCHEDULE_PUBLISH, Permission.SCHEDULE_EXPORT] },
  { label: "Tự động xếp",   icon: "auto_mode",    keys: [Permission.AUTO_SCHEDULE_VIEW, Permission.AUTO_SCHEDULE_RUN, Permission.AUTO_SCHEDULE_APPLY, Permission.AUTO_SCHEDULE_CONFIG_VIEW, Permission.AUTO_SCHEDULE_CONFIG_EDIT] },
  { label: "Nghỉ phép",     icon: "event_busy",    keys: [Permission.LEAVE_VIEW, Permission.LEAVE_CREATE, Permission.LEAVE_APPROVE, Permission.LEAVE_CANCEL_SELF] },
  { label: "Đổi ca",        icon: "swap_horiz",   keys: [Permission.EXCHANGE_VIEW, Permission.EXCHANGE_CREATE, Permission.EXCHANGE_APPROVE, Permission.EXCHANGE_CANCEL_SELF] },
  { label: "Báo cáo",       icon: "assessment",   keys: [Permission.REPORT_VIEW, Permission.REPORT_EXPORT] },
  { label: "Ngày lễ",       icon: "celebration",  keys: [Permission.HOLIDAY_VIEW, Permission.HOLIDAY_CREATE, Permission.HOLIDAY_UPDATE, Permission.HOLIDAY_DELETE] },
  { label: "Thông báo",      icon: "notifications", keys: [Permission.NOTIFICATION_VIEW, Permission.NOTIFICATION_CREATE, Permission.NOTIFICATION_BROADCAST, Permission.NOTIFICATION_MANAGE_SELF] },
  { label: "Hệ thống",      icon: "settings",     keys: [Permission.AUDIT_VIEW, Permission.AUDIT_DELETE, Permission.APP_CONFIG_VIEW, Permission.APP_CONFIG_EDIT, Permission.DATA_INTEGRITY_RUN] },
  { label: "Danh mục",      icon: "folder_open",  keys: [Permission.SPECIALTY_MANAGE, Permission.SHIFT_TYPE_MANAGE, Permission.SCHEDULE_TEMPLATE_MANAGE] },
];

/* ─── Collapsible Group Component ─── */
function PermissionGroup({
  group,
  roles,
  matrixLookup,
  pendingToggles,
  isAdmin,
  saving,
  onToggle,
}: {
  group: { label: string; icon: string; keys: string[] };
  roles: { id: number; name: string }[];
  matrixLookup: Map<string, boolean>;
  pendingToggles: Map<string, boolean>;
  isAdmin: boolean;
  saving: boolean;
  onToggle: (roleId: number, roleName: string, permId: number, permName: string) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const grantedCount = useMemo(() => {
    return group.keys.reduce((acc, key) => {
      roles.forEach((r) => {
        if (matrixLookup.get(`${r.id}|${key}`)) acc++;
      });
      return acc;
    }, 0);
  }, [group.keys, roles, matrixLookup]);

  const totalCells = group.keys.length * roles.length;
  const progressPercent = totalCells > 0 ? Math.round((grantedCount / totalCells) * 100) : 0;
  const ringDashArray = `${progressPercent * 0.942} 94.2`;
  
  // Dynamic accent based on progress
  const getProgressColor = () => {
    if (progressPercent === 0) return "bg-outline-variant";
    if (progressPercent < 33) return "bg-error";
    if (progressPercent < 66) return "bg-tertiary";
    if (progressPercent < 100) return "bg-primary";
    return "bg-secondary";
  };

  const getProgressTextColor = () => {
    if (progressPercent === 0) return "text-on-surface-variant";
    if (progressPercent < 33) return "text-error";
    if (progressPercent < 66) return "text-tertiary";
    if (progressPercent < 100) return "text-primary";
    return "text-secondary";
  };

  return (
    <div className="rounded-2xl border border-outline-variant/50 bg-surface-container-lowest transition-all duration-300 hover:shadow-md overflow-hidden">
      {/* Group Header */}
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="w-full flex items-center justify-between px-5 py-4 hover:bg-primary-container/10 transition-colors duration-200 group/header"
      >
        <div className="flex items-center gap-4">
          <div className={`flex h-12 w-12 items-center justify-center rounded-2xl ${progressPercent === 0 ? "bg-surface-container-highest text-on-surface-variant" : `${getProgressColor()} text-white`} shadow-sm transition-all duration-300 group-hover/header:scale-105`}>
            <span className="material-symbols-outlined text-[22px]">{group.icon}</span>
          </div>
          <div className="text-left">
            <h3 className="text-[16px] font-bold text-on-surface">{group.label}</h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-[12px] text-on-surface-variant">{group.keys.length} quyền</span>
              <span className="text-[12px] text-on-surface-variant">·</span>
              <span className={`text-[12px] font-semibold ${getProgressTextColor()}`}>
                {grantedCount}/{totalCells} đã cấp
              </span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-4">
          {/* Progress Bar */}
          <div className="hidden sm:flex items-center gap-3">
            <div className="w-36 h-2 rounded-full bg-surface-container-highest overflow-hidden">
              <div
                className={`h-full rounded-full ${getProgressColor()} transition-all duration-500`}
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <span className={`text-[13px] font-bold w-10 ${getProgressTextColor()}`}>
              {progressPercent}%
            </span>
          </div>
          
          {/* Mobile Progress Ring */}
          <div className="sm:hidden relative h-10 w-10">
            <svg className="h-10 w-10 -rotate-90 transform" viewBox="0 0 36 36">
              <circle cx="18" cy="18" r="15" fill="none" stroke="currentColor" strokeWidth="4" className="text-surface-container-highest" />
              <circle cx="18" cy="18" r="15" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeDasharray={ringDashArray} className={progressPercent === 0 ? "text-outline-variant" : getProgressTextColor()} />
            </svg>
            <span className={`absolute inset-0 flex items-center justify-center text-[10px] font-bold ${getProgressTextColor()}`}>
              {progressPercent}%
            </span>
          </div>
          
          <span className={`material-symbols-outlined text-[22px] text-on-surface-variant transition-all duration-300 group-hover/header:text-primary ${collapsed ? "" : "rotate-90"}`}>
            chevron_right
          </span>
        </div>
      </button>

      {/* Permission List */}
      <div className={`overflow-hidden transition-all duration-300 ${collapsed ? "max-h-0 opacity-0" : "max-h-[2000px] opacity-100"}`}>
        <div className="divide-y divide-outline-variant/30 bg-surface-container-lowest">
          {group.keys.map((permKey) => {
            const perm = roles.length > 0 ? { id: 0, name: permKey, description: "" } : null;
            if (!perm) return null;
            
            return (
              <div
                key={permKey}
                className="flex items-center justify-between px-5 py-3.5 hover:bg-primary-fixed/30 transition-colors duration-200 group/row"
              >
                <div className="flex-1 min-w-0 mr-4">
                  <p className="text-[14px] font-medium text-on-surface">
                    {PERM_LABELS[permKey] ?? permKey}
                  </p>
                  <p className="text-[10px] text-on-surface-variant font-mono mt-0.5">{permKey}</p>
                </div>
                <div className="flex items-center gap-3">
                  {roles.map((r) => {
                    const granted = matrixLookup.get(`${r.id}|${permKey}`) ?? false;
                    const pendingKey = `${r.id}|${permKey}`;
                    const isPending = pendingToggles.has(pendingKey);
                    const roleColor = ROLE_COLORS[r.name];

                    return (
                      <div key={r.id} className="flex flex-col items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => onToggle(r.id, r.name, 0, permKey)}
                          disabled={!isAdmin || saving}
                          data-testid={`toggle-${r.id}-${permKey}`}
                          data-granted={granted ? "true" : "false"}
                          title={`${r.name}: ${granted ? "Có quyền" : "Không có quyền"}`}
                          className={[
                            "relative flex h-10 w-14 items-center justify-center rounded-lg border transition-colors duration-200",
                            granted
                              ? r.name === "ADMIN"
                                ? "bg-error-container border-error text-on-error-container hover:bg-error hover:text-on-error hover:border-error"
                                : r.name === "MANAGER"
                                ? "bg-primary-container border-primary text-on-primary-container hover:bg-primary hover:text-on-primary hover:border-primary"
                                : "bg-secondary-container border-secondary text-on-secondary-container hover:bg-secondary hover:text-on-secondary hover:border-secondary"
                              : "border-outline bg-surface-container-lowest text-outline hover:border-primary hover:bg-primary-fixed hover:text-primary",
                            (!isAdmin || saving) && "cursor-not-allowed opacity-50",
                            isPending && "animate-pulse ring-2 ring-primary ring-offset-2",
                          ].join(" ")}
                        >
                          {isPending ? (
                            <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                          ) : granted ? (
                            <span className="material-symbols-outlined text-[20px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                              check_circle
                            </span>
                          ) : (
                            <span className="material-symbols-outlined text-[20px] text-outline">radio_button_unchecked</span>
                          )}
                        </button>
                        <span className={`text-[9px] font-bold uppercase tracking-wide ${granted ? (r.name === "ADMIN" ? "text-error" : r.name === "MANAGER" ? "text-primary" : "text-secondary") : "text-on-surface-variant"}`}>
                          {r.name}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* ─── Role Summary Card ─── */
function RoleSummaryCard({
  role,
  totalPerms,
  grantedPerms,
  isAdmin,
  onGrantAll,
  onRevokeAll,
  saving,
}: {
  role: { id: number; name: string };
  totalPerms: number;
  grantedPerms: number;
  isAdmin: boolean;
  onGrantAll: () => void;
  onRevokeAll: () => void;
  saving: boolean;
}) {
  const roleColor = ROLE_COLORS[role.name];
  const percentage = Math.round((grantedPerms / totalPerms) * 100);
  
  const getRoleAccent = () => {
    switch(role.name) {
      case "ADMIN": return "bg-error";
      case "MANAGER": return "bg-primary";
      default: return "bg-secondary";
    }
  };

  const getRoleTextColor = () => {
    switch(role.name) {
      case "ADMIN": return "text-error";
      case "MANAGER": return "text-primary";
      default: return "text-secondary";
    }
  };

  return (
    <div className={`group relative rounded-2xl border border-outline-variant/30 bg-surface-container-lowest overflow-hidden transition-all duration-300 hover:shadow-md hover:-translate-y-0.5`}>
      {/* Decorative gradient top bar */}
      <div className={`absolute top-0 left-0 right-0 h-1 ${getRoleAccent()}`} />
      
<div className="p-5 pt-4">
        <div className="flex items-start justify-between mb-4">
          <div className="flex items-center gap-4">
            <div className={`relative flex h-14 w-14 items-center justify-center rounded-2xl ${getRoleAccent()} shadow-md transition-transform duration-300 group-hover:scale-110`}>
              <span className="material-symbols-outlined text-[26px] text-white">
                {roleColor?.icon ?? "person"}
              </span>
              {/* Badge indicator */}
              <div className={`absolute -bottom-1 -right-1 h-5 w-5 rounded-full bg-surface-container-lowest shadow flex items-center justify-center ${role.name === "ADMIN" ? "text-error" : role.name === "MANAGER" ? "text-primary" : "text-secondary"}`}>
                <span className="material-symbols-outlined text-[12px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                  {role.name === "ADMIN" ? "shield" : role.name === "MANAGER" ? "manage_accounts" : "person"}
                </span>
              </div>
            </div>
            <div>
              <h4 className="text-[17px] font-bold text-on-surface">
                {ROLE_LABELS[role.name] ?? role.name}
              </h4>
              <div className="flex items-center gap-2 mt-0.5">
                <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide ${role.name === "ADMIN" ? "bg-error-container text-on-error-container" : role.name === "MANAGER" ? "bg-primary-container text-on-primary-container" : "bg-secondary-container text-on-secondary-container"}`}>
                  {role.name}
                </span>
              </div>
            </div>
          </div>
          
          {/* Percentage Badge */}
          <div className={`flex flex-col items-end`}>
            <span className={`text-[28px] font-black leading-none ${percentage > 0 ? getRoleTextColor() : "text-on-surface-variant"}`}>
              {percentage}%
            </span>
          </div>
        </div>

        {/* Progress Bar with gradient */}
        <div className="mb-4">
          <div className="flex justify-between text-[11px] mb-2">
            <span className="text-on-surface-variant font-medium">Quyền được cấp</span>
            <span className="font-bold text-on-surface">{grantedPerms}/{totalPerms}</span>
          </div>
          <div className="relative h-2 rounded-full bg-surface-container-highest overflow-hidden">
            <div
              className={`absolute inset-y-0 left-0 rounded-full transition-all duration-700 ease-out ${percentage > 0 ? getRoleAccent() : "bg-outline-variant"}`}
              style={{ width: `${Math.max(percentage, 2)}%` }}
            />
          </div>
        </div>

        {/* Quick Actions */}
        {isAdmin && (
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={onGrantAll}
              disabled={saving || grantedPerms === totalPerms}
              className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-xl text-[12px] font-semibold bg-secondary-container text-on-secondary-container hover:bg-secondary hover:text-on-secondary transition-colors duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-[16px]">add_circle</span>
              Cấp tất cả
            </button>
            <button
              type="button"
              onClick={onRevokeAll}
              disabled={saving || grantedPerms === 0}
              className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-xl text-[12px] font-semibold bg-error-container text-on-error-container hover:bg-error hover:text-on-error transition-colors duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-[16px]">remove_circle</span>
              Thu hồi tất cả
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

/* ─── Main Component ─── */
export function PermissionMatrixContent() {
  const role = useRole();
  const isAdmin = role === "ADMIN";
  const toast = useToast();
  const searchInputRef = useRef<HTMLInputElement>(null);

  const [matrix, setMatrix] = useState<RolePermissionMatrix | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toggleTarget, setToggleTarget] = useState<{
    roleId: number;
    roleName: string;
    permissionId: number;
    permissionName: string;
    currentValue: boolean;
  } | null>(null);
  const [pendingToggles, setPendingToggles] = useState<Map<string, boolean>>(new Map());
  const [searchQuery, setSearchQuery] = useState("");
  const [bulkActionRole, setBulkActionRole] = useState<{ roleId: number; roleName: string; grantAll: boolean } | null>(null);

  /* ── Load ── */
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await api.getRolePermissionMatrix();
        if (!cancelled) setMatrix(res.data);
      } catch (e) {
        if (!cancelled) toast.error(getErrorMessage(e, "Lỗi tải ma trận phân quyền"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [toast]);

  /* ── Keyboard shortcuts ── */
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "f") {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  /* ── Build data structures ── */
  const roles = useMemo(() => matrix?.roles.filter((r) => r.isActive) ?? [], [matrix]);
  
  const matrixLookup = useMemo(() => {
    const map = new Map<string, boolean>();
    matrix?.matrix.forEach((e) => map.set(`${e.roleId}|${e.permissionName}`, e.granted));
    pendingToggles.forEach((value, key) => map.set(key, value));
    return map;
  }, [matrix, pendingToggles]);

  const permMap = useMemo(() => {
    const map = new Map<string, RoleMatrixPermission>();
    matrix?.permissions.forEach((p) => map.set(p.name, p));
    return map;
  }, [matrix]);

  /* ── Filter groups by search ── */
  // Groups come from PERM_GROUPS plus a fallback "Khác" group for any BE
  // permission not covered there, so new backend permissions never disappear
  // from the matrix (BUG#6 — FE list used to drift from the BE registry).
  const extraPermKeys = useMemo(() => {
    if (!matrix) return [];
    const covered = new Set<string>(PERM_GROUPS.flatMap((g) => g.keys));
    return matrix.permissions.map((p) => p.name).filter((name) => !covered.has(name));
  }, [matrix]);

  const allGroups = useMemo(() => {
    if (extraPermKeys.length === 0) return PERM_GROUPS;
    return [...PERM_GROUPS, { label: "Khác", icon: "more_horiz", keys: extraPermKeys }];
  }, [extraPermKeys]);

  const filteredGroups = useMemo(() => {
    if (!searchQuery.trim()) return allGroups;

    const query = searchQuery.toLowerCase();
    return allGroups.map((group) => ({
      ...group,
      keys: group.keys.filter((key) => {
        const label = PERM_LABELS[key]?.toLowerCase() ?? "";
        const keyLower = key.toLowerCase();
        return label.includes(query) || keyLower.includes(query);
      }),
    })).filter((group) => group.keys.length > 0);
  }, [searchQuery, allGroups]);

  /* ── Role summary stats ── */
  // Total = authoritative permission count from the BE matrix endpoint, not a
  // hardcoded FE list (BUG#6: FE said 52/53 while DB had 56).
  const totalPermCount = matrix?.permissions.length ?? 0;
  const roleStats = useMemo(() => {
    return roles.map((r) => {
      let granted = 0;
      matrixLookup.forEach((value, key) => {
        if (value && key.startsWith(`${r.id}|`)) granted++;
      });
      return { role: r, granted, total: totalPermCount };
    });
  }, [roles, matrixLookup, totalPermCount]);

  /* ── Toggle handler ── */
  const handleToggle = useCallback(
    (roleId: number, roleName: string, _permissionId: number, permissionName: string) => {
      if (!isAdmin) return;
      const current = matrixLookup.get(`${roleId}|${permissionName}`) ?? false;
      setToggleTarget({ roleId, roleName, permissionId: 0, permissionName, currentValue: current });
    },
    [isAdmin, matrixLookup],
  );

  const confirmToggle = async () => {
    if (!toggleTarget) return;
    const { roleId, permissionId, permissionName, currentValue } = toggleTarget;
    const key = `${roleId}|${permissionName}`;
    const newValue = !currentValue;

    setPendingToggles((prev) => { const next = new Map(prev); next.set(key, newValue); return next; });
    setToggleTarget(null);

    try {
      setSaving(true);
      // Find the actual permission ID from permMap
      const perm = permMap.get(permissionName);
      if (perm) {
        await api.toggleRolePermission({ roleId, permissionId: perm.id, granted: newValue });
      }
      setPendingToggles((prev) => { const next = new Map(prev); next.delete(key); return next; });
      toast.success(`${newValue ? "Cấp" : "Thu hồi"} quyền thành công.`);
    } catch (e) {
      setPendingToggles((prev) => { const next = new Map(prev); next.delete(key); return next; });
      toast.error(getErrorMessage(e, "Lỗi cập nhật quyền"));
    } finally {
      setSaving(false);
    }
  };

  /* ── Bulk actions ── */
  const confirmBulkAction = async () => {
    if (!bulkActionRole) return;
    const { roleId, roleName, grantAll } = bulkActionRole;

    try {
      setSaving(true);
      // Collect only the cells that actually need to change so the bulk
      // call does the minimum amount of work — and the audit log only
      // records the deltas.
      const permissionIds: number[] = [];
      for (const perm of permMap.values()) {
        const key = `${roleId}|${perm.name}`;
        const current = matrixLookup.get(key) ?? false;
        if (current !== grantAll) {
          permissionIds.push(perm.id);
        }
      }

      if (permissionIds.length > 0) {
        // Single bulk endpoint → single transaction on the backend,
        // single permission-version bump. Avoids the N round-trip + N
        // bump() calls that the per-cell loop used to do.
        await api.bulkToggleRolePermission({ roleId, permissionIds, granted: grantAll });
      }

      // Refresh matrix
      const res = await api.getRolePermissionMatrix();
      setMatrix(res.data);
      toast.success(grantAll ? `Đã cấp tất cả quyền cho ${ROLE_LABELS[roleName] ?? roleName}` : `Đã thu hồi tất cả quyền của ${ROLE_LABELS[roleName] ?? roleName}`);
    } catch (e) {
      toast.error(getErrorMessage(e, "Lỗi cập nhật quyền"));
    } finally {
      setSaving(false);
      setBulkActionRole(null);
    }
  };

  /* ── Render ── */
  if (loading) {
    return (
      <div className="space-y-6" data-testid="roles-loading">
        <div className="flex items-center gap-4">
          <Skeleton className="h-12 w-48" />
          <Skeleton className="h-10 w-64" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-40 rounded-2xl" />
          ))}
        </div>
        <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-20 rounded-2xl" />
        ))}
        </div>
      </div>
    );
  }

  if (!matrix) {
    return (
      <EmptyState
        icon="shield"
        title="Không tải được ma trận phân quyền"
        description="Vui lòng thử lại sau."
      />
    );
  }

  return (
    <div className="space-y-6" data-testid="roles-matrix">
      {/* Header with Search */}
      <div className="relative">
        {/* Decorative background */}
        <div className="absolute inset-0 -m-4 rounded-3xl bg-primary-container/10" />
        
        <div className="relative flex flex-col lg:flex-row lg:items-center justify-between gap-5 p-6">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-primary to-secondary shadow-md">
                <span className="material-symbols-outlined text-[24px] text-white">admin_panel_settings</span>
              </div>
        <div>
                <h2 className="text-[26px] font-bold text-on-surface">
                  Phân quyền hệ thống
          </h2>
                <p className="text-[13px] text-on-surface-variant">
            {isAdmin
                    ? `Quản lý ${totalPermCount} quyền hệ thống cho 3 vai trò · Nhấn Cmd+F để tìm kiếm`
                    : "Xem ma trận phân quyền · Chỉ Quản trị viên mới có thể chỉnh sửa"}
          </p>
        </div>
            </div>
      </div>

          {/* Search */}
          <div className="relative w-full lg:w-auto">
            <div className="absolute inset-y-0 left-0 flex items-center pointer-events-none">
              <span className="material-symbols-outlined pl-4 text-[22px] text-on-surface-variant">search</span>
            </div>
            <input
              ref={searchInputRef}
              type="text"
              placeholder="Tìm kiếm quyền..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="h-11 w-full lg:w-72 pl-12 pr-10 rounded-lg border border-outline bg-surface-container-low text-[14px] text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
            />
            {searchQuery ? (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-[18px] text-on-surface-variant hover:text-on-surface transition-colors"
              >
                close
              </button>
            ) : (
              <kbd className="absolute right-3 top-1/2 -translate-y-1/2 hidden lg:inline-flex h-5 items-center gap-1 rounded border border-outline bg-surface-container-lowest px-1.5 font-mono text-[10px] text-on-surface">
                <span>⌘</span>F
              </kbd>
            )}
          </div>
        </div>
      </div>

      {/* Role Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {roleStats.map(({ role, granted, total }) => (
          <RoleSummaryCard
            key={role.id}
            role={role}
            totalPerms={total}
            grantedPerms={granted}
            isAdmin={isAdmin}
            onGrantAll={() => setBulkActionRole({ roleId: role.id, roleName: role.name, grantAll: true })}
            onRevokeAll={() => setBulkActionRole({ roleId: role.id, roleName: role.name, grantAll: false })}
            saving={saving}
          />
        ))}
                  </div>

      {/* Search Results Info */}
      {searchQuery && (
        <div className="flex items-center gap-2 px-4 py-2.5 rounded-lg border border-outline-variant bg-surface-container-lowest text-[13px] text-on-surface-variant">
          <span className="material-symbols-outlined text-[18px]">search</span>
          Tìm thấy {filteredGroups.reduce((acc, g) => acc + g.keys.length, 0)} quyền cho &quot;{searchQuery}&quot;
                      <button
                        type="button"
            onClick={() => setSearchQuery("")}
            className="ml-auto text-primary font-semibold hover:underline"
          >
            Xóa tìm kiếm
          </button>
        </div>
      )}

      {/* Permission Groups */}
      <div className="space-y-4">
        {filteredGroups.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant mb-3">search_off</span>
            <p className="text-[15px] font-medium text-on-surface">Không tìm thấy quyền nào</p>
            <p className="text-[13px] text-on-surface-variant">Thử từ khóa khác</p>
          </div>
        ) : (
          filteredGroups.map((group) => (
            <PermissionGroup
              key={group.label}
              group={group}
              roles={roles}
              matrixLookup={matrixLookup}
              pendingToggles={pendingToggles}
              isAdmin={isAdmin}
              saving={saving}
              onToggle={handleToggle}
            />
          ))
        )}
      </div>

      {/* Legend */}
      <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-4 rounded-2xl border border-outline-variant/30 bg-surface-container-lowest">
        <div className="flex flex-wrap items-center gap-6">
          <span className="flex items-center gap-2 text-[12px] text-on-surface">
            <span className="flex h-6 w-9 items-center justify-center rounded bg-secondary-container">
              <span className="material-symbols-outlined text-[14px] text-on-secondary-container" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
            </span>
          Đã cấp quyền
        </span>
          <span className="flex items-center gap-2 text-[12px] text-on-surface">
            <span className="flex h-6 w-9 items-center justify-center rounded border border-outline bg-surface-container-lowest">
              <span className="material-symbols-outlined text-[14px] text-outline">radio_button_unchecked</span>
            </span>
          Chưa cấp quyền
        </span>
          <span className="flex items-center gap-2 text-[12px] text-on-surface">
            <span className="flex h-6 w-9 items-center justify-center rounded border-2 border-primary bg-primary-fixed">
              <span className="material-symbols-outlined text-[14px] text-primary" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
            </span>
            Toggle quyền
          </span>
        </div>
        <span className="text-[11px] text-on-surface-variant">M01-F05 · {totalPermCount} quyền</span>
      </div>

      {/* Confirm Dialog for Single Toggle */}
      <ConfirmDialog
        open={!!toggleTarget}
        onClose={() => setToggleTarget(null)}
        onConfirm={confirmToggle}
        title={
          toggleTarget
            ? toggleTarget.currentValue
              ? `Thu hồi quyền "${PERM_LABELS[toggleTarget.permissionName] ?? toggleTarget.permissionName}"?`
              : `Cấp quyền "${PERM_LABELS[toggleTarget.permissionName] ?? toggleTarget.permissionName}"?`
            : ""
        }
        description={
          toggleTarget
            ? `Vai trò: ${ROLE_LABELS[toggleTarget.roleName] ?? toggleTarget.roleName}. Hành động này có thể ảnh hưởng đến khả năng truy cập của người dùng.`
            : ""
        }
        confirmLabel={toggleTarget?.currentValue ? "Thu hồi" : "Cấp quyền"}
        variant={toggleTarget?.currentValue ? "danger" : "primary"}
        loading={saving}
      />

      {/* Confirm Dialog for Bulk Action */}
      <ConfirmDialog
        open={!!bulkActionRole}
        onClose={() => setBulkActionRole(null)}
        onConfirm={confirmBulkAction}
        title={bulkActionRole ? (bulkActionRole.grantAll ? `Cấp tất cả quyền cho ${ROLE_LABELS[bulkActionRole.roleName]}` : `Thu hồi tất cả quyền của ${ROLE_LABELS[bulkActionRole.roleName]}`) : ""}
        description="Hành động này sẽ ảnh hưởng đến tất cả người dùng có vai trò này. Bạn có chắc chắn?"
        confirmLabel={bulkActionRole?.grantAll ? "Cấp tất cả" : "Thu hồi tất cả"}
        variant={bulkActionRole?.grantAll ? "primary" : "danger"}
        loading={saving}
      />
    </div>
  );
}
