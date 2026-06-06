"use client";

import { useState, useEffect } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { api } from "@/lib/api";
import type { SchedulePeriod, Staff, Schedule, ConflictCheckResponse } from "@/types/api";
import type { ConflictItem } from "@/types/schedule";

export default function ServiceClinicPage() {
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
        console.error("Error loading service-clinic initial data:", err);
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
        console.error("Error loading service-clinic period data:", err);
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
        shiftTypeId: "L03", // PK Dịch vụ
      });

      // Reload schedules & conflicts
      const [schedulesData, conflictsData] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`)
      ]);
      setSchedules(schedulesData || []);
      setConflictResponse(conflictsData || null);
    } catch (err: any) {
      console.error("Error creating L03 shift:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi gán ca khám dịch vụ.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle shift delete
  const handleDeleteShift = async (schedId: number) => {
    if (!confirm("Bạn có chắc chắn muốn xóa lịch phòng khám dịch vụ này?")) return;
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
      console.error("Error deleting L03 shift:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi xóa ca trực.");
    }
  };

  // Construct active conflicts list for L03
  const conflictsList: ConflictItem[] = [];
  if (conflictResponse?.conflicts) {
    for (const conf of conflictResponse.conflicts) {
      for (const reason of conf.conflictReasons) {
        if (reason.toLowerCase().includes("dịch vụ") || reason.toLowerCase().includes("l03") || reason.toLowerCase().includes("chuyên gia")) {
          const dateObj = new Date(conf.workDate);
          const dateStr = `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}/${dateObj.getFullYear()}`;
          const severity = reason.toLowerCase().includes("nghỉ bù") || reason.toLowerCase().includes("trực 24/24") ? "Chặn lưu" : "Cảnh báo";
          
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

  // Filter schedules to L03 (PK dịch vụ) list
  const assignedL03Schedules = schedules.filter(s => s.shiftType.id === "L03");

  // Calculate statistics
  const totalL03Shifts = assignedL03Schedules.length;
  const l03ConflictsCount = conflictsList.length;
  const uniqueClinicsCount = new Set(activeStaff.filter(s => s.specialty).map(s => s.specialty?.id)).size || 4;

  const metricCards = [
    { label: "Ca dịch vụ", value: String(totalL03Shifts), helper: selectedPeriod ? selectedPeriod.periodName : "" },
    { label: "Đã gán", value: `${totalL03Shifts} ca`, helper: "Thực tế tháng" },
    { label: "Cảnh báo", value: String(l03ConflictsCount).padStart(2, "0"), helper: "Trùng lịch chuyên gia/nghỉ bù", tone: l03ConflictsCount > 0 ? "warning" : "neutral" },
    { label: "Chuyên khoa", value: String(uniqueClinicsCount).padStart(2, "0"), helper: "Đang hoạt động" },
  ];

  return (
    <DashboardShell
      activeCode="M04"
      description="Gán nhân sự phụ trách phòng khám dịch vụ theo ngày và kiểm tra trùng lịch chuyên gia."
      title="M04 - Lịch phòng khám dịch vụ"
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

      <div className="space-y-4 p-5 max-sm:p-3 flex-1 overflow-y-auto">
        {error && (
          <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            <p className="font-semibold">Đã xảy ra lỗi:</p>
            <p className="mt-1">{error}</p>
          </div>
        )}

        <section className="grid gap-4 md:grid-cols-4">
          {metricCards.map((metric) => (
            <div
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
              key={metric.label}
            >
              <p className="text-xs font-medium uppercase text-slate-500">{metric.label}</p>
              <p className="mt-3 text-2xl font-semibold">{metric.value}</p>
              <p className="mt-1 text-xs text-slate-500">{metric.helper}</p>
            </div>
          ))}
        </section>

        {isLoading && (
          <div className="flex items-center justify-center py-12 bg-white border border-slate-200 rounded-lg shadow-sm">
            <svg className="size-8 animate-spin text-slate-900" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
              <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
            </svg>
            <span className="ml-3 text-sm text-slate-500 font-medium">Đang tải lịch phòng khám dịch vụ...</span>
          </div>
        )}

        {!isLoading && periods.length > 0 && selectedPeriod && (
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
            <SectionCard
              description="Ô ngày có lịch chuyên gia hoặc nghỉ bù sẽ được tô cảnh báo"
              title="Bảng phân công phòng khám dịch vụ"
            >
              <div className="overflow-x-auto">
                <table className="w-full min-w-[650px] border-collapse text-sm text-left">
                  <thead className="bg-slate-50 text-xs font-medium uppercase text-slate-500">
                    <tr className="h-11 border-b border-slate-200">
                      <th className="px-4">Ngày</th>
                      <th className="px-4">Thứ</th>
                      <th className="px-4">Nhân sự</th>
                      <th className="px-4">Chuyên khoa</th>
                      <th className="px-4">Phòng khám</th>
                      <th className="px-4">Trạng thái</th>
                      <th className="px-4 text-center">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {assignedL03Schedules.length === 0 && (
                      <tr className="h-12 text-slate-500">
                        <td colSpan={7} className="px-4 text-center">Chưa có ca khám dịch vụ nào được gán</td>
                      </tr>
                    )}
                    {assignedL03Schedules.map((sched) => {
                      const dateObj = new Date(sched.workDate);
                      const weekdays = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
                      const weekdayStr = weekdays[dateObj.getDay()];

                      const formattedDate = `${String(dateObj.getDate()).padStart(2, "0")}/${String(dateObj.getMonth() + 1).padStart(2, "0")}`;

                      // Generate a room ID based on staff ID or index
                      const roomId = `PK Dịch vụ ${String((sched.staff.id % 3) + 1).padStart(2, "0")}`;

                      return (
                        <tr key={sched.id} className="h-12 hover:bg-slate-50">
                          <td className="px-4 font-medium">{formattedDate}</td>
                          <td className="px-4 text-slate-500">{weekdayStr}</td>
                          <td className="px-4 text-slate-900 font-semibold">{sched.staff.fullName}</td>
                          <td className="px-4 text-slate-600">
                            {activeStaff.find(s => s.id === sched.staff.id)?.specialty?.name || "Bác sĩ"}
                          </td>
                          <td className="px-4 text-slate-500 font-medium">{roomId}</td>
                          <td className="px-4">
                            <StatusBadge tone={sched.hasConflict ? "danger" : "success"}>
                              {sched.hasConflict ? "Cảnh báo" : "Hợp lệ"}
                            </StatusBadge>
                          </td>
                          <td className="px-4 text-center">
                            <button
                              onClick={() => handleDeleteShift(sched.id)}
                              className="inline-flex size-8 items-center justify-center rounded-md text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                              title="Xóa ca khám dịch vụ"
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

            <aside className="space-y-4">
              <SectionCard description="M04-F01: Tạo ca trực khám dịch vụ" title="Tạo lịch dịch vụ">
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
                    <span className="text-xs font-medium text-slate-500">Ngày làm việc</span>
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
                    {isSubmitting ? "Đang gán..." : "Thêm ca dịch vụ"}
                  </button>
                </form>
              </SectionCard>

              {conflictsList.length > 0 ? (
                <section className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
                  <p className="text-xs font-medium uppercase">Cảnh báo vi phạm</p>
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
                  <p className="text-xs font-medium uppercase text-slate-500">Ràng buộc xếp ca</p>
                  <p className="mt-2 text-xs leading-5 text-slate-600">
                    Lịch khám dịch vụ (L03) không được phép trùng ca khám chuyên gia (L04) của cùng chuyên gia và không được xếp vào ngày nghỉ bù của nhân sự đó.
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
