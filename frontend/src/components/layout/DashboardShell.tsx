"use client";

import { useState, type ReactNode } from "react";
import type { AppSectionKey } from "@/data/navigation";
import { getNavigationItems } from "@/data/navigation";
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

  return (
    <div className="flex min-h-screen">
      {/* Skip to main content link */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[9999] focus:px-4 focus:py-2 focus:bg-primary focus:text-on-primary focus:rounded-lg focus:font-medium focus:shadow-lg"
      >
        Chuyển đến nội dung chính
      </a>
      <AppSidebar
        items={getNavigationItems(activeSection)}
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
      />
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
