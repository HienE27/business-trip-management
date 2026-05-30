import type { ReactNode } from "react";
import { getNavigationItems } from "@/data/schedule-dashboard";
import { AppSidebar } from "./AppSidebar";
import { DashboardHeader } from "./DashboardHeader";

type DashboardShellProps = {
  activeCode: string;
  title: string;
  description: string;
  primaryAction?: string;
  secondaryAction?: string;
  children: ReactNode;
};

export function DashboardShell({
  activeCode,
  title,
  description,
  primaryAction,
  secondaryAction,
  children,
}: DashboardShellProps) {
  return (
    <main className="min-h-screen bg-[#f6f7f9] text-[#111418]">
      <div className="grid min-h-screen grid-cols-[256px_minmax(0,1fr)] max-lg:grid-cols-1">
        <AppSidebar items={getNavigationItems(activeCode)} />
        <section className="flex min-w-0 flex-col bg-[#f6f7f9]">
          <DashboardHeader
            description={description}
            primaryAction={primaryAction}
            secondaryAction={secondaryAction}
            title={title}
          />
          {children}
        </section>
      </div>
    </main>
  );
}
