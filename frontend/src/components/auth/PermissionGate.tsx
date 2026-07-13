"use client";

import type { ReactNode } from "react";
import { usePermissions } from "@/hooks/usePermissions";
import type { Permission } from "@/lib/permissions";

type Props = {
  /** Permission(s) the user must have to render {@link children}. */
  required: Permission | Permission[];
  /** When true, user must have ALL permissions (default true). */
  requireAll?: boolean;
  /** Element to render when the user lacks the permission. Default: null. */
  fallback?: ReactNode;
  children: ReactNode;
};

/**
 * Hide children entirely (no disable, no fade) when the current user lacks
 * the required permission. Use this around buttons, table action icons,
 * context-menu items, dropdown actions, forms, and sensitive data columns
 * — anywhere the requirement is "don't even show this" rather than
 * "show but greyed out".
 *
 * <p>For a full-page 403 fallback (when an entire page requires a
 * permission), use {@code RouteGuard} in {@code (dashboard)/layout.tsx}.
 */
export function PermissionGate({
  required,
  requireAll = true,
  fallback = null,
  children,
}: Props) {
  const { can, canAny } = usePermissions();
  const list = Array.isArray(required) ? required : [required];
  const allowed = requireAll ? can(list) : canAny(list);

  if (!allowed) {
    return <>{fallback}</>;
  }
  return <>{children}</>;
}