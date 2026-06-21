/**
 * Centralized role translation utilities.
 * Maps technical role values (ADMIN/MANAGER/STAFF) to business-friendly Vietnamese labels.
 * These functions are used ONLY for display purposes — never for API calls or data storage.
 */

export type RoleCode = "ADMIN" | "MANAGER" | "STAFF";

export const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Quản lý lịch",
  MANAGER: "Trưởng phòng",
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
