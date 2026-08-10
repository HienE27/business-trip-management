/**
 * Centralized role translation utilities.
 * Maps technical role values (ADMIN/MANAGER/STAFF) to business-friendly Vietnamese labels.
 * These functions are used ONLY for display purposes — never for API calls or data storage.
 */

export type RoleCode = "ADMIN" | "MANAGER" | "STAFF";

export const ROLE_LABELS: Record<string, string> = {
  // BUGFIX (was RBAC#3): previous mapping was inverted — `ADMIN` was shown as
  // "Quản lý lịch" (should be "Trưởng phòng") and `MANAGER` was shown as
  // "Trưởng phòng" (should be "Quản lý lịch"). The two roles are distinct
  // business roles per `PROJECT_CONTEXT.mdc`:
  //   • ADMIN (Trưởng phòng) = full-system access
  //   • MANAGER (Quản lý lịch) = schedule planner + approver
  // Swapping the labels caused user confusion — a manager would see
  // "Trưởng phòng" and assume they had admin powers.
  ADMIN: "Trưởng phòng",
  MANAGER: "Quản lý lịch",
  STAFF: "Nhân viên",
};

export function getRoleLabel(roles: string[]): string {
  if (roles.includes("ADMIN")) return ROLE_LABELS.ADMIN;
  if (roles.includes("MANAGER")) return ROLE_LABELS.MANAGER;
  if (roles.includes("STAFF")) return ROLE_LABELS.STAFF;
  return "Nhân sự";
}

export function getRoleBadge(roles: string[]): "primary" | "secondary" | "neutral" {
  if (roles.includes("ADMIN")) return "primary";
  if (roles.includes("MANAGER")) return "secondary";
  return "neutral";
}

export function translateRoleToDisplay(role: string): string {
  const normalized = role.toUpperCase() as RoleCode;
  return ROLE_LABELS[normalized] ?? role;
}

export function translateRoleListToDisplay(roles: string[]): string[] {
  return roles.map(translateRoleToDisplay);
}
