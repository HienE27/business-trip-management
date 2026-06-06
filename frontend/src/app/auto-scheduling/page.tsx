"use client";

import { useState, useEffect } from "react";
import { ScheduleMatrix } from "@/components/dashboard/ScheduleMatrix";
import { StaffLoadTable } from "@/components/dashboard/StaffLoadTable";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SimpleDataTable } from "@/components/ui/SimpleDataTable";
import { api } from "@/lib/api";
import type { 
  SchedulePeriod, 
  Staff, 
  Schedule, 
  AutoScheduleResult, 
  UnassignedDayReport, 
  AlgorithmMetrics,
  ReplacementSuggestion
} from "@/types/api";
import type { StaffScheduleRow, CalendarAssignment, ScheduleTone, StaffLoad } from "@/types/schedule";

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

export default function AutoSchedulingPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [previewResult, setPreviewResult] = useState<AutoScheduleResult | null>(null);
  
  const [algorithmType, setAlgorithmType] = useState<string>("GREEDY");
  const [maxIterations, setMaxIterations] = useState<number>(1000);
  
  const [unassignedReport, setUnassignedReport] = useState<UnassignedDayReport | null>(null);
  const [workloadData, setWorkloadData] = useState<any>(null);
  const [metricsHistory, setMetricsHistory] = useState<AlgorithmMetrics[]>([]);
  
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Replacement Suggestions State
  const [replacementSuggestion, setReplacementSuggestion] = useState<ReplacementSuggestion | null>(null);
  const [selectedCellInfo, setSelectedCellInfo] = useState<{
    staffName: string;
    dateStr: string;
    scheduleId: number;
    shiftTypeName: string;
  } | null>(null);
  const [isLoadingReplacement, setIsLoadingReplacement] = useState<boolean>(false);
  const [isApplyingReplacement, setIsApplyingReplacement] = useState<boolean>(false);

  // Load initial periods and staff list
  useEffect(() => {
    async function loadInitialData() {
      try {
        setIsLoading(true);
        setError(null);
        
        const [periodsData, staffData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active")
        ]);
        
        setPeriods(periodsData || []);
        setActiveStaff(staffData || []);
        
        if (periodsData && periodsData.length > 0) {
          // Sync with localStorage
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
        console.warn("Error loading initial data:", err);
        setError(err.message || "Không thể tải danh sách kỳ lịch hoặc nhân sự");
        setIsLoading(false);
      }
    }
    loadInitialData();
  }, []);

  // Load period data when selectedPeriodId changes
  useEffect(() => {
    if (!selectedPeriodId) return;
    localStorage.setItem("medschedule.selectedPeriodId", String(selectedPeriodId));
    setSuccessMessage(null);
    setPreviewResult(null); // Clear preview when changing periods

    async function loadPeriodData() {
      try {
        setIsLoading(true);
        setError(null);
        
        const [schedulesData, unassignedData, workloadChart, metricsData] = await Promise.all([
          api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
          api.get<UnassignedDayReport>(`/auto-schedule/unassigned/${selectedPeriodId}`),
          api.get<any>(`/auto-schedule/workload-chart/${selectedPeriodId}`),
          api.get<AlgorithmMetrics[]>(`/auto-schedule/metrics/period/${selectedPeriodId}`)
        ]);
        
        setSchedules(schedulesData || []);
        setUnassignedReport(unassignedData || null);
        setWorkloadData(workloadChart || null);
        setMetricsHistory(metricsData || []);
      } catch (err: any) {
        console.warn("Error loading period data:", err);
        setError(err.message || "Không thể tải dữ liệu tự động sắp xếp lịch cho kỳ này");
      } finally {
        setIsLoading(false);
      }
    }
    loadPeriodData();
  }, [selectedPeriodId]);

  // Run preview algorithm
  const handleRunPreview = async () => {
    if (!selectedPeriodId) return;
    try {
      setIsSubmitting(true);
      setError(null);
      setSuccessMessage(null);
      
      const body = {
        periodId: selectedPeriodId,
        algorithmType,
        maxIterations,
        autoAssign: true
      };
      
      const result = await api.post<AutoScheduleResult>("/auto-schedule/preview", body);
      setPreviewResult(result);
      setSuccessMessage("Đã tạo bản xem trước lịch xếp tự động!");
    } catch (err: any) {
      console.warn("Error running preview:", err);
      setError(err.message || "Đã xảy ra lỗi khi chạy thử nghiệm thuật toán.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Confirm and save auto-scheduling results
  const handleConfirmAndApply = async () => {
    if (!selectedPeriodId) return;
    try {
      setIsSubmitting(true);
      setError(null);
      setSuccessMessage(null);
      
      const body = {
        periodId: selectedPeriodId,
        algorithmType,
        maxIterations,
        autoAssign: true
      };
      
      await api.post<AutoScheduleResult>("/auto-schedule", body);
      
      // Reload period data
      const [schedulesData, unassignedData, workloadChart, metricsData] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<UnassignedDayReport>(`/auto-schedule/unassigned/${selectedPeriodId}`),
        api.get<any>(`/auto-schedule/workload-chart/${selectedPeriodId}`),
        api.get<AlgorithmMetrics[]>(`/auto-schedule/metrics/period/${selectedPeriodId}`)
      ]);
      
      setSchedules(schedulesData || []);
      setUnassignedReport(unassignedData || null);
      setWorkloadData(workloadChart || null);
      setMetricsHistory(metricsData || []);
      setPreviewResult(null); // Clear preview
      
      setSuccessMessage("Đã xếp lịch tự động và lưu thành công!");
    } catch (err: any) {
      console.warn("Error applying schedules:", err);
      setError(err.message || "Đã xảy ra lỗi khi lưu kết quả sắp xếp lịch.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Discard preview and show active schedules
  const handleDiscardPreview = () => {
    setPreviewResult(null);
    setSuccessMessage("Đã hủy xem trước và tải lại lịch đã lưu.");
  };

  // Click on a schedule cell (after confirmed / has scheduleId)
  const handleCellClick = async (staffName: string, dateStr: string, assignment: CalendarAssignment) => {
    if (!assignment.scheduleId) return;
    
    try {
      setIsLoadingReplacement(true);
      setSelectedCellInfo({
        staffName,
        dateStr,
        scheduleId: assignment.scheduleId,
        shiftTypeName: assignment.label
      });
      
      const suggestions = await api.get<ReplacementSuggestion>(`/auto-schedule/suggest-replacements/${assignment.scheduleId}`);
      setReplacementSuggestion(suggestions || null);
    } catch (err: any) {
      console.warn("Error loading replacements:", err);
      alert(err.message || "Không thể tải danh sách đề xuất thay thế.");
    } finally {
      setIsLoadingReplacement(false);
    }
  };

  // Apply replacement for scheduleId
  const handleApplyReplacement = async (candidateId: number) => {
    if (!selectedCellInfo || !selectedPeriodId || !replacementSuggestion) return;
    try {
      setIsApplyingReplacement(true);
      
      await api.put(`/schedules/${selectedCellInfo.scheduleId}`, {
        periodId: selectedPeriodId,
        workDate: selectedCellInfo.dateStr,
        staffId: candidateId,
        shiftTypeId: replacementSuggestion.shiftTypeId
      });
      
      // Close suggestion modal
      setReplacementSuggestion(null);
      setSelectedCellInfo(null);
      
      // Reload period data
      const [schedulesData, unassignedData, workloadChart, metricsData] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<UnassignedDayReport>(`/auto-schedule/unassigned/${selectedPeriodId}`),
        api.get<any>(`/auto-schedule/workload-chart/${selectedPeriodId}`),
        api.get<AlgorithmMetrics[]>(`/auto-schedule/metrics/period/${selectedPeriodId}`)
      ]);
      
      setSchedules(schedulesData || []);
      setUnassignedReport(unassignedData || null);
      setWorkloadData(workloadChart || null);
      setMetricsHistory(metricsData || []);
      
      setSuccessMessage("Đã cập nhật nhân sự thay thế thành công!");
    } catch (err: any) {
      console.warn("Error applying replacement:", err);
      alert(err.message || "Đã xảy ra lỗi khi thực hiện thay thế.");
    } finally {
      setIsApplyingReplacement(false);
    }
  };

  // Helper function to resolve shift info from ID
  function getShiftInfo(shiftTypeId: string): { label: string; tone: ScheduleTone } {
    switch (shiftTypeId) {
      case "L01":
        return { label: "24/24", tone: "duty24" };
      case "L02":
        return { label: "Thông tầm", tone: "allDay" };
      case "L03":
        return { label: "PK dịch vụ", tone: "serviceClinic" };
      case "L04":
        return { label: "PK chuyên gia", tone: "expertClinic" };
      default:
        return { label: "Trống", tone: "neutral" };
    }
  }

  // Map workloads to StaffLoad structure
  const getLoadsToDisplay = (): StaffLoad[] => {
    if (previewResult) {
      // Local calculation based on proposed schedules
      return activeStaff.map(s => {
        const staffSchedules = previewResult.schedules.filter(sched => sched.staffId === s.id);
        const l01 = staffSchedules.filter(sched => sched.shiftTypeId === "L01").length;
        const l02 = staffSchedules.filter(sched => sched.shiftTypeId === "L02").length;
        const clinics = staffSchedules.filter(sched => sched.shiftTypeId === "L03" || sched.shiftTypeId === "L04").length;
        return {
          name: s.fullName,
          duty24: l01,
          allDay: l02,
          clinics: clinics
        };
      }).sort((a, b) => (b.duty24 + b.allDay + b.clinics) - (a.duty24 + a.allDay + a.clinics));
    }
    
    // Normal mode: from backend chart data
    return workloadData?.staffWorkloadData?.map((item: any) => ({
      name: item.staffName,
      duty24: item.L01 || 0,
      allDay: item.L02 || 0,
      clinics: (item.L03 || 0) + (item.L04 || 0)
    })) || [];
  };

  // Format data for ScheduleMatrix
  const selectedPeriod = periods.find(p => p.id === selectedPeriodId);
  const staffColumns = activeStaff.map(s => s.fullName);
  const scheduleRows: StaffScheduleRow[] = [];

  if (selectedPeriod) {
    const start = new Date(selectedPeriod.startDate);
    const end = new Date(selectedPeriod.endDate);
    const listToUse = previewResult ? previewResult.schedules : schedules;
    
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const currentDateStr = d.toISOString().split("T")[0];
      const dayNum = String(d.getDate()).padStart(2, "0");
      
      const weekdays = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
      const weekdayStr = weekdays[d.getDay()];
      
      const assignments: Record<string, CalendarAssignment> = {};
      for (const s of activeStaff) {
        assignments[s.fullName] = { label: "Trống", tone: "neutral" };
      }
      
      if (previewResult) {
        const daySchedules = (listToUse as any[]).filter(s => s.workDate === currentDateStr);
        for (const sched of daySchedules) {
          const staffName = sched.staffName;
          if (assignments[staffName]) {
            const { label, tone } = getShiftInfo(sched.shiftTypeId);
            assignments[staffName] = { 
              label, 
              tone,
              scheduleId: sched.scheduleId,
              shiftTypeId: sched.shiftTypeId
            };
          }
        }
      } else {
        const daySchedules = (listToUse as any[]).filter(s => s.workDate === currentDateStr);
        for (const sched of daySchedules) {
          const staffName = sched.staff.fullName;
          if (assignments[staffName]) {
            let label = "Trống";
            let tone: ScheduleTone = "neutral";
            
            if (sched.hasConflict) {
              label = "Cảnh báo";
              tone = "warning";
            } else {
              const info = getShiftInfo(sched.shiftType.id);
              label = info.label;
              tone = info.tone;
            }
            
            assignments[staffName] = { 
              label, 
              tone, 
              scheduleId: sched.id,
              shiftTypeId: sched.shiftType.id
            };
          }
        }
      }
      
      // Overlay calculated compensation days
      for (const s of activeStaff) {
        const staffL01Schedules = listToUse.filter(sched => {
          if (previewResult) {
            const summary = sched as any;
            return summary.staffId === s.id && summary.shiftTypeId === "L01";
          } else {
            const schedule = sched as any;
            return schedule.staff.id === s.id && schedule.shiftType.id === "L01";
          }
        });
        
        for (const sched of staffL01Schedules) {
          const l01Date = new Date(sched.workDate);
          const compDate = calculateCompensationDateOnFrontend(l01Date);
          const compDateStr = compDate.toISOString().split("T")[0];
          
          if (compDateStr === currentDateStr) {
            assignments[s.fullName] = { label: "Nghỉ bù", tone: "compLeave" };
          }
        }
      }
      
      scheduleRows.push({
        day: dayNum,
        weekday: weekdayStr,
        assignments,
        dateStr: currentDateStr
      });
    }
  }

  // Pre-formatted logs for "Tiến trình thuật toán"
  const stepLogs = [
    { step: "Bước 1", desc: "Quét cấu hình & Ràng buộc", status: isLoading ? "Đang xử lý" : "Hoàn thành" },
    { step: "Bước 2", desc: "Áp dụng danh sách ngoại lệ", status: isLoading ? "Chờ xử lý" : "Hoàn thành" },
    { step: "Bước 3", desc: "Khởi tạo ma trận phân bổ tối ưu", status: isSubmitting ? "Đang chạy" : (previewResult ? "Hoàn thành" : "Sẵn sàng") },
    { step: "Bước 4", desc: "Kiểm tra luật nghỉ bù tự động (L01)", status: previewResult ? "Hoàn thành" : "Sẵn sàng" },
  ];

  return (
    <DashboardShell
      activeCode="M07"
      description="Tự động phân công lịch theo thuật toán, kiểm tra ràng buộc và xem trước trước khi áp dụng."
      title="M07 - Tự động sắp xếp lịch"
    >
      {/* Configuration Control Panel */}
      <div className="mx-5 my-4 rounded-xl border border-slate-200 bg-white p-4 shadow-[0_1px_3px_rgba(0,0,0,0.05)] flex flex-wrap gap-4 items-end justify-between">
        <div className="flex flex-wrap gap-5 items-center">
          <div className="flex flex-col gap-1.5">
            <span className="text-2xs font-bold text-slate-400 uppercase tracking-wider">Kỳ lịch trực</span>
            <select 
              value={selectedPeriodId || ""} 
              onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
              disabled={isLoading || isSubmitting}
              className="h-9 w-48 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-medium focus:border-slate-400 focus:outline-none disabled:opacity-50"
            >
              {periods.map(p => (
                <option key={p.id} value={p.id}>{p.periodName} ({p.status === "PUBLISHED" ? "Hiện tại" : "Bản nháp"})</option>
              ))}
            </select>
          </div>
          
          <div className="flex flex-col gap-1.5">
            <span className="text-2xs font-bold text-slate-400 uppercase tracking-wider">Thuật toán tối ưu</span>
            <select 
              value={algorithmType} 
              onChange={(e) => setAlgorithmType(e.target.value)}
              disabled={isLoading || isSubmitting}
              className="h-9 w-48 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-medium focus:border-slate-400 focus:outline-none"
            >
              <option value="GREEDY">Greedy (Tham lam)</option>
              <option value="ROUND_ROBIN">Round Robin (Phân bổ đều)</option>
              <option value="BACKTRACKING">Backtracking (Quay lui)</option>
            </select>
          </div>

          {algorithmType === "BACKTRACKING" && (
            <div className="flex flex-col gap-1.5">
              <span className="text-2xs font-bold text-slate-400 uppercase tracking-wider">Số vòng lặp tối đa</span>
              <input 
                type="number" 
                value={maxIterations} 
                onChange={(e) => setMaxIterations(Number(e.target.value))}
                disabled={isLoading || isSubmitting}
                className="h-9 w-32 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-medium focus:border-slate-400 focus:outline-none"
                min={1}
                max={50000}
              />
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          {previewResult ? (
            <>
              <button
                onClick={handleDiscardPreview}
                disabled={isSubmitting}
                className="h-9 rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors disabled:opacity-50"
              >
                Hủy xem trước
              </button>
              <button
                onClick={handleConfirmAndApply}
                disabled={isSubmitting}
                className="h-9 rounded-lg bg-emerald-600 px-4 text-xs font-semibold text-white hover:bg-emerald-700 transition-colors shadow-sm disabled:opacity-50"
              >
                {isSubmitting ? "Đang áp dụng..." : "Xác nhận & Áp dụng"}
              </button>
            </>
          ) : (
            <button
              onClick={handleRunPreview}
              disabled={isLoading || isSubmitting || !selectedPeriodId}
              className="h-9 rounded-lg bg-slate-900 px-4 text-xs font-semibold text-white hover:bg-slate-800 transition-colors shadow-sm disabled:opacity-50"
            >
              {isSubmitting ? "Đang xử lý..." : "Chạy tự động (Xem trước)"}
            </button>
          )}
        </div>
      </div>

      <div className="grid gap-4 px-5 pb-5 max-sm:p-3 2xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-4">
          {/* Status Message Alerts */}
          {error && (
            <div className="rounded-lg border border-rose-100 bg-rose-50 p-4 text-xs text-rose-700 font-medium flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-rose-500 shrink-0" />
              {error}
            </div>
          )}
          {successMessage && (
            <div className="rounded-lg border border-emerald-100 bg-emerald-50 p-4 text-xs text-emerald-700 font-medium flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0" />
              {successMessage}
            </div>
          )}
          {previewResult && (
            <div className="rounded-lg border border-blue-100 bg-blue-50/50 p-4 text-xs text-blue-700 font-medium flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse shrink-0" />
                <span>Bạn đang xem trước kết quả xếp lịch tự động. Vui lòng bấm <strong>"Xác nhận & Áp dụng"</strong> để lưu chính thức.</span>
              </div>
              <span className="text-3xs uppercase bg-blue-100 text-blue-800 px-1.5 py-0.5 rounded font-bold">Xem trước</span>
            </div>
          )}

          {/* Metric Cards */}
          <section className="grid gap-4 sm:grid-cols-2 md:grid-cols-4">
            {[
              [
                "Thuật toán", 
                previewResult 
                  ? (previewResult.algorithmType === "GREEDY" ? "Greedy (Tham lam)" : previewResult.algorithmType === "ROUND_ROBIN" ? "Round Robin" : "Backtracking") 
                  : (algorithmType === "GREEDY" ? "Greedy" : algorithmType === "ROUND_ROBIN" ? "Round Robin" : "Backtracking"), 
                previewResult ? `Thời gian: ${previewResult.executionTimeMs}ms` : "Sẵn sàng chạy"
              ],
              [
                "Tỷ lệ phủ", 
                previewResult ? `${previewResult.coverageRate}%` : (schedules.length > 0 ? "100%" : "0%"), 
                previewResult ? `Xếp được: ${previewResult.totalSchedulesCreated} ca` : `Đã lưu: ${schedules.length} ca`
              ],
              [
                "Ngày chưa đủ", 
                unassignedReport ? String(unassignedReport.totalUnassignedDays).padStart(2, "0") : "00", 
                "Cần xử lý thủ công"
              ],
              [
                "Vi phạm ràng buộc", 
                previewResult ? String(previewResult.conflictCount).padStart(2, "0") : "00", 
                previewResult ? "Sau quét ràng buộc" : "Lịch đã lưu"
              ],
            ].map(([label, value, helper]) => (
              <div
                className="rounded-xl border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
                key={label}
              >
                <p className="text-3xs font-bold uppercase tracking-wider text-slate-400">{label}</p>
                <p className="mt-3 text-xl font-bold text-slate-800">{value}</p>
                <p className="mt-1 text-2xs text-slate-500 font-medium">{helper}</p>
              </div>
            ))}
          </section>

          {/* Main Matrix Schedule */}
          {isLoading ? (
            <div className="rounded-lg border border-slate-200 bg-white p-12 text-center text-xs text-slate-500 shadow-sm flex flex-col items-center justify-center gap-3">
              <div className="w-8 h-8 rounded-full border-2 border-slate-200 border-t-slate-800 animate-spin" />
              <span>Đang tải thông tin lịch trực...</span>
            </div>
          ) : scheduleRows.length > 0 ? (
            <div className="relative">
              <ScheduleMatrix 
                staff={staffColumns} 
                rows={scheduleRows} 
                onCellClick={previewResult ? undefined : handleCellClick} 
              />
              {!previewResult && (
                <div className="mt-2 text-right">
                  <span className="text-4xs text-slate-400 font-semibold italic">* Nhấp vào một ô ca trực đã phân công để xem đề xuất thay thế nhân sự</span>
                </div>
              )}
            </div>
          ) : (
            <div className="rounded-lg border border-slate-200 bg-white p-12 text-center text-xs text-slate-500 shadow-sm">
              Không tìm thấy cấu hình ngày trực trong kỳ được chọn. Vui lòng tạo cấu hình kỳ lịch trước.
            </div>
          )}

          {/* Heuristic Execution Steps Status */}
          <SectionCard description="Trạng thái thực thi luồng tối ưu M07" title="Tiến trình thuật toán">
            <SimpleDataTable
              headers={["Bước", "Mô tả xử lý", "Trạng thái"]}
              rows={stepLogs.map(log => [log.step, log.desc, log.status])}
              statusColumn={2}
            />
          </SectionCard>
        </div>

        {/* Sidebar panels */}
        <aside className="space-y-4">
          {/* Missing Days Report (M07-F06) */}
          <SectionCard description="Danh sách các ca trực chưa đủ nhân sự" title="Báo cáo ngày thiếu ca trực">
            {unassignedReport && unassignedReport.unassignedDays && unassignedReport.unassignedDays.length > 0 ? (
              <div className="space-y-2">
                {unassignedReport.unassignedDays.slice(0, 8).map((day, idx) => (
                  <div className="p-2.5 rounded-lg border border-slate-100 bg-slate-50/50 flex items-center justify-between text-xs" key={idx}>
                    <div>
                      <div className="font-semibold text-slate-800">
                        {day.workDate} ({day.dayOfWeek})
                      </div>
                      <div className="text-slate-500 mt-0.5 text-3xs font-medium">
                        Ca {day.shiftTypeName} {day.specialty ? `- Khoa ${day.specialty}` : ""}
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <span className="inline-flex items-center rounded-full bg-rose-100 px-2 py-0.5 text-2xs font-semibold text-rose-800">
                        Thiếu {day.missingCount}
                      </span>
                    </div>
                  </div>
                ))}
                {unassignedReport.unassignedDays.length > 8 && (
                  <p className="text-center text-3xs text-slate-400 font-semibold mt-2 pt-1 border-t border-slate-100">
                    Và {unassignedReport.unassignedDays.length - 8} ngày khác...
                  </p>
                )}
              </div>
            ) : (
              <div className="p-4 rounded-lg bg-emerald-50 border border-emerald-100 text-center text-xs text-emerald-800 font-medium">
                🎉 Đã phân bổ 100%! Không có ngày nào thiếu nhân sự.
              </div>
            )}
          </SectionCard>

          {/* Workload balancing chart (M07-F09) */}
          <StaffLoadTable loads={getLoadsToDisplay()} />

          {/* Optimization history dashboard metrics (M07-F12) */}
          <SectionCard description="Các lượt chạy tối ưu gần nhất trong kỳ" title="Lịch sử tối ưu hóa kỳ này">
            {metricsHistory && metricsHistory.length > 0 ? (
              <div className="space-y-2 max-h-[320px] overflow-y-auto pr-1">
                {metricsHistory.map((metric) => (
                  <div className="p-3 rounded-lg border border-slate-100 bg-slate-50/40 text-xs space-y-1" key={metric.id}>
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-slate-800">
                        {metric.algorithmType === "GREEDY" ? "Greedy (Tham lam)" : metric.algorithmType === "ROUND_ROBIN" ? "Round Robin" : "Backtracking"}
                      </span>
                      <span className="text-slate-400 text-3xs">
                        {new Date(metric.createdAt).toLocaleTimeString("vi-VN")}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-y-1 text-slate-500 mt-1.5 text-3xs font-medium">
                      <div>Thời gian: <span className="font-bold text-slate-700">{metric.executionTimeMs}ms</span></div>
                      <div>Tỷ lệ phủ: <span className="font-bold text-slate-700">{metric.coverageRate}%</span></div>
                      <div>Điểm tải: <span className="font-bold text-slate-700">{metric.balanceScore}</span></div>
                      <div>Vi phạm: <span className="font-bold text-rose-600">{metric.conflictCount}</span></div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-center text-2xs text-slate-400 py-4 font-semibold italic">Chưa có lịch sử tối ưu hóa nào cho kỳ này.</p>
            )}
          </SectionCard>
        </aside>
      </div>

      {/* Replacement Suggestion Popup Modal (M07-F08) */}
      {replacementSuggestion && selectedCellInfo && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
          <div className="w-full max-w-xl rounded-xl border border-slate-200 bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-sm font-bold text-slate-900">Đề xuất người thay thế</h3>
                <p className="text-2xs text-slate-500 font-medium mt-0.5">
                  Đang xem ca trực ngày {selectedCellInfo.dateStr} (Ca {selectedCellInfo.shiftTypeName}) của {selectedCellInfo.staffName}
                </p>
              </div>
              <button 
                onClick={() => {
                  setReplacementSuggestion(null);
                  setSelectedCellInfo(null);
                }}
                className="text-slate-400 hover:text-slate-600 text-lg font-bold p-1 leading-none"
              >
                &times;
              </button>
            </div>
            
            <div className="mt-4 max-h-[360px] overflow-y-auto pr-1 space-y-2.5">
              {isLoadingReplacement ? (
                <div className="text-center py-6 text-xs text-slate-500 flex flex-col items-center justify-center gap-2">
                  <div className="w-6 h-6 rounded-full border-2 border-slate-200 border-t-slate-800 animate-spin" />
                  <span>Đang tải các đề xuất...</span>
                </div>
              ) : replacementSuggestion.suggestions && replacementSuggestion.suggestions.length > 0 ? (
                replacementSuggestion.suggestions.map((candidate) => (
                  <div 
                    key={candidate.staffId} 
                    className={`flex items-center justify-between p-3 rounded-lg border transition-all ${
                      candidate.isAvailable 
                        ? "border-emerald-100 bg-emerald-50/30 hover:bg-emerald-50" 
                        : "border-slate-100 bg-slate-50/20"
                    }`}
                  >
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-slate-800 text-xs">{candidate.staffName}</span>
                        {candidate.specialty && (
                          <span className="inline-flex items-center rounded bg-slate-100 px-1.5 py-0.5 text-3xs font-semibold text-slate-600">
                            Khoa {candidate.specialty}
                          </span>
                        )}
                        {candidate.isAvailable ? (
                          <span className="inline-flex items-center rounded bg-emerald-100 px-1.5 py-0.2 text-3xs font-bold text-emerald-800">
                            SẴN SÀNG
                          </span>
                        ) : (
                          <span className="inline-flex items-center rounded bg-rose-100 px-1.5 py-0.2 text-3xs font-bold text-rose-800">
                            BẬN
                          </span>
                        )}
                      </div>
                      <div className="mt-1 flex items-center gap-4 text-3xs text-slate-500 font-semibold">
                        <span>Số ca đã trực kỳ này: {candidate.currentWorkload} ca</span>
                      </div>
                      {candidate.conflicts && candidate.conflicts.length > 0 && (
                        <div className="mt-2 space-y-1 border-t border-slate-100/50 pt-1.5">
                          {candidate.conflicts.map((conf, index) => (
                            <p key={index} className="text-3xs text-rose-600 flex items-start gap-1 font-medium">
                              <span className="text-rose-500">•</span>
                              {conf}
                            </p>
                          ))}
                        </div>
                      )}
                    </div>
                    {candidate.isAvailable && (
                      <button 
                        disabled={isApplyingReplacement}
                        onClick={() => handleApplyReplacement(candidate.staffId)}
                        className="h-7 rounded-lg bg-slate-900 px-3 text-3xs font-bold text-white transition-colors hover:bg-slate-800 disabled:opacity-50"
                      >
                        {isApplyingReplacement ? "Đang lưu..." : "Thay thế"}
                      </button>
                    )}
                  </div>
                ))
              ) : (
                <div className="text-center py-6 text-2xs text-slate-400 italic font-semibold">
                  Không tìm thấy nhân sự khả dụng nào phù hợp.
                </div>
              )}
            </div>
            
            <div className="mt-5 flex justify-end border-t border-slate-100 pt-3">
              <button 
                onClick={() => {
                  setReplacementSuggestion(null);
                  setSelectedCellInfo(null);
                }}
                className="h-8 rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </DashboardShell>
  );
}
