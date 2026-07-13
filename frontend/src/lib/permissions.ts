/**
 * Mirror của {@code backend/src/main/java/com/hospital/scheduler/security/Permissions.java}.
 *
 * <p>Dùng string literal cố định (không dùng TS enum runtime) để:
 * <ul>
 *   <li>Có thể dùng câu lệnh {@code Permission.STAFF_VIEW} ở cả server và client.</li>
 *   <li>Tránh được enum runtime bị strip khi build Next.js.</li>
 * </ul>
 *
 * <p>Bất kỳ permission nào thêm mới phải được thêm vào:
 * <ol>
 *   <li>{@code backend/src/main/java/com/hospital/scheduler/security/Permissions.java}</li>
 *   <li>File này (giữ đồng bộ)</li>
 *   <li>Ma trận role-permission trong {@code DataSeeder.java} (ADMIN = all, MANAGER = all - adminOnly, STAFF = staff set)</li>
 * </ol>
 */
export const Permission = {
  // Dashboard
  DASHBOARD_VIEW: "DASHBOARD_VIEW",
  DASHBOARD_AGGREGATE: "DASHBOARD_AGGREGATE",

  // Staff
  STAFF_VIEW: "STAFF_VIEW",
  STAFF_CREATE: "STAFF_CREATE",
  STAFF_UPDATE: "STAFF_UPDATE",
  STAFF_DELETE: "STAFF_DELETE",
  STAFF_IMPORT: "STAFF_IMPORT",
  STAFF_EXPORT: "STAFF_EXPORT",

  // Role / Permission matrix
  ROLE_VIEW: "ROLE_VIEW",
  ROLE_EDIT: "ROLE_EDIT",

  // Schedule period
  PERIOD_VIEW: "PERIOD_VIEW",
  PERIOD_CREATE: "PERIOD_CREATE",
  PERIOD_UPDATE: "PERIOD_UPDATE",
  PERIOD_DELETE: "PERIOD_DELETE",
  PERIOD_PUBLISH: "PERIOD_PUBLISH",
  PERIOD_ARCHIVE: "PERIOD_ARCHIVE",

  // Schedule (L01–L04)
  SCHEDULE_VIEW: "SCHEDULE_VIEW",
  SCHEDULE_CREATE: "SCHEDULE_CREATE",
  SCHEDULE_UPDATE: "SCHEDULE_UPDATE",
  SCHEDULE_DELETE: "SCHEDULE_DELETE",
  SCHEDULE_PUBLISH: "SCHEDULE_PUBLISH",
  SCHEDULE_EXPORT: "SCHEDULE_EXPORT",

  // Auto scheduling
  AUTO_SCHEDULE_VIEW: "AUTO_SCHEDULE_VIEW",
  AUTO_SCHEDULE_RUN: "AUTO_SCHEDULE_RUN",
  AUTO_SCHEDULE_APPLY: "AUTO_SCHEDULE_APPLY",
  AUTO_SCHEDULE_CONFIG_VIEW: "AUTO_SCHEDULE_CONFIG_VIEW",
  AUTO_SCHEDULE_CONFIG_EDIT: "AUTO_SCHEDULE_CONFIG_EDIT",

  // Leave
  LEAVE_VIEW: "LEAVE_VIEW",
  LEAVE_CREATE: "LEAVE_CREATE",
  LEAVE_APPROVE: "LEAVE_APPROVE",
  LEAVE_CANCEL_SELF: "LEAVE_CANCEL_SELF",

  // Exchange (swap)
  EXCHANGE_VIEW: "EXCHANGE_VIEW",
  EXCHANGE_CREATE: "EXCHANGE_CREATE",
  EXCHANGE_APPROVE: "EXCHANGE_APPROVE",
  EXCHANGE_CANCEL_SELF: "EXCHANGE_CANCEL_SELF",

  // Reports
  REPORT_VIEW: "REPORT_VIEW",
  REPORT_EXPORT: "REPORT_EXPORT",

  // Holiday
  HOLIDAY_VIEW: "HOLIDAY_VIEW",
  HOLIDAY_CREATE: "HOLIDAY_CREATE",
  HOLIDAY_UPDATE: "HOLIDAY_UPDATE",
  HOLIDAY_DELETE: "HOLIDAY_DELETE",

  // Notification
  NOTIFICATION_VIEW: "NOTIFICATION_VIEW",
  NOTIFICATION_CREATE: "NOTIFICATION_CREATE",
  NOTIFICATION_BROADCAST: "NOTIFICATION_BROADCAST",
  NOTIFICATION_MANAGE_SELF: "NOTIFICATION_MANAGE_SELF",

  // Audit / System log / Config / Integrity
  AUDIT_VIEW: "AUDIT_VIEW",
  AUDIT_DELETE: "AUDIT_DELETE",
  AUDIT_EXPORT: "AUDIT_EXPORT",
  SYSTEM_LOG_VIEW: "SYSTEM_LOG_VIEW",
  APP_CONFIG_VIEW: "APP_CONFIG_VIEW",
  APP_CONFIG_EDIT: "APP_CONFIG_EDIT",
  DATA_INTEGRITY_RUN: "DATA_INTEGRITY_RUN",

  // Specialty / Shift Type / Schedule Template
  SPECIALTY_MANAGE: "SPECIALTY_MANAGE",
  SHIFT_TYPE_MANAGE: "SHIFT_TYPE_MANAGE",
  SCHEDULE_TEMPLATE_MANAGE: "SCHEDULE_TEMPLATE_MANAGE",
} as const;

export type Permission = (typeof Permission)[keyof typeof Permission];

/**
 * Bảng mặc định role -> permission set — dùng làm fallback khi JWT không mang
 * permissions (token cũ trước khi deploy RBAC v2). Frontend vẫn nên ưu tiên
 * permissions lấy từ `useAuth().user?.permissions`.
 *
 * <p>Nguồn sự thật: backend {@code Permissions.staffPermissions()} / {@code managerPermissions()}.
 */
export const RoleDefaultPermissions: Record<string, Permission[]> = {
  ADMIN: Object.values(Permission),
  MANAGER: Object.values(Permission).filter(
    (p) =>
      p !== Permission.ROLE_EDIT &&
      p !== Permission.AUDIT_DELETE &&
      p !== Permission.SYSTEM_LOG_VIEW &&
      p !== Permission.DATA_INTEGRITY_RUN &&
      p !== Permission.AUTO_SCHEDULE_CONFIG_EDIT &&
      p !== Permission.APP_CONFIG_EDIT,
  ),
  STAFF: [
    Permission.DASHBOARD_VIEW,
    Permission.STAFF_VIEW,
    Permission.SCHEDULE_VIEW,
    Permission.LEAVE_VIEW,
    Permission.LEAVE_CREATE,
    Permission.LEAVE_CANCEL_SELF,
    Permission.EXCHANGE_VIEW,
    Permission.EXCHANGE_CREATE,
    Permission.EXCHANGE_CANCEL_SELF,
    Permission.NOTIFICATION_VIEW,
    Permission.NOTIFICATION_MANAGE_SELF,
  ],
};

/**
 * Resolve the effective permission set for a user:
 *   1. If the JWT explicitly carries `permissions`, use that.
 *   2. Else fall back to the highest-privilege role's default set.
 *
 * <p>This lets the frontend degrade gracefully when a token issued before the
 * RBAC v2 rollout (no permissions claim) is still in flight.
 */
export function resolvePermissions(
  user: { roles?: string[]; permissions?: string[] } | null | undefined,
): Set<string> {
  if (!user) return new Set();
  if (Array.isArray(user.permissions) && user.permissions.length > 0) {
    return new Set(user.permissions);
  }
  const roles = user.roles ?? [];
  const priority = ["ADMIN", "MANAGER", "STAFF"];
  for (const role of priority) {
    if (roles.includes(role)) {
      return new Set(RoleDefaultPermissions[role] ?? []);
    }
  }
  return new Set();
}