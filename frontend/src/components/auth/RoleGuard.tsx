"use client";

import type { ReactNode } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { EmptyState } from "@/components/ui/EmptyState";
import { useAuth } from "@/components/auth/AuthProvider";

export type UserRole = "ADMIN" | "MANAGER" | "STAFF";

type RoleGuardProps = {
  /** Section key for sidebar highlighting (so the denied page still matches its nav entry) */
  activeSection: Parameters<typeof DashboardShell>[0]["activeSection"];
  /** Title shown in the shell header */
  title: string;
  /** Description shown in the shell header */
  description: string;
  /** Roles allowed to view the children. Anything else renders the denied state. */
  allow: UserRole[];
  /** Children rendered when the user has the required role */
  children: ReactNode;
  /** Optional override description for the denied state. Defaults to a generic message. */
  deniedDescription?: string;
};

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: "Quản lý lịch",
  MANAGER: "Trưởng phòng",
  STAFF: "Nhân viên",
};

/**
 * Client-side role guard for dashboard pages.
 *
 * Wraps page content in a DashboardShell and renders a 'no permission'
 * EmptyState when the current user does not have any of the allowed roles.
 *
 * NOTE: This is UI-only. The backend MUST independently enforce authorization
 * on every endpoint. This guard exists to give a friendlier message and hide
 * privileged controls; it is not a security boundary.
 */
export function RoleGuard({
  activeSection,
  title,
  description,
  allow,
  children,
  deniedDescription,
}: RoleGuardProps) {
  const { user } = useAuth();
  const roles = (user?.roles ?? []) as UserRole[];
  const hasAccess = roles.some((r) => allow.includes(r));

  if (!hasAccess) {
    const allowedLabel = allow.map((r) => ROLE_LABELS[r]).join(" hoặc ");
    return (
      <DashboardShell activeSection={activeSection} title={title} description={description}>
        <div className="p-margin-desktop">
          <EmptyState
            icon="lock"
            title="Bạn không có quyền truy cập trang này"
            description={
              deniedDescription ??
              `Trang này chỉ dành cho ${allowedLabel}. Vui lòng liên hệ quản trị viên nếu cần.`
            }
          />
        </div>
      </DashboardShell>
    );
  }

  return <>{children}</>;
}