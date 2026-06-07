import type { ReactNode } from "react";
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
  return (
    <div className="flex min-h-screen bg-background text-on-surface">
      <AppSidebar items={getNavigationItems(activeCode)} />
      <div className="flex-1 flex flex-col md:ml-[260px] min-w-0">
        <DashboardHeader title={title} description={description} />
        <main
          className="flex-1 overflow-y-auto p-6 bg-background"
          id="main-content"
          tabIndex={-1}
        >
          <div className="max-w-[1440px] mx-auto flex flex-col gap-6">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
