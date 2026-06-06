"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { AutoSchedulingPanel } from "@/components/dashboard/AutoSchedulingPanel";
import { ConflictPanel } from "@/components/dashboard/ConflictPanel";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";
import { ScheduleModuleCard } from "@/components/dashboard/ScheduleModuleCard";
import { StaffLoadTable } from "@/components/dashboard/StaffLoadTable";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import type { SchedulePeriod, Staff, Schedule, ConflictCheckResponse } from "@/types/api";
import type { StaffScheduleRow, CalendarAssignment, ScheduleTone, ConflictItem, StaffLoad, Metric } from "@/types/schedule";
import { scheduleModules, workflowSteps } from "@/data/schedule-dashboard";

// Frontend calculation of compensation date (mirrors backend calculateCompensationDate method)
function calculateCompensationDateOnFrontend(shiftDate: Date): Date {
  const date = new Date(shiftDate);
  const dayOfWeek = date.getDay(); // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
  
  let daysToAdd = 1;
  if (dayOfWeek === 5) { // Friday -> Tuesday (+4)
    daysToAdd = 4;
  } else if (dayOfWeek === 6) { // Saturday -> Tuesday (+3)
    daysToAdd = 3;
  }
  
  date.setDate(date.getDate() + daysToAdd);
  return date;
}

export default function Home() {
  const router = useRouter();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [conflictResponse, setConflictResponse] = useState<ConflictCheckResponse | null>(null);
  const [workloadData, setWorkloadData] = useState<any>(null);
  const [requirements, setRequirements] = useState<any[]>([]);
  
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load initial periods and staff list
  useEffect(() => {
    async function loadInitialData() {
      try {
        setIsLoading(true);
        setError(null);
        
        const periodsData = await api.get<SchedulePeriod[]>("/periods");
        setPeriods(periodsData || []);
        
        const staffData = await api.get<Staff[]>("/staff/active");
        setActiveStaff(staffData || []);
        
        if (periodsData && periodsData.length > 0) {
          // Default to the first period or one that is PUBLISHED
          const initialPeriod = periodsData.find(p => p.status === "PUBLISHED") || periodsData[0];
          setSelectedPeriodId(initialPeriod.id);
        } else {
          setIsLoading(false);
        }
      } catch (err: any) {
        console.error("Error loading initial dashboard data:", err);
        setError(err.message || "Không thể tải danh sách kỳ lịch hoặc nhân sự");
        setIsLoading(false);
      }
    }
    loadInitialData();
  }, []);

  // Load data for the selected period
  useEffect(() => {
    if (!selectedPeriodId) return;

    async function loadPeriodData() {
      try {
        setIsLoading(true);
        setError(null);
        
        // Fetch schedules
        const schedulesData = await api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`);
        setSchedules(schedulesData || []);
        
        // Fetch conflicts
        const conflictsData = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
        setConflictResponse(conflictsData || null);
        
        // Fetch workload balance data
        const workload = await api.get<any>(`/auto-schedule/workload-chart/${selectedPeriodId}`);
        setWorkloadData(workload || null);
        
        // Fetch shift requirements to calculate completion/coverage rate
        try {
          const reqs = await api.get<any[]>(`/shift-requirements/period/${selectedPeriodId}`);
          setRequirements(reqs || []);
        } catch (reqErr) {
          console.error("Error loading requirements:", reqErr);
          setRequirements([]);
        }
      } catch (err: any) {
        console.error("Error loading period schedules/conflicts:", err);
        setError(err.message || "Không thể tải dữ liệu kỳ lịch");
      } finally {
        setIsLoading(false);
      }
    }

    loadPeriodData();
  }, [selectedPeriodId]);

  // Handle excel export download
  const handleExportExcel = useCallback(async () => {
    if (!selectedPeriodId) return;
    try {
      const token = window.localStorage.getItem("medschedule.token");
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
      const response = await fetch(`${API_BASE_URL}/dashboard/export/schedule/${selectedPeriodId}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error("Không thể xuất báo cáo Excel");
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lich_cong_tac_${selectedPeriodId}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err: any) {
      alert(err.message || "Đã xảy ra lỗi khi tải báo cáo Excel");
    }
  }, [selectedPeriodId]);

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId);

  // Reconstruct matrix data
  const staffColumns = activeStaff.map(s => s.fullName);
  const scheduleRows: StaffScheduleRow[] = [];

  if (selectedPeriod) {
    const start = new Date(selectedPeriod.startDate);
    const end = new Date(selectedPeriod.endDate);
    
    // Day-by-day mapping
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const currentDateStr = d.toISOString().split("T")[0]; // YYYY-MM-DD
      const dayNum = String(d.getDate()).padStart(2, "0");
      
      const weekdays = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
      const weekdayStr = weekdays[d.getDay()];
      
      const assignments: Record<string, CalendarAssignment> = {};
      
      // Initialize as neutral/empty
      for (const s of activeStaff) {
        assignments[s.fullName] = { label: "Trống", tone: "neutral" };
      }
      
      // Map assigned shifts
      const daySchedules = schedules.filter(s => s.workDate === currentDateStr);
      for (const sched of daySchedules) {
        const staffName = sched.staff.fullName;
        if (assignments[staffName]) {
          let label = "Trống";
          let tone: ScheduleTone = "neutral";
          
          if (sched.hasConflict) {
            label = "Cảnh báo";
            tone = "warning";
          } else {
            switch (sched.shiftType.id) {
              case "L01":
                label = "24/24";
                tone = "duty24";
                break;
              case "L02":
                label = "Thông tầm";
                tone = "allDay";
                break;
              case "L03":
                label = "PK dịch vụ";
                tone = "serviceClinic";
                break;
              case "L04":
                label = "PK chuyên gia";
                tone = "expertClinic";
                break;
            }
          }
          
          assignments[staffName] = { label, tone };
        }
      }
      
      // Calculate and overlay compensation leave ("Nghỉ bù")
      for (const s of activeStaff) {
        const staffL01Schedules = schedules.filter(
          sched => sched.staff.id === s.id && sched.shiftType.id === "L01"
        );
        
        for (const sched of staffL01Schedules) {
          const l01Date = new Date(sched.workDate);
          const compDate = calculateCompensationDateOnFrontend(l01Date);
          const compDateStr = compDate.toISOString().split("T")[0];
          
          if (compDateStr === currentDateStr) {
            // Only overlay if there isn't a conflict or an active shift already assigned
            if (assignments[s.fullName].tone === "neutral") {
              assignments[s.fullName] = {
                label: "Nghỉ bù",
                tone: "compLeave",
                locked: true
              };
            }
          }
        }
      }
      
      scheduleRows.push({
        day: dayNum,
        weekday: weekdayStr,
        assignments
      });
    }
  }

  // Construct conflict list
  const mappedConflicts: ConflictItem[] = [];
  if (conflictResponse?.conflicts) {
    for (const conf of conflictResponse.conflicts) {
      for (const reason of conf.conflictReasons) {
        const dateObj = new Date(conf.workDate);
        const dateStr = `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}/${dateObj.getFullYear()}`;
        
        const severity = (reason.includes("nghỉ bù") || reason.includes("trực 24/24") || reason.includes("thông tầm")) 
          ? "Chặn lưu" 
          : "Cảnh báo";
          
        mappedConflicts.push({
          type: reason,
          staff: conf.staffName,
          date: dateStr,
          severity
        });
      }
    }
  }

  // Construct staff load top list
  const staffLoads: StaffLoad[] = [];
  if (workloadData?.staffWorkloadData) {
    for (const item of workloadData.staffWorkloadData) {
      staffLoads.push({
        name: item.staffName,
        duty24: Number(item.L01 || 0),
        allDay: Number(item.L02 || 0),
        clinics: Number(item.L03 || 0) + Number(item.L04 || 0)
      });
    }
  }

  // Calculate metrics
  const totalActiveStaff = activeStaff.length;
  const totalRequired = requirements.reduce((acc, req) => acc + req.requiredStaffCount, 0);
  const coveragePercent = totalRequired > 0 
    ? Math.min(100, Math.round((schedules.length / totalRequired) * 100))
    : (schedules.length > 0 ? 100 : 0);
    
  const totalConflictsCount = mappedConflicts.length;
  const totalCompDays = schedules.filter(s => s.shiftType.id === "L01").length;

  const metrics: Metric[] = [
    { label: "Nhân sự hoạt động", value: String(totalActiveStaff), helper: "3 vai trò hệ thống" },
    { label: "Ngày đã phân công", value: `${coveragePercent}%`, helper: selectedPeriod ? selectedPeriod.periodName : "Chưa chọn kỳ" },
    { label: "Xung đột cần xử lý", value: String(totalConflictsCount).padStart(2, "0"), helper: "Chặn lưu lịch tháng", tone: totalConflictsCount > 0 ? "warning" : "neutral" },
    { label: "Ngày nghỉ bù", value: String(totalCompDays).padStart(2, "0"), helper: "Tự tính sau trực 24/24", tone: "compLeave" },
  ];

  return (
    <DashboardShell
      activeCode="M06"
      description="Tổng hợp 4 loại lịch, cảnh báo xung đột và tự động phân công."
      primaryAction="Xếp lịch tự động"
      secondaryAction="Xuất báo cáo"
      onPrimaryAction={() => router.push("/auto-scheduling")}
      onSecondaryAction={handleExportExcel}
      title="Dashboard lịch công tác toàn phòng"
    >
      {/* Period Selector Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 bg-white px-5 py-3 shadow-sm">
        <div className="flex items-center gap-2">
          <label htmlFor="period-select" className="text-sm font-medium text-slate-700">
            Kỳ lập lịch:
          </label>
          <select
            id="period-select"
            value={selectedPeriodId || ""}
            onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
            className="h-9 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm outline-none transition-colors hover:border-slate-400 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
          >
            {periods.length === 0 && (
              <option value="">(Không có kỳ lịch nào)</option>
            )}
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.periodName} ({p.startDate} ~ {p.endDate})
              </option>
            ))}
          </select>
        </div>
        {selectedPeriod && (
          <div className="flex items-center gap-2 text-xs">
            <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 font-medium border ${
              selectedPeriod.status === "PUBLISHED" 
                ? "bg-emerald-50 text-emerald-700 border-emerald-200" 
                : selectedPeriod.status === "ARCHIVED"
                ? "bg-slate-100 text-slate-600 border-slate-200"
                : "bg-amber-50 text-amber-700 border-amber-200"
            }`}>
              {selectedPeriod.status === "PUBLISHED" ? "Đã công bố" : selectedPeriod.status === "ARCHIVED" ? "Đã lưu trữ" : "Bản nháp"}
            </span>
          </div>
        )}
      </div>

      <div className="grid gap-4 p-5 max-sm:p-3 2xl:grid-cols-[minmax(0,1fr)_340px] flex-1 overflow-y-auto">
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <p className="font-semibold">Đã xảy ra lỗi:</p>
              <p className="mt-1">{error}</p>
            </div>
          )}

          {isLoading && (
            <div className="flex items-center justify-center py-12">
              <svg className="size-8 animate-spin text-slate-900" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
                <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
              </svg>
              <span className="ml-3 text-sm text-slate-500 font-medium">Đang tải dữ liệu lịch...</span>
            </div>
          )}

          {!isLoading && periods.length === 0 && (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm">
              <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-slate-50 text-slate-400">
                <svg className="size-6" fill="none" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
              <h3 className="mt-2 text-sm font-semibold text-slate-900">Không có kỳ lập lịch</h3>
              <p className="mt-1 text-xs text-slate-500">
                Chưa có kỳ lập lịch nào trong hệ thống. Vui lòng tạo kỳ lập lịch mới trong phần quản trị hoặc chạy lập lịch.
              </p>
            </div>
          )}

          {!isLoading && periods.length > 0 && (
            <>
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
            </>
          )}
        </div>

        <aside className="grid gap-4 lg:grid-cols-3 2xl:block 2xl:space-y-4">
          <ConflictPanel conflicts={mappedConflicts} />
          <AutoSchedulingPanel steps={workflowSteps} />
          <StaffLoadTable loads={staffLoads.slice(0, 5)} />
        </aside>
      </div>
    </DashboardShell>
  );
}
