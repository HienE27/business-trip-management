"use client";

import { usePathname } from "next/navigation";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { resolveSectionKey, getSectionMeta } from "@/data/navigation";
import { RouteGuard } from "@/components/auth/RouteGuard";

/**
 * Shared layout for all dashboard pages.
 *
 * This layout mounts DashboardShell ONCE when the user enters any
 * protected route. Subsequent page navigations only swap {children},
 * so the sidebar, header, and scroll positions persist — no remount.
 *
 * Pages wrapped by this layout must NOT import DashboardShell or
 * RoleGuard themselves; they only return the page content.
 *
 * Auth pages (login, register) live in app/(auth)/ which has its own
 * minimal layout and never reaches this file.
 */
export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const activeSection = resolveSectionKey(pathname);
  const meta = getSectionMeta(activeSection);

  // Filter the sidebar by permissions, then mount RouteGuard around
  // children so any direct URL access without a permission lands on
  // the in-page EmptyState 403 instead of silently rendering the page.
  return (
    <DashboardShell
      activeSection={activeSection}
      title={meta.label}
      description={meta.description}
    >
      <RouteGuard>{children}</RouteGuard>
    </DashboardShell>
  );
}
