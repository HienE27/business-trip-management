"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Skeleton } from "@/components/ui/Skeleton";
import { ScheduleCalendarSection } from "@/components/monthly-schedule/ScheduleCalendarSection";
import { QuickAddModal } from "@/components/monthly-schedule/QuickAddModal";
import { useRole, canManage } from "@/hooks/useRole";
import { api } from "@/lib/api";
import { getInitialCalendar } from "@/components/monthly-schedule/utils";
import type { Schedule, Specialty, CompensationDay, SchedulePeriod, Staff } from "@/types/api";
import type { ScheduleTab, ViewMode } from "@/components/monthly-schedule/types";

export default function ExpertClinicPage() {
  const role = useRole();
  const isManager = canManage(role);

  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [selectedSpecialtyId, setSelectedSpecialtyId] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [compensationDays, setCompensationDays] = useState<CompensationDay[]>([]);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [addModalDate, setAddModalDate] = useState<Date | null>(null);
  const [selectedTab, setSelectedTab] = useState<ScheduleTab>("ALL");
  const [viewMode, setViewMode] = useState<ViewMode>("calendar");

  const loadBaseData = useCallback(async () => {
    try {
      setLoading(true);
      const [periodData, specialtyData, staffData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Specialty[]>("/specialties/active"),
        api.get<Staff[]>("/staff/active"),
      ]);
      const pList = periodData ?? [];
      setPeriods(pList);
      setSpecialties(specialtyData ?? []);
      setActiveStaff(staffData ?? []);
      const draft = pList.find((p) => p.status === "DRAFT") ?? pList[0] ?? null;
      setSelectedPeriodId(draft?.id ?? null);
    } catch {
      setMessage("Không thể tải dữ liệu. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadBaseData(); }, [loadBaseData]);

  const selectedPeriod = useMemo(
    () => periods.find((p) => p.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  const initialCalendar = useMemo(() => getInitialCalendar(selectedPeriod), [selectedPeriod]);

  useEffect(() => {
    if (!selectedPeriodId) return;
    setLoading(true);
    setMessage(null);
    api.get<Schedule[]>("/schedules/expert-clinic", {
      periodId: selectedPeriodId,
      ...(selectedSpecialtyId ? { specialtyId: selectedSpecialtyId } : {}),
    })
      .then((data) => setSchedules(data ?? []))
      .catch(() => setMessage("Không thể tải lịch phòng khám chuyên gia."))
      .finally(() => setLoading(false));
  }, [selectedPeriodId, selectedSpecialtyId]);

  const handleRefresh = useCallback(() => {
    if (!selectedPeriodId) return;
    setLoading(true);
    setMessage(null);
    api.get<Schedule[]>("/schedules/expert-clinic", {
      periodId: selectedPeriodId,
      ...(selectedSpecialtyId ? { specialtyId: selectedSpecialtyId } : {}),
    })
      .then((data) => setSchedules(data ?? []))
      .catch(() => setMessage("Không thể tải lịch phòng khám chuyên gia."))
      .finally(() => setLoading(false));
  }, [selectedPeriodId, selectedSpecialtyId]);

  if (loading && periods.length === 0) {
    return (
      <DashboardShell
        activeSection="monthly-schedule"
        title="Phòng khám chuyên gia"
        description="Xem và quản lý lịch phòng khám chuyên gia theo chuyên khoa."
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
      activeSection="monthly-schedule"
      title="Phòng khám chuyên gia"
      description="Xem và quản lý lịch phòng khám chuyên gia theo chuyên khoa."
    >
      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">{message}</div>
      )}

      {/* Period + Specialty selector */}
      <section className="flex flex-wrap items-end gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div className="min-w-[200px]">
          <label htmlFor="expert-period-select" className="mb-1.5 block text-label-sm text-on-surface-variant">Kỳ lịch</label>
          <div className="relative">
            <select
              id="expert-period-select"
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
            <span className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]">expand_more</span>
          </div>
        </div>
        <div className="min-w-[200px]">
          <label htmlFor="expert-specialty-filter" className="mb-1.5 block text-label-sm text-on-surface-variant">Chuyên khoa</label>
          <div className="relative">
            <select
              id="expert-specialty-filter"
              className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
              value={selectedSpecialtyId ?? ""}
              onChange={(e) => setSelectedSpecialtyId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">Tất cả chuyên khoa</option>
              {specialties.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
            <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]">expand_more</span>
          </div>
        </div>
        {isManager && (
          <button
            type="button"
            onClick={() => setAddModalDate(new Date())}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-semibold text-on-primary hover:bg-primary/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">add</span>
            Thêm ca chuyên gia
          </button>
        )}
      </section>

      {/* Stats */}
      <section className="grid gap-3 grid-cols-2 sm:grid-cols-4">
        {[
          { label: "Tổng ca PK Chuyên gia", value: schedules.length, icon: "stethoscope", accent: "bg-shift-expert/10 text-on-shift-expert" },
          { label: "Chuyên khoa", value: specialties.length, icon: "local_hospital", accent: "bg-primary/10 text-primary" },
          { label: "Nhân sự tham gia", value: new Set(schedules.map((s) => s.staff.id)).size, icon: "groups", accent: "bg-shift-all-day/10 text-on-shift-all-day" },
          { label: "Xung đột", value: schedules.filter((s) => s.hasConflict === true).length, icon: "warning", accent: "bg-error-container text-on-error-container" },
        ].map((kpi) => (
          <div key={kpi.label} className={`rounded-xl border border-outline-variant p-4 ${kpi.accent}`}>
            <p className="text-label-sm opacity-80 mb-1">{kpi.label}</p>
            <p className="text-headline-md font-bold">{kpi.value}</p>
          </div>
        ))}
      </section>

      {/* Calendar */}
      {!selectedPeriod ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
          <span className="material-symbols-outlined text-5xl text-outline">stethoscope</span>
          <p className="text-on-surface-variant">Chọn một kỳ lịch để xem lịch phòng khám chuyên gia.</p>
        </div>
      ) : loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <ScheduleCalendarSection
          schedules={schedules}
          calendarAnnotations={[]}
          coverages={{}}
          activeStaff={activeStaff}
          specialties={specialties}
          staffFilterId={null}
          specialtyFilterId={selectedSpecialtyId}
          selectedPeriodId={selectedPeriodId}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          viewMode={viewMode}
          selectedTab={selectedTab}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onFocusDate={() => {}}
          onAddDate={(date) => setAddModalDate(date)}
          onStaffFilterChange={() => {}}
          onSpecialtyFilterChange={setSelectedSpecialtyId}
          onViewDetail={() => {}}
          onViewModeChange={setViewMode}
          onFilterTypeChange={(filter: string) => setSelectedTab(filter as ScheduleTab)}
        />
      )}

      {selectedPeriodId && (
        <QuickAddModal
          date={addModalDate}
          periodId={selectedPeriodId}
          defaultShiftTypeId="L04"
          staffList={activeStaff}
          compensationDays={compensationDays}
          onSuccess={handleRefresh}
          onClose={() => setAddModalDate(null)}
        />
      )}
    </DashboardShell>
  );
}
