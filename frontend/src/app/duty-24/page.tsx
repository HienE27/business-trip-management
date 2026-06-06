"use client";

import { useState, useEffect } from "react";
import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ruleCards } from "@/data/module-screens";
import { api } from "@/lib/api";
import type { SchedulePeriod, Staff, Schedule, ConflictCheckResponse } from "@/types/api";
import type { StaffScheduleRow, CalendarAssignment, ScheduleTone, ConflictItem } from "@/types/schedule";

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

export default function Duty24Page() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [conflictResponse, setConflictResponse] = useState<ConflictCheckResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form states for quick assignment
  const [staffId, setStaffId] = useState<string>("");
  const [workDate, setWorkDate] = useState<string>("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

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
          // Read selectedPeriodId from localStorage if present
          const savedPeriodId = localStorage.getItem("medschedule.selectedPeriodId");
          const exists = savedPeriodId && periodsData.some(p => p.id === Number(savedPeriodId));
          if (exists) {
            setSelectedPeriodId(Number(savedPeriodId));
          } else {
            const initialPeriod = periodsData.find(p => p.status === "PUBLISHED") || periodsData[0];
            setSelectedPeriodId(initialPeriod.id);
            localStorage.setItem("medschedule.selectedPeriodId", String(initialPeriod.id));
          }
        } else {
          setIsLoading(false);
        }
      } catch (err: any) {
        console.error("Error loading duty-24 initial data:", err);
        setError(err.message || "Không thể tải danh sách kỳ lịch hoặc nhân sự");
        setIsLoading(false);
      }
    }
    loadInitialData();
  }, []);

  // Sync periodId to localStorage and load data for selected period
  useEffect(() => {
    if (!selectedPeriodId) return;
    localStorage.setItem("medschedule.selectedPeriodId", String(selectedPeriodId));

    async function loadPeriodData() {
      try {
        setIsLoading(true);
        setError(null);
        
        const schedulesData = await api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`);
        setSchedules(schedulesData || []);
        
        const conflictsData = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
        setConflictResponse(conflictsData || null);
        
        // Reset form inputs to default values helper
        const period = periods.find(p => p.id === selectedPeriodId);
        if (period) {
          setWorkDate(period.startDate);
        }
        if (activeStaff.length > 0) {
          setStaffId(String(activeStaff[0].id));
        }
      } catch (err: any) {
        console.error("Error loading duty-24 schedules/conflicts:", err);
        setError(err.message || "Không thể tải dữ liệu kỳ lịch");
      } finally {
        setIsLoading(false);
      }
    }

    loadPeriodData();
  }, [selectedPeriodId, periods]);

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId);

  // Set default form values when active staff loads
  useEffect(() => {
    if (activeStaff.length > 0 && !staffId) {
      setStaffId(String(activeStaff[0].id));
    }
  }, [activeStaff, staffId]);

  // Handle shift assignment submit
  const handleAssignShift = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPeriodId || !staffId || !workDate) return;

    try {
      setIsSubmitting(true);
      setActionError(null);
      
      await api.post("/schedules", {
        periodId: selectedPeriodId,
        workDate,
        staffId: Number(staffId),
        shiftTypeId: "L01", // Trực 24/24
      });

      // Reload schedules & conflicts
      const [schedulesData, conflictsData] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`)
      ]);
      setSchedules(schedulesData || []);
      setConflictResponse(conflictsData || null);
    } catch (err: any) {
      console.error("Error creating shift:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi gán ca trực.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle shift delete
  const handleDeleteShift = async (schedId: number) => {
    if (!confirm("Bạn có chắc chắn muốn xóa ca trực này?")) return;
    try {
      setActionError(null);
      await api.delete(`/schedules/${schedId}`);

      // Reload schedules & conflicts
      const [schedulesData, conflictsData] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`)
      ]);
      setSchedules(schedulesData || []);
      setConflictResponse(conflictsData || null);
    } catch (err: any) {
      console.error("Error deleting shift:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi xóa ca trực.");
    }
  };

  // Reconstruct matrix data
  const staffColumns = activeStaff.map(s => s.fullName);
  const scheduleRows: StaffScheduleRow[] = [];

  if (selectedPeriod) {
    const start = new Date(selectedPeriod.startDate);
    const end = new Date(selectedPeriod.endDate);
    
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const currentDateStr = d.toISOString().split("T")[0];
      const dayNum = String(d.getDate()).padStart(2, "0");
      
      const weekdays = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
      const weekdayStr = weekdays[d.getDay()];
      
      const assignments: Record<string, CalendarAssignment> = {};
      for (const s of activeStaff) {
        assignments[s.fullName] = { label: "Trống", tone: "neutral" };
      }
      
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
      
      // Overlay calculated compensation days
      for (const s of activeStaff) {
        const staffL01Schedules = schedules.filter(
          sched => sched.staff.id === s.id && sched.shiftType.id === "L01"
        );
        
        for (const sched of staffL01Schedules) {
          const l01Date = new Date(sched.workDate);
          const compDate = calculateCompensationDateOnFrontend(l01Date);
          const compDateStr = compDate.toISOString().split("T")[0];
          
          if (compDateStr === currentDateStr) {
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

  // Construct active conflicts count
  const conflictsList: ConflictItem[] = [];
  if (conflictResponse?.conflicts) {
    for (const conf of conflictResponse.conflicts) {
      for (const reason of conf.conflictReasons) {
        // Only focus on L01 (24/24) related conflicts for this page context
        if (reason.toLowerCase().includes("trực 24/24") || reason.toLowerCase().includes("nghỉ bù")) {
          const dateObj = new Date(conf.workDate);
          const dateStr = `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}/${dateObj.getFullYear()}`;
          const severity = "Chặn lưu";
          
          conflictsList.push({
            type: reason,
            staff: conf.staffName,
            date: dateStr,
            severity
          });
        }
      }
    }
  }

  // Filter schedules to L01 list for table view
  const assignedL01Schedules = schedules.filter(s => s.shiftType.id === "L01");

  return (
    <DashboardShell
      activeCode="M02"
      description="Xếp lịch trực cả tháng, tự tính nghỉ bù và kiểm tra xung đột hàng loạt."
      title="M02 - Lịch trực 24/24"
    >
      {/* Period selector */}
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

      <div className="space-y-4 p-5 max-sm:p-3 flex-1 overflow-y-auto">
        {error && (
          <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            <p className="font-semibold">Đã xảy ra lỗi:</p>
            <p className="mt-1">{error}</p>
          </div>
        )}

        <section className="grid gap-4 md:grid-cols-3">
          {ruleCards.map((rule) => (
            <article
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
              key={rule.title}
            >
              <h2 className="text-sm font-semibold">{rule.title}</h2>
              <p className="mt-2 text-sm leading-6 text-slate-600">{rule.detail}</p>
            </article>
          ))}
        </section>

        {isLoading && (
          <div className="flex items-center justify-center py-12 bg-white border border-slate-200 rounded-lg">
            <svg className="size-8 animate-spin text-slate-900" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
              <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
            </svg>
            <span className="ml-3 text-sm text-slate-500 font-medium">Đang tải dữ liệu trực...</span>
          </div>
        )}

        {!isLoading && periods.length > 0 && selectedPeriod && (
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
            <div className="space-y-4">
              <ScheduleMatrix staff={staffColumns} rows={scheduleRows} />
              
              <SectionCard
                description="Mỗi dòng là ngày trực và ngày nghỉ bù hệ thống tự sinh"
                title="Bảng trực đã gán"
              >
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[650px] border-collapse text-sm text-left">
                    <thead className="bg-slate-50 text-xs font-medium uppercase text-slate-500">
                      <tr className="h-11 border-b border-slate-200">
                        <th className="px-4">Ngày trực</th>
                        <th className="px-4">Thứ</th>
                        <th className="px-4">Nhân sự trực</th>
                        <th className="px-4">Nghỉ bù</th>
                        <th className="px-4">Trạng thái</th>
                        <th className="px-4 text-center">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {assignedL01Schedules.length === 0 && (
                        <tr className="h-12 text-slate-500">
                          <td colSpan={6} className="px-4 text-center">Chưa có ca trực 24/24 nào được gán</td>
                        </tr>
                      )}
                      {assignedL01Schedules.map((sched) => {
                        const dateObj = new Date(sched.workDate);
                        const weekdays = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
                        const weekdayStr = weekdays[dateObj.getDay()];
                        
                        const compDate = calculateCompensationDateOnFrontend(dateObj);
                        const compDateStr = `${String(compDate.getDate()).padStart(2, "0")}/${String(compDate.getMonth() + 1).padStart(2, "0")} (${weekdays[compDate.getDay()]})`;

                        const formattedShiftDate = `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}`;

                        return (
                          <tr key={sched.id} className="h-12 hover:bg-slate-50">
                            <td className="px-4 font-medium">{formattedShiftDate}</td>
                            <td className="px-4 text-slate-500">{weekdayStr}</td>
                            <td className="px-4 text-slate-900 font-semibold">{sched.staff.fullName}</td>
                            <td className="px-4">
                              <span className="inline-flex items-center rounded-md bg-indigo-50 px-2 py-1 text-xs font-medium text-indigo-700 border border-indigo-200">
                                {compDateStr}
                              </span>
                            </td>
                            <td className="px-4">
                              <StatusBadge tone={sched.hasConflict ? "danger" : "success"}>
                                {sched.hasConflict ? "Chặn lưu" : "Hợp lệ"}
                              </StatusBadge>
                            </td>
                            <td className="px-4 text-center">
                              <button
                                onClick={() => handleDeleteShift(sched.id)}
                                className="inline-flex size-8 items-center justify-center rounded-md text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                                title="Xóa ca trực"
                              >
                                <svg className="size-5" fill="none" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor">
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                </svg>
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </SectionCard>
            </div>

            <aside className="space-y-4">
              <SectionCard description="Gán lịch trực 24/24 thủ công" title="Form gán nhanh">
                <form onSubmit={handleAssignShift} className="space-y-3 p-4">
                  {actionError && (
                    <div className="rounded border border-rose-200 bg-rose-50 p-2.5 text-xs text-rose-800">
                      {actionError}
                    </div>
                  )}

                  <label className="block">
                    <span className="text-xs font-medium text-slate-500">Nhân sự</span>
                    <select
                      value={staffId}
                      onChange={(e) => setStaffId(e.target.value)}
                      className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:border-slate-400"
                      required
                    >
                      {activeStaff.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.fullName} ({s.specialty?.name || "Không chuyên khoa"})
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="block">
                    <span className="text-xs font-medium text-slate-500">Ngày trực</span>
                    <input
                      type="date"
                      min={selectedPeriod?.startDate}
                      max={selectedPeriod?.endDate}
                      value={workDate}
                      onChange={(e) => setWorkDate(e.target.value)}
                      className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm outline-none focus:border-slate-400"
                      required
                    />
                  </label>

                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="h-9 w-full rounded-md bg-slate-950 text-sm font-medium text-white shadow-sm hover:bg-slate-800 transition-colors disabled:bg-slate-400 disabled:cursor-not-allowed"
                  >
                    {isSubmitting ? "Đang gán..." : "Thêm ca trực"}
                  </button>
                </form>
              </SectionCard>

              {conflictsList.length > 0 && (
                <section className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
                  <p className="text-xs font-medium uppercase">Chặn lưu</p>
                  <h2 className="mt-2 text-lg font-semibold">{conflictsList.length} ô cần xử lý</h2>
                  <div className="mt-2 space-y-2 text-xs leading-5">
                    {conflictsList.map((c, idx) => (
                      <p key={idx} className="border-t border-rose-100 pt-1.5 first:border-0 first:pt-0">
                        • <strong>{c.staff}</strong> ({c.date}): {c.type}
                      </p>
                    ))}
                  </div>
                </section>
              )}

              {conflictsList.length === 0 && (
                <section className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-800">
                  <p className="text-xs font-medium uppercase">Kiểm tra hợp lệ</p>
                  <h2 className="mt-2 text-base font-semibold">Hệ thống an toàn</h2>
                  <p className="mt-1 text-xs leading-5">
                    Không phát hiện xung đột lịch trực 24/24 nào trong kỳ này.
                  </p>
                </section>
              )}
            </aside>
          </div>
        )}
      </div>
    </DashboardShell>
  );
}
