import { scheduleAlerts, weekDays } from "@/data/schedule-summary";
import { getNavigationItems } from "@/data/schedule-dashboard";
import { AppSidebar } from "@/components/layout/AppSidebar";
import { DashboardHeader } from "@/components/layout/DashboardHeader";
import { ScheduleAlertsPanel } from "@/components/schedule-summary/ScheduleAlertsPanel";
import { ScheduleLegend } from "@/components/schedule-summary/ScheduleLegend";
import { UnifiedScheduleCalendar } from "@/components/schedule-summary/UnifiedScheduleCalendar";

export default function ScheduleSummaryPage() {
  const activeCode = "M03-SUMMARY";
  const navItems = getNavigationItems(activeCode);
  // Insert calendar_view_month link after existing nav items
  const navWithSummary: typeof navItems = [
    ...navItems.slice(0, 5),
    { label: "Tổng hợp lịch", code: "M03-SUMMARY", href: "/schedule-summary", icon: "calendar_view_month" },
    ...navItems.slice(5),
  ];

  const todayLabel = weekDays.find((d) => d.isToday)
    ? `${weekDays[0].dayNumber} - ${weekDays[weekDays.length - 1].dayNumber} Tháng 11, 2023`
    : "12 - 18 Tháng 11, 2023";

  return (
    <div className="flex min-h-screen bg-background text-on-surface">
      <AppSidebar items={navWithSummary} />
      <div className="flex-1 flex flex-col md:ml-[260px] min-w-0">
        <DashboardHeader title="Tổng hợp lịch" description="Chế độ xem toàn cảnh lịch công tác toàn bệnh viện" />
        <main className="flex-1 overflow-y-auto bg-surface p-4 md:p-6">
          <div className="max-w-[1600px] mx-auto flex flex-col gap-6">

            {/* Page Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div />
              <div className="flex items-center gap-3">
                <button
                  className="px-4 py-2 rounded-lg border border-primary text-primary font-label-md hover:bg-primary/5 transition-colors flex items-center gap-2 bg-surface-container-lowest"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">print</span>
                  In lịch
                </button>
                <button
                  className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md hover:opacity-90 transition-opacity flex items-center gap-2 shadow-sm"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">download</span>
                  Xuất Excel
                </button>
              </div>
            </div>

            {/* Master Control Bar */}
            <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] border border-outline-variant/40 p-2 flex flex-wrap lg:flex-nowrap items-center justify-between gap-4 sticky top-0 z-20">
              {/* View Switcher + Date Nav */}
              <div className="flex items-center gap-4 w-full lg:w-auto">
                <div className="flex items-center bg-surface-container-low rounded-lg p-1 border border-outline-variant/30">
                  <button className="px-3 py-1.5 text-on-surface-variant hover:text-on-surface hover:bg-surface-container-highest transition-colors font-label-md text-label-md rounded-lg">
                    Tháng
                  </button>
                  <button className="px-3 py-1.5 bg-surface-container-lowest text-on-surface font-label-md text-label-md shadow-sm border border-outline-variant/20 rounded-lg">
                    Tuần
                  </button>
                  <button className="px-3 py-1.5 text-on-surface-variant hover:text-on-surface hover:bg-surface-container-highest transition-colors font-label-md text-label-md rounded-lg">
                    Ngày
                  </button>
                </div>
                <div className="flex items-center gap-2">
                  <button className="p-1.5 rounded-lg hover:bg-surface-container-low text-on-surface-variant transition-colors">
                    <span className="material-symbols-outlined text-[20px]">chevron_left</span>
                  </button>
                  <span className="font-title-lg text-title-lg min-w-[180px] text-center">
                    {todayLabel}
                  </span>
                  <button className="p-1.5 rounded-lg hover:bg-surface-container-low text-on-surface-variant transition-colors">
                    <span className="material-symbols-outlined text-[20px]">chevron_right</span>
                  </button>
                </div>
              </div>

              <div className="w-px h-8 bg-outline-variant/40 hidden lg:block" />

              {/* Filters */}
              <div className="flex flex-wrap items-center gap-2 flex-1 justify-end">
                <button className="px-3 py-1.5 rounded-lg border border-outline-variant/50 bg-surface text-on-surface-variant font-body-sm text-body-sm flex items-center gap-2 hover:bg-surface-container-low transition-colors">
                  <span className="material-symbols-outlined text-[16px]">domain</span>
                  Khoa/Phòng
                  <span className="material-symbols-outlined text-[16px]">arrow_drop_down</span>
                </button>
                <button className="px-3 py-1.5 rounded-lg border border-outline-variant/50 bg-surface text-on-surface-variant font-body-sm text-body-sm flex items-center gap-2 hover:bg-surface-container-low transition-colors">
                  <span className="material-symbols-outlined text-[16px]">badge</span>
                  Chức danh
                  <span className="material-symbols-outlined text-[16px]">arrow_drop_down</span>
                </button>
                <button className="px-3 py-1.5 rounded-lg border border-outline-variant/50 bg-surface text-on-surface-variant font-body-sm text-body-sm flex items-center gap-2 hover:bg-surface-container-low transition-colors">
                  <span className="material-symbols-outlined text-[16px]">filter_list</span>
                  Lọc nâng cao
                </button>
              </div>
            </div>

            {/* Main Layout Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* Left: Calendar */}
              <div className="lg:col-span-9">
                <UnifiedScheduleCalendar />
              </div>

              {/* Right: Sidebar Widgets */}
              <div className="lg:col-span-3 flex flex-col gap-6">
                <ScheduleAlertsPanel alerts={scheduleAlerts} />
                <ScheduleLegend />
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
