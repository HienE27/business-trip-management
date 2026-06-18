"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Skeleton } from "@/components/ui/Skeleton";
import { ScheduleCalendarSection } from "@/components/monthly-schedule/ScheduleCalendarSection";
import { QuickAddModal } from "@/components/monthly-schedule/QuickAddModal";
import { ShiftDetailModal } from "@/components/monthly-schedule/ShiftDetailModal";
import { useRole, canManage } from "@/hooks/useRole";
import { api } from "@/lib/api";
import { getInitialCalendar } from "@/components/monthly-schedule/utils";
import type {
  CompensationDay,
  Schedule,
  SchedulePeriod,
  Staff,
} from "@/types/api";
import type { ScheduleTab, ViewMode } from "@/components/monthly-schedule/types";

export default function AllDayPage() {
  const role = useRole();
  const isManager = canManage(role);

  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [compensationDays, setCompensationDays] = useState<CompensationDay[]>([]);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [addModalDate, setAddModalDate] = useState<Date | null>(null);
  const [detailScheduleId, setDetailScheduleId] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("calendar");

  const loadBaseData = useCallback(async () => {
    try {
      setLoading(true);
      const [periodData, staffData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
      ]);
      const pList = periodData ?? [];
      setPeriods(pList);
      setActiveStaff(staffData ?? []);
      const draft = pList.find((p) => p.status === "DRAFT") ?? pList[0] ?? null;
      setSelectedPeriodId(draft?.id ?? null);
    } catch {
      setMessage("Không thể tải dữ liệu. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadBaseData();
  }, [loadBaseData]);

  const selectedPeriod = useMemo(
    () => periods.find((p) => p.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId]
  );

  const initialCalendar = useMemo(() => getInitialCalendar(selectedPeriod), [selectedPeriod]);

  const handleRefresh = useCallback(() => {
    if (!selectedPeriodId) return;
    setLoading(true);
    setMessage(null);
    Promise.all([
      api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
      api.get<CompensationDay[]>(`/schedules/compensation-days/${selectedPeriodId}`),
    ])
      .then(([scheduleData, compData]) => {
        setSchedules((scheduleData ?? []).filter((s) => s.shiftType.id === "L02"));
        setCompensationDays(compData ?? []);
      })
      .catch(() => setMessage("Không thể tải lịch thông tầm."))
      .finally(() => setLoading(false));
  }, [selectedPeriodId]);

  useEffect(() => {
    handleRefresh();
  }, [handleRefresh]);

  const selectedSchedule = useMemo(
    () => schedules.find((s) => s.id === detailScheduleId) ?? null,
    [schedules, detailScheduleId]
  );

  const calendarAnnotations = useMemo(() => {
    const compByDate = new Map<string, CompensationDay[]>();
    for (const cd of compensationDays) {
      const key = cd.compensationDate.split("T")[0];
      const list = compByDate.get(key) ?? [];
      list.push(cd);
      compByDate.set(key, list);
    }
    return Array.from(compByDate.entries()).map(([date, days]) => {
      const staffNames = days.map((d) => d.staffName);
      const label =
        staffNames.length === 1
          ? `Nghỉ bù · ${staffNames[0]}`
          : `Nghỉ bù · ${staffNames[0]}${staffNames.length > 1 ? ` (+${staffNames.length - 1})` : ""}`;
      return {
        date,
        label,
        tone: "compLeave" as const,
        description: `Ngày nghỉ bù — không thể xếp lịch thông tầm cho nhân sự này`,
      };
    });
  }, [compensationDays]);

  if (loading && periods.length === 0) {
    return (
      <DashboardShell
        activeSection="all-day"
        title="Lịch thông tầm"
        description="Xếp lịch thông tầm theo tháng. Nhân sự làm ca liên tục không nghỉ trưa trong ngày được chọn."
      >
        <div className="space-y-4">
          <Skeleton className="h-32 rounded-xl" />
          <Skeleton className="h-96 rounded-xl" />
        </div>
      </DashboardShell>
    );
  }

  return (
    <DashboardShell
      activeSection="all-day"
      title="Lịch thông tầm"
      description="Xếp lịch thông tầm theo tháng. Ràng buộc: không trùng lịch trực 24/24 cùng ngày và không xếp vào ngày nghỉ bù."
    >
      {message && (
        <div
          className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error"
          role="alert"
        >
          {message}
        </div>
      )}

      <section className="flex flex-wrap items-end gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div className="min-w-[200px]">
          <label
            htmlFor="allday-period-select"
            className="mb-1.5 block text-label-sm text-on-surface-variant"
          >
            Kỳ lịch
          </label>
          <div className="relative">
            <select
              id="allday-period-select"
              className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
            >
              <option value="">Chọn kỳ lịch</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.periodName} ({p.status})
                </option>
              ))}
            </select>
            <span
              aria-hidden="true"
              className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]"
            >
              expand_more
            </span>
          </div>
        </div>
        {isManager && (
          <button
            type="button"
            onClick={() => setAddModalDate(new Date())}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-semibold text-on-primary hover:bg-primary/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>
            Thêm ca thông tầm
          </button>
        )}
      </section>

      <section className="grid gap-3 grid-cols-2 sm:grid-cols-4">
        {[
          {
            label: "Tổng ca thông tầm",
            value: schedules.length,
            icon: "schedule",
            accent: "bg-shift-all-day/30 text-on-shift-all-day",
          },
          {
            label: "Ngày nghỉ bù",
            value: compensationDays.length,
            icon: "bedtime",
            accent: "bg-surface-container-high text-on-surface",
          },
          {
            label: "Nhân sự tham gia",
            value: new Set(schedules.map((s) => s.staff.id)).size,
            icon: "groups",
            accent: "bg-shift-24/20 text-on-shift-24",
          },
          {
            label: "Xung đột",
            value: schedules.filter((s) => s.hasConflict === true).length,
            icon: "warning",
            accent: "bg-error-container text-on-error-container",
          },
        ].map((kpi) => (
          <div
            key={kpi.label}
            className={`rounded-xl border border-outline-variant p-4 ${kpi.accent}`}
          >
            <p className="text-label-sm opacity-80 mb-1">{kpi.label}</p>
            <p className="text-headline-md font-bold">{kpi.value}</p>
          </div>
        ))}
      </section>

      {!selectedPeriod ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
          <span aria-hidden="true" className="material-symbols-outlined text-5xl text-outline">schedule</span>
          <p className="text-on-surface-variant">Chọn một kỳ lịch để xem lịch thông tầm.</p>
        </div>
      ) : loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <ScheduleCalendarSection
          schedules={schedules}
          calendarAnnotations={calendarAnnotations}
          coverages={{}}
          activeStaff={activeStaff}
          specialties={[]}
          staffFilterId={null}
          specialtyFilterId={null}
          selectedPeriodId={selectedPeriodId}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          viewMode={viewMode}
          selectedTab={"L02" satisfies ScheduleTab}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onFocusDate={() => undefined}
          onAddDate={(date) => setAddModalDate(date)}
          onStaffFilterChange={() => undefined}
          onSpecialtyFilterChange={() => undefined}
          onViewDetail={(schedule) => setDetailScheduleId(schedule.id)}
          onViewModeChange={setViewMode}
          onFilterTypeChange={() => undefined}
          hideFilters
        />
      )}

      {selectedPeriodId && (
        <QuickAddModal
          date={addModalDate}
          periodId={selectedPeriodId}
          defaultShiftTypeId="L02"
          staffList={activeStaff}
          compensationDays={compensationDays}
          onSuccess={handleRefresh}
          onClose={() => setAddModalDate(null)}
        />
      )}

      <ShiftDetailModal
        scheduleId={detailScheduleId}
        schedule={selectedSchedule}
        loading={false}
        canEdit={isManager}
        onClose={() => setDetailScheduleId(null)}
        onSave={handleRefresh}
      />
    </DashboardShell>
  );
}
