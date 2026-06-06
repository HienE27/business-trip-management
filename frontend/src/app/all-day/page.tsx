"use client";

import { useState, useEffect } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { api } from "@/lib/api";
import type { SchedulePeriod, Staff, Schedule, ConflictCheckResponse } from "@/types/api";
import type { ConflictItem } from "@/types/schedule";

export default function AllDayPage() {
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
        console.error("Error loading all-day initial data:", err);
        setError(err.message || "Không thể tải danh sách kỳ lịch hoặc nhân sự");
        setIsLoading(false);
      }
    }
    loadInitialData();
  }, []);

  // Sync selectedPeriodId to localStorage and load schedules and conflicts
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

        const period = periods.find(p => p.id === selectedPeriodId);
        if (period) {
          setWorkDate(period.startDate);
        }
        if (activeStaff.length > 0) {
          setStaffId(String(activeStaff[0].id));
        }
      } catch (err: any) {
        console.error("Error loading all-day period data:", err);
        setError(err.message || "Không thể tải dữ liệu kỳ lịch");
      } finally {
        setIsLoading(false);
      }
    }

    loadPeriodData();
  }, [selectedPeriodId, periods]);

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId);

  // Set default form values
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
        shiftTypeId: "L02", // Thông tầm
      });

      // Reload schedules & conflicts
      const [schedulesData, conflictsData] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`)
      ]);
      setSchedules(schedulesData || []);
      setConflictResponse(conflictsData || null);
    } catch (err: any) {
      console.error("Error creating L02 shift:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi gán ca thông tầm.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle shift delete
  const handleDeleteShift = async (schedId: number) => {
    if (!confirm("Bạn có chắc chắn muốn xóa lịch thông tầm này?")) return;
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
      console.error("Error deleting L02 shift:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi xóa ca trực.");
    }
  };

  // Construct active conflicts count
  const conflictsList: ConflictItem[] = [];
  if (conflictResponse?.conflicts) {
    for (const conf of conflictResponse.conflicts) {
      for (const reason of conf.conflictReasons) {
        if (reason.toLowerCase().includes("thông tầm") || reason.toLowerCase().includes("l02")) {
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

  // Filter schedules to L02 (Thông tầm) list
  const assignedL02Schedules = schedules.filter(s => s.shiftType.id === "L02");

  return (
    <DashboardShell
      activeCode="M03"
      description="Xếp lịch làm liên tục không nghỉ trưa, chỉ chọn ngày và nhân sự."
      title="M03 - Lịch thông tầm"
    >
      {/* Period Selector */}
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

      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_340px] flex-1 overflow-y-auto">
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <p className="font-semibold">Đã xảy ra lỗi:</p>
              <p className="mt-1">{error}</p>
            </div>
          )}

          {isLoading && (
            <div className="flex items-center justify-center py-12 bg-white border border-slate-200 rounded-lg shadow-sm">
              <svg className="size-8 animate-spin text-slate-900" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
                <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
              </svg>
              <span className="ml-3 text-sm text-slate-500 font-medium">Đang tải lịch thông tầm...</span>
            </div>
          )}

          {!isLoading && periods.length > 0 && selectedPeriod && (
            <>
              <SectionCard
                action={
                  <div className="flex rounded-md border border-slate-200 bg-slate-50 p-1 text-xs font-medium">
                    <button className="h-7 rounded bg-white px-3 shadow-sm">Tháng</button>
                    <button className="h-7 px-3 text-slate-500">Tuần</button>
                  </div>
                }
                description="Dữ liệu trực 24/24 và nghỉ bù được kiểm nghiệm thời gian thực để tránh xung đột"
                title="Bảng lịch thông tầm"
              >
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[650px] border-collapse text-sm text-left">
                    <thead className="bg-slate-50 text-xs font-medium uppercase text-slate-500">
                      <tr className="h-11 border-b border-slate-200">
                        <th className="px-4">Ngày</th>
                        <th className="px-4">Thứ</th>
                        <th className="px-4">Nhân sự</th>
                        <th className="px-4">Kiểm tra</th>
                        <th className="px-4">Trạng thái</th>
                        <th className="px-4 text-center">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {assignedL02Schedules.length === 0 && (
                        <tr className="h-12 text-slate-500">
                          <td colSpan={6} className="px-4 text-center">Chưa có ca thông tầm nào được gán</td>
                        </tr>
                      )}
                      {assignedL02Schedules.map((sched) => {
                        const dateObj = new Date(sched.workDate);
                        const weekdays = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
                        const weekdayStr = weekdays[dateObj.getDay()];

                        const formattedDate = `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}`;

                        return (
                          <tr key={sched.id} className="h-12 hover:bg-slate-50">
                            <td className="px-4 font-medium">{formattedDate}</td>
                            <td className="px-4 text-slate-500">{weekdayStr}</td>
                            <td className="px-4 text-slate-900 font-semibold">{sched.staff.fullName}</td>
                            <td className="px-4 text-slate-600">
                              {sched.hasConflict ? "Phát hiện xung đột lịch trực/nghỉ" : "Hợp lệ (Không trùng ca trực)"}
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
                                title="Xóa ca thông tầm"
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

              <section className="grid gap-4 md:grid-cols-3">
                {["Chọn tháng", "Gán nhân sự", "Lưu & công bố"].map((title, index) => (
                  <div
                    className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                    key={title}
                  >
                    <p className="text-xs font-semibold text-slate-500">B{index + 1}</p>
                    <h2 className="mt-1 text-sm font-semibold">{title}</h2>
                    <p className="mt-2 text-xs leading-5 text-slate-600">
                      {index === 0
                        ? "Tải kỳ lịch trực 24/24 và ngày nghỉ bù của tháng hoạt động."
                        : index === 1
                          ? "Chọn nhân sự và ngày muốn gán nhanh bằng sidebar."
                          : "Chỉ cho phép chạy khi không còn lỗi xung đột chặn."}
                    </p>
                  </div>
                ))}
              </section>
            </>
          )}
        </div>

        <aside className="space-y-4">
          <SectionCard description="Không chọn giờ, chỉ chọn ngày" title="Form gán nhanh">
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
                <span className="text-xs font-medium text-slate-500">Ngày làm thông tầm</span>
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
                className="h-9 w-full rounded-md bg-slate-950 text-sm font-medium text-white shadow-sm hover:bg-slate-800 transition-colors disabled:bg-slate-400"
              >
                {isSubmitting ? "Đang gán..." : "Gán lịch"}
              </button>
            </form>
          </SectionCard>

          {conflictsList.length > 0 ? (
            <section className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
              <p className="text-xs font-medium uppercase">Xung đột chặn lưu</p>
              <h2 className="mt-2 text-lg font-semibold">{conflictsList.length} lỗi xung đột</h2>
              <div className="mt-2 space-y-2 text-xs leading-5">
                {conflictsList.map((c, idx) => (
                  <p key={idx} className="border-t border-rose-100 pt-1.5 first:border-0 first:pt-0">
                    • <strong>{c.staff}</strong> ({c.date}): {c.type}
                  </p>
                ))}
              </div>
            </section>
          ) : (
            <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
              <p className="text-xs font-medium uppercase text-slate-500">Ràng buộc nghiệp vụ</p>
              <p className="mt-2 text-xs leading-5 text-slate-600">
                Cùng một nhân sự trong cùng một ngày không được phép có đồng thời lịch thông tầm (L02), trực ca 24/24 (L01), hoặc ngày nghỉ bù.
              </p>
            </section>
          )}
        </aside>
      </div>
    </DashboardShell>
  );
}
