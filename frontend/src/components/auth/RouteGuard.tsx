"use client";

import type { ReactNode } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useEffect, useMemo } from "react";
import { usePermissions } from "@/hooks/usePermissions";
import { Permission } from "@/lib/permissions";
import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";

/**
 * Map a path under {@code (dashboard)} to the permission set required to
 * render it. Only paths listed here are guarded — anything else falls back
 * to the default rule (any authenticated user can view).
 *
 * <p>To add a new guarded page:
 * <ol>
 *   <li>Add a permission to {@code Permissions.java} + frontend mirror.</li>
 *   <li>Add the route + required permission here.</li>
 *   <li>Wire {@code <RouteGuard>} around the page content in the layout.</li>
 * </ol>
 */
export const ROUTE_PERMISSIONS: Record<string, Permission[]> = {
  // Nhân sự & phân quyền
  "/staff": [Permission.STAFF_VIEW_ALL],
  "/staff/create": [Permission.STAFF_CREATE],
  "/periods": [Permission.PERIOD_VIEW],
  "/periods/create": [Permission.PERIOD_CREATE],

  // Auto-scheduling & cấu hình
  "/auto-scheduling": [Permission.AUTO_SCHEDULE_VIEW],
  "/algorithm-config": [Permission.AUTO_SCHEDULE_CONFIG_VIEW],

  // Duyệt — ADMIN/MANAGER xem/duyệt (LEAVE_VIEW / EXCHANGE_VIEW).
  // STAFF xem yêu cầu của chính mình (LEAVE_CANCEL_SELF / EXCHANGE_CANCEL_SELF).
  // Cả hai vai trò đều có thể vào trang; component sẽ tự filter "chỉ của tôi"
  // cho STAFF dựa trên userId.
  "/leave-requests": [Permission.LEAVE_VIEW, Permission.LEAVE_CANCEL_SELF],
  "/swap-requests": [Permission.EXCHANGE_VIEW, Permission.EXCHANGE_CANCEL_SELF],

  // Báo cáo
  "/reports": [Permission.REPORT_VIEW],

  // Cài đặt
  "/settings": [Permission.APP_CONFIG_VIEW],
  "/settings/roles": [Permission.ROLE_VIEW],

  // Audit + Config
  "/audit-history": [Permission.AUDIT_VIEW],

  // Ngày lễ
  "/holidays": [Permission.HOLIDAY_VIEW],

  // Schedule template
  "/schedule-templates": [Permission.SCHEDULE_TEMPLATE_MANAGE],

  // Notifications — ADMIN/MANAGER xem hết (NOTIFICATION_VIEW).
  // STAFF xem của chính mình (NOTIFICATION_MANAGE_SELF).
  // Frontend `/notifications` đã có flow owner-aware (gọi /me/page khi là staff),
  // nên chỉ cần một trong hai permission là đủ.
  "/notifications": [Permission.NOTIFICATION_VIEW, Permission.NOTIFICATION_MANAGE_SELF],

  // Lịch theo kỳ (M02/M03/M04/M05) — chỉ cần SCHEDULE_VIEW. Component
  // ScheduleByTypePage tự phân nhánh theo role:
  //   - ADMIN/MANAGER: fetch /periods, /staff/active, /schedules/period/{id} đầy đủ
  //   - STAFF: fetch /schedules/me (lịch cá nhân), ẩn hết nút tạo/sửa
  "/duty-24": [Permission.SCHEDULE_VIEW],
  "/all-day": [Permission.SCHEDULE_VIEW],
  "/service-clinic": [Permission.SCHEDULE_VIEW],
  "/expert-clinic": [Permission.SCHEDULE_VIEW],
  "/schedule-summary": [Permission.SCHEDULE_VIEW],
  "/monthly-schedule": [Permission.SCHEDULE_VIEW, Permission.PERIOD_VIEW],

  // Data integrity (admin only)
  "/data-integrity": [Permission.DATA_INTEGRITY_RUN],
};

export function requiredPermissionsForPath(pathname: string): Permission[] {
  if (!pathname) return [];
  // Exact match first
  if (ROUTE_PERMISSIONS[pathname]) {
    return ROUTE_PERMISSIONS[pathname];
  }
  // Otherwise, walk up the path segments looking for a partial match so that
  // /staff/123 or /periods/45/edit still hit the parent route's rule.
  const segments = pathname.split("/").filter(Boolean);
  while (segments.length > 1) {
    segments.pop();
    const candidate = "/" + segments.join("/");
    if (ROUTE_PERMISSIONS[candidate]) {
      return ROUTE_PERMISSIONS[candidate];
    }
  }
  return [];
}

type Props = {
  children: ReactNode;
};

/**
 * Page-level guard for the {@code (dashboard)} route group.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>User not authenticated → redirect to /login (handled by AuthGuard).</li>
 *   <li>User authenticated but missing permission for current path → keep URL,
 *       render EmptyState "Bạn không có quyền truy cập trang này" + button
 *       "Về Tổng quan". NO redirect.</li>
 *   <li>Path not in {@link ROUTE_PERMISSIONS} → render children as-is
 *       (permissive default so we don't accidentally hide new pages).</li>
 * </ul>
 */
export function RouteGuard({ children }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();
  // ROUTE_PERMISSIONS entries are lists of acceptable permissions — user
  // passes if they have ANY one of them. Use canAny (some) instead of can
  // (every) which would require ALL listed permissions.
  const { canAny } = usePermissions();

  const required = useMemo(() => requiredPermissionsForPath(pathname), [pathname]);

  useEffect(() => {
    if (isLoading) return;
    if (!isAuthenticated) {
      router.replace("/login");
    }
  }, [isAuthenticated, isLoading, router]);

  // BUGFIX (was FE#11): rendering an animated "Đang tải..." spinner while
  // the auth state is settling caused a visible flash on every route
  // navigation — the page content would briefly appear, get replaced by
  // the spinner for one frame as the layout re-mounted, then disappear
  // again. Worse, replacing the spinner with the EmptyState / page after
  // a tick also caused a height jump. Replace the spinner with a stable
  // skeleton: same minimum height as the EmptyState (so the layout
  // doesn't reflow), no internal animation that gets interrupted, and
  // a one-shot fade-in via Tailwind so we never flash white-to-blank.
  if (isLoading || !isAuthenticated) {
    return (
      <div
        role="status"
        aria-live="polite"
        aria-busy={isLoading}
        className="flex h-64 items-center justify-center text-on-surface-variant animate-fade-in"
      >
        {isLoading ? "Đang tải..." : "Đang chuyển hướng..."}
      </div>
    );
  }

  if (required.length === 0) {
    return <>{children}</>;
  }

  if (!canAny(required)) {
    return (
      <EmptyState
        icon="lock"
        title="Bạn không có quyền truy cập trang này"
        description="Tài khoản của bạn không được cấp quyền để xem nội dung này. Liên hệ quản trị viên nếu bạn cho rằng đây là nhầm lẫn."
        action={
          <button
            onClick={() => router.replace("/dashboard")}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-primary text-on-primary rounded-lg font-label-md hover:bg-primary/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">home</span>
            Về Tổng quan
          </button>
        }
      />
    );
  }

  return <>{children}</>;
}