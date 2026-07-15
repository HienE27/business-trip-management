"use client";

import { useMemo, useState, type ReactNode } from "react";
import type { AppSectionKey } from "@/data/navigation";
import { APP_SECTIONS, getNavigationItems } from "@/data/navigation";
import { usePermissions } from "@/hooks/usePermissions";
import { AppSidebar } from "./AppSidebar";
import { DashboardHeader } from "./DashboardHeader";

type DashboardShellProps = {
  activeSection: AppSectionKey;
  title: string;
  description: string;
  children: ReactNode;
};

export function DashboardShell({
  activeSection,
  title,
  description,
  children,
}: DashboardShellProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { can, canAny } = usePermissions();

  // BUGFIX: usePermissions does not expose isLoading — fall back to the full
  // nav when permissions haven't been computed yet (same defensive default
  // as before, just without the broken hook field).
  const visibleItems = useMemo(() => {
    const visibleHrefs = new Set(
      APP_SECTIONS.filter((section) => {
        if (!section.requiredPermissions || section.requiredPermissions.length === 0) {
          return true;
        }
        // Yêu cầu TẤT CẢ permissions trong `requiredPermissions` (AND). Một trang
        // lịch theo kỳ có thể cần cả SCHEDULE_VIEW (xem được) + PERIOD_VIEW
        // (cần danh sách kỳ lịch). Nếu dùng `canAny` (OR) thì STAFF có
        // SCHEDULE_VIEW sẽ thấy menu và click vào sẽ 403.
        return section.requiredPermissions.every((perm) => can(perm));
      }).map((section) => section.href),
    );
    return getNavigationItems(activeSection).filter((item) => visibleHrefs.has(item.href));
  }, [canAny, activeSection]);

  return (
    <div className="flex min-h-screen">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[9999] focus:px-4 focus:py-2 focus:bg-primary focus:text-on-primary focus:rounded-lg focus:font-medium focus:shadow-lg"
      >
        Chuyển đến nội dung chính
      </a>
      <AppSidebar
        items={visibleItems}
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
      />
      {mobileOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 lg:hidden animate-fade-in"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden lg:ml-60">
        <DashboardHeader
          title={title}
          description={description}
          mobileOpen={mobileOpen}
          onMenuToggle={() => setMobileOpen((v) => !v)}
        />
        <main
          className="flex-1 overflow-y-auto p-4 md:p-6 bg-background"
          id="main-content"
          tabIndex={-1}
        >
          <div className="flex flex-col gap-3 md:gap-4 max-w-[1440px] mx-auto w-full">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}