"use client";

import { useCallback, useMemo } from "react";
import type { Permission } from "@/lib/permissions";
import { resolvePermissions } from "@/lib/permissions";
import { useAuth } from "@/components/auth/AuthProvider";

/**
 * Single source of truth for "can this user perform action X" on the
 * frontend. Mirrors the backend permission catalog in
 * {@code Permissions.java} — keep the two in sync when adding a new
 * permission.
 */
export function usePermissions() {
  const { user } = useAuth();

  const permissions = useMemo<Set<string>>(
    () => resolvePermissions(user),
    [user],
  );

  const can = useCallback(
    (p: Permission | Permission[]): boolean => {
      const list = Array.isArray(p) ? p : [p];
      return list.every((perm) => permissions.has(perm));
    },
    [permissions],
  );

  const canAny = useCallback(
    (p: Permission[]): boolean => {
      if (p.length === 0) return false;
      return p.some((perm) => permissions.has(perm));
    },
    [permissions],
  );

  return {
    permissions,
    can,
    canAny,
    hasPermission: (perm: string) => permissions.has(perm),
  };
}