import { AllocationStats } from "@/components/dashboard/AllocationStats";
import { ConflictPanel } from "@/components/dashboard/ConflictPanel";
import { DashboardCalendar } from "@/components/dashboard/DashboardCalendar";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { SwapRequestsPanel } from "@/components/dashboard/SwapRequestsPanel";
import { DashboardShell } from "@/components/layout/DashboardShell";
import {
  allocationStats,
  conflicts,
  metrics,
  swapRequests,
} from "@/data/schedule-dashboard";

export default function Home() {
  return (
    <DashboardShell
      activeCode="HOME"
      description="Thông tin điều phối nhân sự ngày hôm nay"
      title="Tổng quan"
    >
      {/* Page Header */}
      <div className="flex items-center justify-between gap-4">
        <div />
        <div className="flex gap-3">
          <button
            className="h-10 px-4 bg-surface-container-lowest border border-outline-variant text-on-surface rounded-lg font-label-md flex items-center gap-2 hover:bg-surface-container-low transition-colors shadow-[0_1px_2px_0_rgba(0,0,0,0.05)]"
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-sm">download</span>
            Xuất báo cáo
          </button>
          <button
            className="h-10 px-4 bg-primary text-on-primary rounded-lg font-label-md flex items-center gap-2 hover:bg-primary/90 transition-colors shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)]"
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-sm">add</span>
            Xếp lịch mới
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <section className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {metrics.map((metric) => (
          <MetricCard key={metric.label} metric={metric} />
        ))}
      </section>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 h-full min-h-[600px]">
        {/* Left: Calendar Overview */}
        <div className="col-span-1 lg:col-span-8">
          <DashboardCalendar />
        </div>

        {/* Right: Widgets */}
        <div className="col-span-1 lg:col-span-4 flex flex-col gap-gutter">
          <ConflictPanel conflicts={conflicts} />
          <SwapRequestsPanel requests={swapRequests} />
          <AllocationStats stats={allocationStats} />
        </div>
      </div>
    </DashboardShell>
  );
}
