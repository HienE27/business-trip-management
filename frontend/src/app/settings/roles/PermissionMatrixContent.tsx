'use client';

/**
 * M01-F05 — Phân quyền hệ thống
 *
 * Hiển thị ma trận vai trò × quyền hệ thống.
 * ADMIN có thể toggle từng cell để cấp/thu hồi quyền.
 *
 * Backend: GET /api/v1/roles/permissions/matrix
 */

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { RolePermissionMatrix } from "@/types/api";
import { useRole } from "@/hooks/useRole";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/ToastProvider";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";

/* ─── Role badge colours ─── */
const ROLE_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  ADMIN:   { bg: "bg-error-container",    text: "text-on-error-container",    border: "border-on-error-container/20" },
  MANAGER: { bg: "bg-primary-fixed",      text: "text-primary",               border: "border-primary/20" },
  STAFF:   { bg: "bg-secondary-container", text: "text-on-secondary-container", border: "border-on-secondary-container/20" },
};

/* ─── Friendly labels ─── */
const ROLE_LABELS: Record<string, string> = {
  ADMIN:   "Quản lý lịch",
  MANAGER: "Trưởng phòng",
  STAFF:   "Nhân viên",
};

const PERM_LABELS: Record<string, string> = {
  // From DB seed: hospital_scheduler_business_final.sql
  "STAFF_READ":             "Xem danh sách nhân sự",
  "STAFF_WRITE":            "Thêm/sửa/xóa nhân sự",
  "SCHEDULE_READ":          "Xem lịch trực",
  "SCHEDULE_WRITE":         "Tạo/sửa/xóa lịch trực",
  "SCHEDULE_PUBLISH":       "Công bố lịch trực",
  "LEAVE_REQUEST_CREATE":   "Tạo yêu cầu nghỉ",
  "LEAVE_REQUEST_REVIEW":   "Duyệt/từ chối yêu cầu nghỉ",
  "SCHEDULE_EXCHANGE_CREATE": "Tạo yêu cầu đổi ca",
  "SCHEDULE_EXCHANGE_REVIEW": "Duyệt/từ chối yêu cầu đổi ca",
  "CONFIG_MANAGE":          "Quản lý cấu hình thuật toán",
  "AUDIT_READ":             "Xem lịch sử thay đổi",
};

export function PermissionMatrixContent() {
  const role = useRole();
  const isAdmin = role === "ADMIN";
  const toast = useToast();

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
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ── Effective granted ── */
  const effectiveGranted = useCallback(
    (roleId: number, permissionId: number) => {
      if (!matrix) return false;
      const key = `${roleId}|${permissionId}`;
      if (pendingToggles.has(key)) return pendingToggles.get(key)!;
      return matrix.matrix.find(
        (e) => e.roleId === roleId && e.permissionId === permissionId,
      )?.granted ?? false;
    },
    [matrix, pendingToggles],
  );

  /* ── Handlers ── */
  const handleToggle = (
    roleId: number, roleName: string,
    permissionId: number, permissionName: string,
  ) => {
    if (!isAdmin) return;
    const current = effectiveGranted(roleId, permissionId);
    setToggleTarget({ roleId, roleName, permissionId, permissionName, currentValue: current });
  };

  const confirmToggle = async () => {
    if (!toggleTarget) return;
    const { roleId, permissionId, currentValue } = toggleTarget;
    const key = `${roleId}|${permissionId}`;
    const newValue = !currentValue;

    setPendingToggles((prev) => { const next = new Map(prev); next.set(key, newValue); return next; });
    setToggleTarget(null);

    try {
      setSaving(true);
      await api.toggleRolePermission({ roleId, permissionId, granted: newValue });
      toast.success(`${newValue ? "Cấp" : "Thu hồi"} quyền thành công.`);
    } catch (e) {
      setPendingToggles((prev) => { const next = new Map(prev); next.delete(key); return next; });
      toast.error(getErrorMessage(e, "Lỗi cập nhật quyền"));
    } finally {
      setSaving(false);
    }
  };

  /* ── Render ── */
  if (loading) {
    return (
      <div className="space-y-4" data-testid="roles-loading">
        <div className="h-6 w-48 animate-pulse rounded-lg bg-surface-variant" />
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full rounded-lg" />
        ))}
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

  const roles = matrix.roles.filter((r) => r.isActive);
  const permissions = matrix.permissions.filter((p) => p.name in PERM_LABELS);

  return (
    <div className="space-y-6" data-testid="roles-matrix">
      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-[18px] font-semibold text-on-surface">
            Ma trận phân quyền hệ thống
          </h2>
          <p className="mt-1 text-[13px] text-on-surface-variant">
            {isAdmin
              ? "Nhấn vào ô để cấp hoặc thu hồi quyền cho từng vai trò."
              : "Chỉ Quản lý lịch mới có thể chỉnh sửa phân quyền."}
          </p>
        </div>
        {saving && (
          <span className="flex items-center gap-2 text-[13px] text-on-surface-variant">
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            Đang lưu…
          </span>
        )}
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded-xl border border-outline-variant shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-outline-variant">
              <th
                scope="col"
                className="sticky left-0 z-10 min-w-[200px] bg-surface-container-low px-4 py-3 text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant"
              >
                Quyền
              </th>
              {roles.map((r) => (
                <th
                  key={r.id}
                  scope="col"
                  className="min-w-[160px] px-4 py-3 text-center text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant"
                >
                  <div className={`inline-flex flex-col items-center gap-1 rounded-lg border px-3 py-1.5 ${ROLE_COLORS[r.name]?.bg ?? "bg-surface-container-low"} ${ROLE_COLORS[r.name]?.border ?? "border-outline-variant"}`}>
                    <span className={ROLE_COLORS[r.name]?.text ?? "text-on-surface"}>
                      {ROLE_LABELS[r.name] ?? r.name}
                    </span>
                    <span className={`text-[10px] font-normal opacity-70 ${ROLE_COLORS[r.name]?.text ?? "text-on-surface-variant"}`}>
                      {r.name}
                    </span>
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/30 bg-surface-container-lowest">
            {permissions.length === 0 && (
              <tr>
                <td colSpan={roles.length + 1} className="px-4 py-8 text-center text-[13px] text-on-surface-variant" role="status">
                  Chưa có quyền nào được định nghĩa.
                </td>
              </tr>
            )}
            {permissions.map((perm) => (
              <tr key={perm.id} className="hover:bg-surface-container-low transition-colors group">
                <td className="sticky left-0 z-10 bg-surface-container-lowest px-4 py-3">
                  <div>
                    <p className="text-[13px] font-medium text-on-surface">
                      {PERM_LABELS[perm.name] ?? perm.name}
                    </p>
                    <p className="text-[11px] text-on-surface-variant">{perm.description ?? perm.name}</p>
                  </div>
                </td>
                {roles.map((r) => {
                  const granted = effectiveGranted(r.id, perm.id);
                  const pendingKey = `${r.id}|${perm.id}`;
                  const isPending = pendingToggles.has(pendingKey);
                  return (
                    <td key={r.id} className="px-4 py-3 text-center">
                      <button
                        type="button"
                        onClick={() => handleToggle(r.id, r.name, perm.id, perm.name)}
                        disabled={!isAdmin || saving}
                        aria-label={`${granted ? "Thu hồi" : "Cấp"} quyền ${PERM_LABELS[perm.name] ?? perm.name} cho ${ROLE_LABELS[r.name] ?? r.name}`}
                        data-testid={`toggle-${r.id}-${perm.id}`}
                        className={[
                          "mx-auto flex h-8 w-14 items-center justify-center rounded-full border-2 text-[13px] font-semibold transition-all",
                          granted
                            ? "border-secondary bg-secondary-container text-on-secondary-container hover:border-error hover:bg-error-container hover:text-on-error-container"
                            : "border-outline-variant bg-surface-container-low text-outline hover:border-primary hover:bg-primary-fixed hover:text-primary",
                          (!isAdmin || saving) && "cursor-not-allowed opacity-60",
                        ].join(" ")}
                      >
                        {isPending ? (
                          <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
                        ) : granted ? (
                          <span className="material-symbols-outlined text-[16px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                            check_circle
                          </span>
                        ) : (
                          <span className="material-symbols-outlined text-[16px]">remove_circle_outline</span>
                        )}
                      </button>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-4 text-[12px] text-on-surface-variant">
        <span className="flex items-center gap-1.5">
          <span className="material-symbols-outlined text-[14px] text-secondary" aria-hidden="true">check_circle</span>
          Đã cấp quyền
        </span>
        <span className="flex items-center gap-1.5">
          <span className="material-symbols-outlined text-[14px] text-outline" aria-hidden="true">remove_circle_outline</span>
          Chưa cấp quyền
        </span>
        <span className="ml-auto italic opacity-70">M01-F05 · Phân quyền hệ thống</span>
      </div>

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
    </div>
  );
}
