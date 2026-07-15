"use client";

import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";
import {
  ScheduleByTypePage,
  type ScheduleTypeConfig,
} from "@/components/monthly-schedule/ScheduleByTypePage";
import { StaffScheduleView } from "@/components/monthly-schedule/StaffScheduleView";

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
 *   <li><b>STAFF</b> → {@link StaffScheduleView} (chỉ xem lịch cá nhân
 *       qua {@code /api/v1/schedules/me}, không cần PERIOD_VIEW/STAFF_VIEW_ALL,
 *       toàn bộ nút chỉnh sửa bị ẩn theo M01-F05 "xem lịch cá nhân").</li>
 * </ul>
 *
 * <p>Route group (dashboard) đã cung cấp DashboardShell; component này chỉ
 * chọn view phù hợp theo role rồi render nội dung — không thêm shell.
 */
export function GuardedScheduleByTypePage({
  config,
  // Theo tài liệu M01-F05: cả 3 role đều có thể xem lịch, chỉ khác mức
  // chi tiết (cá nhân vs toàn kỳ). Hành vi cụ thể phân nhánh dưới đây.
  allow = ["ADMIN", "MANAGER", "STAFF"],
}: GuardedScheduleByTypePageProps) {
  const { user } = useAuth();
  const roles = (user?.roles ?? []) as Array<"ADMIN" | "MANAGER" | "STAFF">;
  const hasAccess = roles.some((r) => allow.includes(r));
  const isStaffOnly = roles.length > 0 && roles.every((r) => r === "STAFF");

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

  // STAFF (và không có role nào khác) → view read-only đơn giản.
  // User đồng thời có ADMIN/MANAGER (hiếm nhưng có thể) → dùng view đầy đủ.
  if (isStaffOnly) {
    return <StaffScheduleView config={config} />;
  }

  return <ScheduleByTypePage config={config} />;
}
