"use client";

import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";
import {
  ScheduleByTypePage,
  type ScheduleTypeConfig,
} from "@/components/monthly-schedule/ScheduleByTypePage";

type GuardedScheduleByTypePageProps = {
  config: ScheduleTypeConfig;
  allow?: Array<"ADMIN" | "MANAGER" | "STAFF">;
};

/**
 * Role-checked wrapper for ScheduleByTypePage.
 *
 * All 4 schedule-by-type routes (/duty-24, /all-day, /service-clinic,
 * /expert-clinic) are inside the (dashboard) route group which already
 * provides DashboardShell. This component only does the role check and
 * renders an EmptyState on denial — it does NOT add another shell.
 */
export function GuardedScheduleByTypePage({
  config,
  allow = ["ADMIN", "MANAGER"],
}: GuardedScheduleByTypePageProps) {
  const { user } = useAuth();
  const roles = (user?.roles ?? []) as Array<"ADMIN" | "MANAGER" | "STAFF">;
  const hasAccess = roles.some((r) => allow.includes(r));

  if (!hasAccess) {
    const allowedLabel = allow.join(" hoặc ");
    return (
      <div className="p-4 md:p-6">
        <EmptyState
          icon="lock"
          title="Bạn không có quyền truy cập trang này"
          description={`Trang này chỉ dành cho ${allowedLabel}. Vui lòng liên hệ quản trị viên nếu cần.`}
        />
      </div>
    );
  }

  return <ScheduleByTypePage config={config} />;
}
