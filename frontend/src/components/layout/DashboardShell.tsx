"use client";

import { useState, type ReactNode } from "react";
import { getNavigationItems } from "@/data/schedule-dashboard";
import { AppSidebar } from "./AppSidebar";
import { DashboardHeader } from "./DashboardHeader";

type DashboardShellProps = {
  activeCode: string;
  title: string;
  description: string;
  children: ReactNode;
};

export function DashboardShell({
  activeCode,
  title,
  description,
  children,
}: DashboardShellProps) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-screen">
      <AppSidebar
        items={getNavigationItems(activeCode)}
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
      />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden md:ml-[260px]">
        <DashboardHeader
          title={title}
          description={description}
          onMenuToggle={() => setMobileOpen((v) => !v)}
        />
        <main
          className="flex-1 overflow-y-auto p-4 md:p-6 bg-background"
          id="main-content"
          tabIndex={-1}
        >
          <div className="max-w-[1440px] mx-auto flex flex-col gap-4 md:gap-6">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
