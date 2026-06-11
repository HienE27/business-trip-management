import type { ReactNode } from "react";
import type { AppSectionKey } from "@/data/navigation";
import { DashboardShell } from "@/components/layout/DashboardShell";

type WorkflowShellProps = {
  section: AppSectionKey;
  title: string;
  description: string;
  children: ReactNode;
};

export function WorkflowShell({ section, title, description, children }: WorkflowShellProps) {
  return (
    <DashboardShell activeSection={section} title={title} description={description}>
      {children}
    </DashboardShell>
  );
}
