"use client";

import { useAuth } from "@/components/auth/AuthProvider";

export type UserRole = "ADMIN" | "MANAGER" | "STAFF";

export function useRole(): UserRole {
  const { user } = useAuth();
  const roles = user?.roles ?? [];
  if (roles.includes("ADMIN")) return "ADMIN";
  if (roles.includes("MANAGER")) return "MANAGER";
  return "STAFF";
}

export function canManage(role: UserRole): boolean {
  return role === "ADMIN" || role === "MANAGER";
}

export function canApprove(role: UserRole): boolean {
  return role === "ADMIN";
}

export function canEditSchedule(role: UserRole): boolean {
  return role === "ADMIN" || role === "MANAGER";
}

export function canDeleteSchedule(role: UserRole): boolean {
  return role === "ADMIN";
}

export function canViewAuditLog(role: UserRole): boolean {
  return role === "ADMIN" || role === "MANAGER";
}
