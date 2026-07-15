import type { NavigationItem } from "@/types/schedule";
import type { Permission } from "@/lib/permissions";

export type AppSectionKey =
  | "dashboard"
  | "monthly-schedule"
  | "periods"
  | "duty-24"
  | "all-day"
  | "service-clinic"
  | "expert-clinic"
  | "staff"
  | "leave-requests"
  | "shift-swaps"
  | "auto-scheduling"
  | "reports"
  | "holidays"
  | "notifications"
  | "audit-history"
  | "compensation-days"
  | "settings";

export type AppSectionDefinition = {
  key: AppSectionKey;
  label: string;
  href: string;
  icon: string;
  description: string;
  requiredPermissions?: Permission[];
};

export const APP_SECTIONS: AppSectionDefinition[] = [
  {
    key: "dashboard",
    label: "Tổng quan",
    href: "/dashboard",
    icon: "dashboard",
    description: "Theo dõi KPI, cảnh báo vận hành và tác vụ quan trọng trong kỳ lịch hiện hành.",
    requiredPermissions: ["DASHBOARD_VIEW"],
  },
  {
    key: "monthly-schedule",
    label: "Lập lịch tháng",
    href: "/monthly-schedule",
    icon: "calendar_month",
    description: "Điều phối kỳ lịch theo workflow.",
    requiredPermissions: ["SCHEDULE_VIEW", "PERIOD_VIEW"],
  },
  {
    key: "periods",
    label: "Kỳ lịch công tác",
    href: "/periods",
    icon: "event_note",
    description: "M02 — Quản lý kỳ lịch.",
    requiredPermissions: ["PERIOD_VIEW"],
  },
  {
    key: "duty-24",
    label: "Lịch trực 24/24",
    href: "/duty-24",
    icon: "emergency",
    description: "M02 — Lịch trực 24/24.",
    requiredPermissions: ["SCHEDULE_VIEW"],
  },
  {
    key: "all-day",
    label: "Lịch thông tầm",
    href: "/all-day",
    icon: "schedule",
    description: "M03 — Lịch thông tầm.",
    requiredPermissions: ["SCHEDULE_VIEW"],
  },
  {
    key: "service-clinic",
    label: "Lịch PK dịch vụ",
    href: "/service-clinic",
    icon: "medical_services",
    description: "M04 — Lịch phòng khám dịch vụ.",
    requiredPermissions: ["SCHEDULE_VIEW"],
  },
  {
    key: "expert-clinic",
    label: "Lịch PK chuyên gia",
    href: "/expert-clinic",
    icon: "stethoscope",
    description: "M05 — Lịch phòng khám chuyên gia.",
    requiredPermissions: ["SCHEDULE_VIEW"],
  },
  {
    key: "auto-scheduling",
    label: "Tự động xếp lịch",
    href: "/auto-scheduling",
    icon: "auto_mode",
    description: "Thuật toán tạo phương án phân công.",
    requiredPermissions: ["AUTO_SCHEDULE_VIEW", "SCHEDULE_VIEW"],
  },
  {
    key: "staff",
    label: "Nhân sự",
    href: "/staff",
    icon: "groups",
    description: "Quản lý hồ sơ nhân sự.",
    requiredPermissions: ["STAFF_VIEW_ALL"],
  },
  {
    key: "leave-requests",
    label: "Nghỉ phép",
    href: "/leave-requests",
    icon: "event_busy",
    description: "Theo dõi yêu cầu nghỉ phép.",
    requiredPermissions: ["LEAVE_VIEW"],
  },
  {
    key: "shift-swaps",
    label: "Đổi trực",
    href: "/swap-requests",
    icon: "swap_horiz",
    description: "Yêu cầu đổi trực.",
    requiredPermissions: ["EXCHANGE_VIEW"],
  },
  {
    key: "reports",
    label: "Báo cáo",
    href: "/reports",
    icon: "assessment",
    description: "Xem báo cáo kỳ lịch.",
    requiredPermissions: ["REPORT_VIEW"],
  },
  {
    key: "holidays",
    label: "Ngày lễ",
    href: "/holidays",
    icon: "celebration",
    description: "Quản lý ngày lễ.",
    requiredPermissions: ["HOLIDAY_VIEW"],
  },
  {
    key: "notifications",
    label: "Thông báo",
    href: "/notifications",
    icon: "notifications",
    description: "Thông báo hệ thống.",
    requiredPermissions: ["NOTIFICATION_VIEW"],
  },
  {
    key: "audit-history",
    label: "Nhật ký",
    href: "/audit-history",
    icon: "history",
    description: "Tra cứu vết thay đổi.",
    requiredPermissions: ["AUDIT_VIEW"],
  },
  {
    key: "compensation-days",
    label: "Ngày nghỉ bù",
    href: "/compensation-days",
    icon: "event_available",
    description: "Tra cứu lịch sử ngày nghỉ bù.",
    requiredPermissions: ["SCHEDULE_VIEW", "PERIOD_VIEW"],
  },
  {
    key: "settings",
    label: "Cài đặt",
    href: "/settings",
    icon: "settings",
    description: "Cấu hình hệ thống.",
    requiredPermissions: ["APP_CONFIG_VIEW"],
  },
];

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

const LEGACY_ROUTE_MAP: Record<string, AppSectionKey> = {
  "/": "dashboard",
  "/dashboard": "dashboard",
  "/staff": "staff",
  "/staff/create": "staff",
  "/staff/profile": "staff",
  "/duty-24": "duty-24",
  "/all-day": "all-day",
  "/service-clinic": "service-clinic",
  "/expert-clinic": "expert-clinic",
  "/schedule-summary": "monthly-schedule",
  "/periods": "periods",
  "/auto-scheduling": "auto-scheduling",
  "/conflict-check": "monthly-schedule",
  "/leave-requests": "leave-requests",
  "/swap-requests": "shift-swaps",
  "/reports": "reports",
  "/reports/monthly": "reports",
  "/reports/staff": "reports",
  "/reports/conflicts": "reports",
  "/holidays": "holidays",
  "/notifications": "notifications",
  "/audit-history": "audit-history",
  "/audit-logs": "audit-history",
  "/compensation-days": "compensation-days",
  "/settings": "settings",
  "/settings/roles": "settings",
};

export function resolveSectionKey(pathname: string): AppSectionKey {
  if (LEGACY_ROUTE_MAP[pathname]) {
    return LEGACY_ROUTE_MAP[pathname];
  }
  const matched = APP_SECTIONS.find(
    (section) => pathname === section.href || pathname.startsWith(`${section.href}/`),
  );
  return matched?.key ?? "dashboard";
}