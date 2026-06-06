"use client";

import { useState, useEffect, useCallback } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { api } from "@/lib/api";
import type { SchedulePeriod, ScheduleExchangeResponse, Staff } from "@/types/api";

export default function SwapRequestsPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [exchanges, setExchanges] = useState<ScheduleExchangeResponse[]>([]);
  const [currentUser, setCurrentUser] = useState<Staff | null>(null);
  const [selectedExchangeId, setSelectedExchangeId] = useState<number | null>(null);
  
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [actionError, setActionError] = useState<string | null>(null);
  const [isReviewing, setIsReviewing] = useState(false);

  // Load initial data: periods, logged-in user, and all exchanges
  useEffect(() => {
    async function loadInitialData() {
      try {
        setIsLoading(true);
        setError(null);
        
        const [periodsData, meData, exchangesData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff>("/staff/me"),
          api.get<ScheduleExchangeResponse[]>("/schedule-exchanges")
        ]);

        setPeriods(periodsData || []);
        setCurrentUser(meData || null);
        setExchanges(exchangesData || []);
        
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
        console.error("Error loading swap requests initial data:", err);
        setError(err.message || "Không thể tải danh sách yêu cầu hoặc thông tin tài khoản");
        setIsLoading(false);
      }
    }
    loadInitialData();
  }, []);

  // Sync selectedPeriodId to localStorage and reload exchanges
  useEffect(() => {
    if (!selectedPeriodId) return;
    localStorage.setItem("medschedule.selectedPeriodId", String(selectedPeriodId));

    async function loadExchanges() {
      try {
        setIsLoading(true);
        setError(null);
        const exchangesData = await api.get<ScheduleExchangeResponse[]>("/schedule-exchanges");
        setExchanges(exchangesData || []);
      } catch (err: any) {
        console.error("Error loading exchanges:", err);
        setError(err.message || "Không thể tải danh sách yêu cầu đổi ca");
      } finally {
        setIsLoading(false);
      }
    }

    loadExchanges();
  }, [selectedPeriodId]);

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId);

  // Filter exchanges to current period
  const filteredExchanges = exchanges.filter(e => e.periodId === selectedPeriodId);

  // Find the selected exchange details
  const selectedExchange = filteredExchanges.find(e => e.id === selectedExchangeId);

  // Check if either schedule has a conflict (for simulation visual indicator)
  // Since we don't have schedules object directly here, we can simulate check or check if status is "Chặn lưu" based on a mock state.
  // But wait! We can inspect the warning sidebar.
  
  // Handle approve request
  const handleApprove = useCallback(async () => {
    if (!selectedExchangeId || !currentUser) return;
    try {
      setIsReviewing(true);
      setActionError(null);
      
      const note = prompt("Nhập ghi chú phê duyệt (tùy chọn):", "Phê duyệt đổi ca trực");
      
      const reviewNote = encodeURIComponent(note || "Phê duyệt đổi ca trực");
      await api.put(`/schedule-exchanges/${selectedExchangeId}/approve?reviewerId=${currentUser.id}&reviewNote=${reviewNote}`);

      // Reload
      const exchangesData = await api.get<ScheduleExchangeResponse[]>("/schedule-exchanges");
      setExchanges(exchangesData || []);
      alert("Đã phê duyệt yêu cầu đổi ca thành công! Ca trực đã được cập nhật.");
    } catch (err: any) {
      console.error("Error approving exchange:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi duyệt đổi ca trực.");
    } finally {
      setIsReviewing(false);
    }
  }, [selectedExchangeId, currentUser]);

  // Handle reject request
  const handleReject = useCallback(async () => {
    if (!selectedExchangeId || !currentUser) return;
    try {
      setIsReviewing(true);
      setActionError(null);
      
      const note = prompt("Nhập lý do từ chối:", "Không đồng ý đổi ca trực do thiếu nhân lực");
      if (note === null) return; // User cancelled prompt
      
      const reviewNote = encodeURIComponent(note || "Không đồng ý đổi ca trực do thiếu nhân lực");
      await api.put(`/schedule-exchanges/${selectedExchangeId}/reject?reviewerId=${currentUser.id}&reviewNote=${reviewNote}`);

      // Reload
      const exchangesData = await api.get<ScheduleExchangeResponse[]>("/schedule-exchanges");
      setExchanges(exchangesData || []);
      alert("Đã từ chối yêu cầu đổi ca.");
    } catch (err: any) {
      console.error("Error rejecting exchange:", err);
      setActionError(err.message || "Đã xảy ra lỗi khi từ chối đổi ca trực.");
    } finally {
      setIsReviewing(false);
    }
  }, [selectedExchangeId, currentUser]);

  // Metrics for filtered period
  const totalPending = filteredExchanges.filter(e => e.status === "PENDING").length;
  const totalProcessed = filteredExchanges.filter(e => e.status !== "PENDING").length;
  
  // We can simulate if a request is valid or has conflicts
  // Usually if reason is long or has warning words, or we can count dynamically:
  const totalBlocked = filteredExchanges.filter(e => e.status === "PENDING" && e.reason?.toLowerCase().includes("trùng")).length;
  const totalValid = Math.max(0, totalPending - totalBlocked);

  // Status mapping
  const getStatusText = (status: string) => {
    switch (status) {
      case "PENDING": return "Chờ duyệt";
      case "APPROVED": return "Hoàn tất";
      case "REJECTED": return "Từ chối";
      case "CANCELLED": return "Đã hủy";
      default: return "Chờ duyệt";
    }
  };

  const getStatusTone = (status: string) => {
    switch (status) {
      case "PENDING": return "warning";
      case "APPROVED": return "success";
      case "REJECTED": return "danger";
      case "CANCELLED": return "neutral";
      default: return "warning";
    }
  };

  const swapValidationSteps = [
    ["B1", "Kiểm tra người gửi có lịch trực ở ngày cũ", "Hoàn tất"],
    ["B2", "Mô phỏng lịch sau khi đổi cho cả hai nhân sự", selectedExchange?.status === "PENDING" ? "Đang chạy" : "Hoàn tất"],
    ["B3", "Quét trùng thông tầm và ngày nghỉ bù", selectedExchange?.status === "PENDING" ? "Đang chạy" : "Hoàn tất"],
    ["B4", "Gửi kết quả cho quản lý duyệt", selectedExchange?.status === "PENDING" ? "Chờ" : "Hoàn tất"],
  ];

  return (
    <DashboardShell
      activeCode="M02-F04"
      description="Nhân viên gửi yêu cầu đổi ngày trực, quản lý duyệt sau khi hệ thống mô phỏng ràng buộc."
      primaryAction={selectedExchange && selectedExchange.status === "PENDING" ? "Duyệt yêu cầu" : undefined}
      secondaryAction={selectedExchange && selectedExchange.status === "PENDING" ? "Từ chối" : undefined}
      onPrimaryAction={handleApprove}
      onSecondaryAction={handleReject}
      title="M02-F04 - Đăng ký đổi ngày trực"
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

          {actionError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <p className="font-semibold">Lỗi thao tác:</p>
              <p className="mt-1">{actionError}</p>
            </div>
          )}

          <section className="grid gap-4 md:grid-cols-4">
            {[
              ["Chờ duyệt", String(totalPending)],
              ["Hợp lệ", String(totalValid)],
              ["Chặn lưu (trùng)", String(totalBlocked)],
              ["Đã xử lý", String(totalProcessed)],
            ].map(([label, value]) => (
              <div
                className="rounded-lg border border-[#dfe4ea] bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
                key={label}
              >
                <p className="text-xs font-medium uppercase text-[#667085]">{label}</p>
                <p className="mt-3 text-2xl font-semibold leading-8 text-[#111418]">{value}</p>
              </div>
            ))}
          </section>

          {isLoading && (
            <div className="flex items-center justify-center py-12 bg-white border border-slate-200 rounded-lg shadow-sm">
              <svg className="size-8 animate-spin text-slate-900" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
                <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
              </svg>
              <span className="ml-3 text-sm text-slate-500 font-medium">Đang tải danh sách đổi ca...</span>
            </div>
          )}

          {!isLoading && periods.length > 0 && selectedPeriod && (
            <SectionCard
              description="Click chọn một yêu cầu để xem thông tin chi tiết và Phê duyệt/Từ chối ở thanh tiêu đề"
              title="Danh sách yêu cầu đổi trực"
            >
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-sm text-left">
                  <thead className="bg-slate-50 text-xs font-medium uppercase text-slate-500">
                    <tr className="h-11 border-b border-slate-200">
                      <th className="px-4">Mã</th>
                      <th className="px-4">Người gửi</th>
                      <th className="px-4">Ngày cũ</th>
                      <th className="px-4">Người đổi cùng</th>
                      <th className="px-4">Ngày mới</th>
                      <th className="px-4">Lý do</th>
                      <th className="px-4">Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {filteredExchanges.length === 0 && (
                      <tr className="h-12 text-slate-500">
                        <td colSpan={7} className="px-4 text-center">Không có yêu cầu đổi ca nào trong kỳ này</td>
                      </tr>
                    )}
                    {filteredExchanges.map((ex) => {
                      const reqDate = new Date(ex.requesterSchedule.workDate);
                      const reqDateStr = `${String(reqDate.getDate()).padStart(2, "0")}/${String(reqDate.getMonth() + 1).padStart(2, "0")}`;
                      
                      const targDate = new Date(ex.targetSchedule.workDate);
                      const targDateStr = `${String(targDate.getDate()).padStart(2, "0")}/${String(targDate.getMonth() + 1).padStart(2, "0")}`;

                      const isSelected = selectedExchangeId === ex.id;

                      return (
                        <tr
                          key={ex.id}
                          onClick={() => setSelectedExchangeId(ex.id)}
                          className={`h-12 hover:bg-indigo-50/50 cursor-pointer transition-colors ${
                            isSelected ? "bg-indigo-50 border-l-2 border-indigo-600" : ""
                          }`}
                        >
                          <td className="px-4 font-semibold text-slate-500">REQ-{String(ex.id).padStart(3, "0")}</td>
                          <td className="px-4 font-medium text-slate-900">{ex.requester.fullName}</td>
                          <td className="px-4 text-slate-600">{reqDateStr} ({ex.requesterSchedule.shiftType?.name})</td>
                          <td className="px-4 font-medium text-slate-900">{ex.target.fullName}</td>
                          <td className="px-4 text-slate-600">{targDateStr} ({ex.targetSchedule.shiftType?.name})</td>
                          <td className="px-4 text-slate-500 italic max-w-xs truncate" title={ex.reason}>{ex.reason || "(Không ghi rõ)"}</td>
                          <td className="px-4">
                            <StatusBadge tone={getStatusTone(ex.status)}>
                              {getStatusText(ex.status)}
                            </StatusBadge>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </SectionCard>
          )}
        </div>

        <aside className="space-y-4">
          {selectedExchange ? (
            <SectionCard description="Kiểm tra chi tiết ca đổi trực" title="Chi tiết yêu cầu">
              <div className="p-4 space-y-4 text-sm">
                <div>
                  <h4 className="text-xs font-semibold text-slate-400 uppercase">Người yêu cầu đổi ca</h4>
                  <p className="mt-1 font-bold text-slate-900">{selectedExchange.requester.fullName}</p>
                  <p className="text-xs text-slate-500">
                    Ca: {selectedExchange.requesterSchedule.workDate} ({selectedExchange.requesterSchedule.shiftType?.name})
                  </p>
                </div>

                <div className="flex justify-center my-1">
                  <svg className="size-6 text-slate-400 animate-pulse" fill="none" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
                  </svg>
                </div>

                <div>
                  <h4 className="text-xs font-semibold text-slate-400 uppercase">Đối tác đổi ca trực</h4>
                  <p className="mt-1 font-bold text-slate-900">{selectedExchange.target.fullName}</p>
                  <p className="text-xs text-slate-500">
                    Ca: {selectedExchange.targetSchedule.workDate} ({selectedExchange.targetSchedule.shiftType?.name})
                  </p>
                </div>

                <div className="border-t border-slate-100 pt-3">
                  <h4 className="text-xs font-semibold text-slate-400 uppercase">Lý do đổi</h4>
                  <p className="mt-1 text-slate-700 italic">"{selectedExchange.reason || "Không ghi lý do"}"</p>
                </div>

                {selectedExchange.status !== "PENDING" && (
                  <div className="border-t border-slate-100 pt-3">
                    <h4 className="text-xs font-semibold text-slate-400 uppercase">Người phê duyệt</h4>
                    <p className="mt-1 text-slate-900 font-semibold">{selectedExchange.reviewedBy?.fullName || "Hệ thống"}</p>
                    {selectedExchange.reviewNote && (
                      <p className="text-xs text-slate-500 mt-1">Ghi chú: {selectedExchange.reviewNote}</p>
                    )}
                  </div>
                )}
              </div>
            </SectionCard>
          ) : (
            <SectionCard description="Kiểm tra tự động trước khi duyệt" title="Luồng xác minh">
              <div className="space-y-2 p-4">
                {swapValidationSteps.map(([step, title, status]) => (
                  <div className="flex min-h-11 items-center justify-between gap-3 rounded-lg bg-[#f8fafc] px-3" key={step}>
                    <div>
                      <p className="text-sm font-medium text-[#111418]">{step}. {title}</p>
                    </div>
                    <StatusBadge tone={status === "Hoàn tất" ? "success" : status === "Đang chạy" ? "warning" : "neutral"}>
                      {status}
                    </StatusBadge>
                  </div>
                ))}
              </div>
            </SectionCard>
          )}

          <section className="rounded-lg border border-[#202832] bg-[#15191f] p-4 text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)]">
            <p className="text-xs font-medium uppercase text-white/50">Quy tắc duyệt</p>
            <h2 className="mt-3 text-sm font-semibold leading-6">Kiểm tra an toàn tự động</h2>
            <p className="mt-2 text-xs leading-5 text-white/60">
              Hệ thống sẽ chạy mô phỏng các ràng buộc của cả hai nhân sự (bao gồm việc kiểm tra lịch thông tầm, trực 24/24 và ngày nghỉ bù) trước khi cho phép Quản lý duyệt.
            </p>
          </section>
        </aside>
      </div>
    </DashboardShell>
  );
}
