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
 * Role-checked wrapper cho các trang lịch theo kỳ (/duty-24, /all-day,
 * /service-clinic, /expert-clinic, /schedule-summary).
 *
 * <p>Phân nhánh theo role:
 * <ul>
 *   <li><b>ADMIN/MANAGER</b> → {@link ScheduleByTypePage} (toàn quyền:
 *       chọn kỳ, tạo/sửa/xoá ca, công bố, xuất Excel, gửi thông báo).</li>
 *   <li><b>STAFF</b> → {@link ScheduleByTypePage} (toàn quyền xem lịch,
 *       nhưng các thao tác chỉnh sửa bị ẩn theo role — xem canManage/canEditSchedule
 *       trong useRole.ts).</li>
 * </ul>
 */
export function GuardedScheduleByTypePage({
  config,
  // Theo tài liệu M01-F05: cả 3 role đều có thể xem lịch.
  allow = ["ADMIN", "MANAGER", "STAFF"],
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

  // Tất cả role đều dùng ScheduleByTypePage — quyền chỉnh sửa được kiểm soát
  // trong component qua canManage(role), canEditSchedule(role).
  return <ScheduleByTypePage config={config} />;
}
