import type { NavigationItem } from "@/types/schedule";

export type AppSectionKey =
  | "dashboard"
  | "monthly-schedule"
  | "periods"
  | "duty-24"
  | "all-day"
  | "service-clinic"
  | "expert-clinic"
  | "staff"
  | "specialties"
  | "leave-requests"
  | "shift-swaps"
  | "requirements"
  | "auto-scheduling"
  | "reports"
  | "holidays"
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
    key: "periods",
    label: "Kỳ lịch công tác",
    href: "/periods",
    icon: "event_note",
    description: "M02 — Quản lý các kỳ lịch theo tháng: tạo mới, chỉnh sửa, công bố và lưu trữ.",
  },
  {
    key: "duty-24",
    label: "Lịch trực 24/24",
    href: "/duty-24",
    icon: "emergency",
    description: "M02 — Xếp lịch trực 24/24 theo tháng. Hệ thống tự động tính ngày nghỉ bù sau ca trực.",
  },
  {
    key: "all-day",
    label: "Lịch thông tầm",
    href: "/all-day",
    icon: "schedule",
    description: "M03 — Xếp lịch thông tầm theo tháng. Ca liên tục không nghỉ trưa, không trùng lịch trực 24/24.",
  },
  {
    key: "service-clinic",
    label: "Lịch PK dịch vụ",
    href: "/service-clinic",
    icon: "medical_services",
    description: "M04 — Xếp lịch phòng khám dịch vụ theo tháng. Không trùng lịch phòng khám chuyên gia.",
  },
  {
    key: "expert-clinic",
    label: "Lịch PK chuyên gia",
    href: "/expert-clinic",
    icon: "stethoscope",
    description: "M05 — Xếp lịch phòng khám chuyên gia theo chuyên khoa. Lọc theo Ngoại, Nội, Sản, Nhi, Mắt, Răng…",
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
    key: "specialties",
    label: "Chuyên khoa",
    href: "/staff",
    icon: "stethoscope",
    description: "Quản lý danh sách chuyên khoa trong bệnh viện. Các chuyên khoa được dùng để phân loại nhân sự và lịch trực.",
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
    key: "requirements",
    label: "Yêu cầu nhân sự",
    href: "/requirements",
    icon: "assignment",
    description: "M07-F01 — Cấu hình số nhân sự cần thiết cho từng ngày và loại ca trong kỳ lịch.",
  },
  {
    key: "reports",
    label: "Báo cáo",
    href: "/reports",
    icon: "assessment",
    description: "Xem báo cáo kỳ lịch, tải nhân sự và thống kê xung đột theo góc nhìn vận hành.",
  },
  {
    key: "holidays",
    label: "Ngày lễ",
    href: "/holidays",
    icon: "celebration",
    description: "Quản lý ngày lễ quốc gia và ngày nghỉ đặc biệt trong năm.",
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
  "/staff/specialties": "specialties",
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
  "/requirements": "requirements",
  "/reports": "reports",
  "/reports/monthly": "reports",
  "/reports/staff": "reports",
  "/reports/conflicts": "reports",
  "/holidays": "holidays",
  "/notifications": "notifications",
  "/audit-history": "audit-history",
  "/audit-logs": "audit-history",
  "/settings": "settings",
  "/settings/roles": "settings",
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
