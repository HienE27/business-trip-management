import type { NavigationItem } from "@/types/schedule";

export type AppSectionKey =
  | "dashboard"
  | "monthly-schedule"
  | "staff"
  | "leave-requests"
  | "shift-swaps"
  | "auto-scheduling"
  | "reports"
  | "notifications"
  | "audit-history"
  | "settings";

export type AppSectionDefinition = {
  key: AppSectionKey;
  label: string;
  href: string;
  icon: string;
  description: string;
};

export const APP_SECTIONS: AppSectionDefinition[] = [
  {
    key: "dashboard",
    label: "Tổng quan",
    href: "/dashboard",
    icon: "dashboard",
    description: "Theo dõi KPI, cảnh báo vận hành và tác vụ quan trọng trong kỳ lịch hiện hành.",
  },
  {
    key: "monthly-schedule",
    label: "Lập lịch tháng",
    href: "/monthly-schedule",
    icon: "calendar_month",
    description: "Điều phối kỳ lịch theo workflow: auto schedule, conflict check, review, publish và export.",
  },
  {
    key: "auto-scheduling",
    label: "Tự động xếp lịch",
    href: "/auto-scheduling",
    icon: "auto_mode",
    description: "Chạy thuật toán tạo phương án phân công, xem trước và áp dụng cho kỳ lịch.",
  },
  {
    key: "staff",
    label: "Nhân sự",
    href: "/staff",
    icon: "groups",
    description: "Quản lý hồ sơ nhân sự, trạng thái hoạt động, chuyên môn và dữ liệu phục vụ lập lịch.",
  },
  {
    key: "leave-requests",
    label: "Nghỉ phép",
    href: "/leave-requests",
    icon: "event_busy",
    description: "Theo dõi yêu cầu nghỉ phép, phê duyệt và các ảnh hưởng tới kỳ lịch đang vận hành.",
  },
  {
    key: "shift-swaps",
    label: "Đổi trực",
    href: "/swap-requests",
    icon: "swap_horiz",
    description: "Quản lý yêu cầu đổi trực, đánh giá rủi ro và phê duyệt trên lịch đã công bố.",
  },
  {
    key: "reports",
    label: "Báo cáo",
    href: "/reports",
    icon: "assessment",
    description: "Xem báo cáo kỳ lịch, tải nhân sự và thống kê xung đột theo góc nhìn vận hành.",
  },
  {
    key: "notifications",
    label: "Thông báo",
    href: "/notifications",
    icon: "notifications",
    description: "Nhận thông tin phát sinh từ lịch trực, đổi trực, nghỉ phép và các thông báo hệ thống.",
  },
  {
    key: "audit-history",
    label: "Nhật ký",
    href: "/audit-history",
    icon: "history",
    description: "Tra cứu vết thay đổi và hành động vận hành trên toàn hệ thống.",
  },
  {
    key: "settings",
    label: "Cài đặt",
    href: "/settings",
    icon: "settings",
    description: "Điểm vào cho thiết lập hệ thống và khu vực cấu hình đang được hoàn thiện.",
  },
];

const LEGACY_ROUTE_MAP: Record<string, AppSectionKey> = {
  "/": "dashboard",
  "/dashboard": "dashboard",
  "/staff": "staff",
  "/staff/create": "staff",
  "/staff/profile": "staff",
  "/duty-24": "monthly-schedule",
  "/all-day": "monthly-schedule",
  "/service-clinic": "monthly-schedule",
  "/expert-clinic": "monthly-schedule",
  "/schedule-summary": "monthly-schedule",
  "/auto-scheduling": "auto-scheduling",
  "/conflict-check": "monthly-schedule",
  "/leave-requests": "leave-requests",
  "/swap-requests": "shift-swaps",
  "/reports": "reports",
  "/reports/monthly": "reports",
  "/reports/staff": "reports",
  "/reports/conflicts": "reports",
  "/notifications": "notifications",
  "/audit-history": "audit-history",
  "/audit-logs": "audit-history",
  "/settings": "settings",
};

export function resolveSectionKey(pathname: string): AppSectionKey {
  if (LEGACY_ROUTE_MAP[pathname]) {
    return LEGACY_ROUTE_MAP[pathname];
  }

  const matched = APP_SECTIONS.find((section) => pathname === section.href || pathname.startsWith(`${section.href}/`));
  return matched?.key ?? "dashboard";
}

export function getNavigationItems(activeSection: AppSectionKey): NavigationItem[] {
  return APP_SECTIONS.map((section) => ({
    code: section.key,
    label: section.label,
    href: section.href,
    icon: section.icon,
    active: section.key === activeSection,
  }));
}

export function getSectionMeta(sectionKey: AppSectionKey) {
  return APP_SECTIONS.find((section) => section.key === sectionKey) ?? APP_SECTIONS[0];
}
