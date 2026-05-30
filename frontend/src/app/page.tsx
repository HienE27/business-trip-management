import { AutoSchedulingPanel } from "@/components/dashboard/AutoSchedulingPanel";
import { ConflictPanel } from "@/components/dashboard/ConflictPanel";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";
import { ScheduleModuleCard } from "@/components/dashboard/ScheduleModuleCard";
import { StaffLoadTable } from "@/components/dashboard/StaffLoadTable";
import { DashboardShell } from "@/components/layout/DashboardShell";
import {
  conflicts,
  metrics,
  scheduleModules,
  scheduleRows,
  staffColumns,
  staffLoads,
  workflowSteps,
} from "@/data/schedule-dashboard";

export default function Home() {
  return (
    <DashboardShell
      activeCode="M06"
      description="Tổng hợp 4 loại lịch, cảnh báo xung đột và tự động phân công."
      primaryAction="Xếp lịch tự động"
      secondaryAction="Xuất báo cáo"
      title="Dashboard lịch công tác toàn phòng"
    >
      <div className="grid gap-4 p-5 max-sm:p-3 2xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-4">
          <section className="grid gap-4 md:grid-cols-4">
            {metrics.map((metric) => (
              <MetricCard key={metric.label} metric={metric} />
            ))}
          </section>

          <section className="grid gap-4 lg:grid-cols-4">
            {scheduleModules.map((module) => (
              <ScheduleModuleCard key={module.code} module={module} />
            ))}
          </section>

          <ScheduleMatrix staff={staffColumns} rows={scheduleRows} />
        </div>

        <aside className="grid gap-4 lg:grid-cols-3 2xl:block 2xl:space-y-4">
          <ConflictPanel conflicts={conflicts} />
          <AutoSchedulingPanel steps={workflowSteps} />
          <StaffLoadTable loads={staffLoads} />
        </aside>
      </div>
    </DashboardShell>
  );
}
