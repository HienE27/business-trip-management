"use client";

import { useEffect, useMemo, useState } from "react";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictCheckResponse, ConflictDetail, Schedule, SchedulePeriod, Staff } from "@/types/api";

type ConflictDetailState = {
  schedule: Schedule;
  detail: ConflictDetail;
};

type SummaryViewMode = "month" | "day" | "staff";

export default function ScheduleSummaryPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [staffOptions, setStaffOptions] = useState<Staff[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState<number | null>(null);
  const [selectedDate, setSelectedDate] = useState<string>("");
  const [viewMode, setViewMode] = useState<SummaryViewMode>("month");
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [exportingPdf, setExportingPdf] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [conflictExpand, setConflictExpand] = useState(false);
  const [focusDate, setFocusDate] = useState<string | null>(null);
  const [conflictDetail, setConflictDetail] = useState<ConflictDetailState | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    const loadPeriods = async () => {
      try {
        setMessage(null);
        const [periodData, staffData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active"),
        ]);
        if (!active) {
          return;
        }

        const nextPeriods = periodData ?? [];
        setPeriods(nextPeriods);
        setStaffOptions(staffData ?? []);
        const activePeriod =
          nextPeriods.find((p) => p.status === "PUBLISHED" || p.status === "DRAFT") ?? nextPeriods[0];
        if (activePeriod) {
          setSelectedPeriodId(activePeriod.id);
        }
      } catch (err) {
        if (!active) {
          return;
        }

        setPeriods([]);
        setStaffOptions([]);
        setMessage(getErrorMessage(err, "Không thể tải danh sách kỳ lịch."));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadPeriods();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedPeriodId) return;

    let active = true;
    setLoading(true);
    setMessage(null);

    const loadSummaryData = async () => {
      try {
        const schedulePath = viewMode === "staff" && selectedStaffId
          ? `/schedules/staff/${selectedStaffId}`
          : viewMode === "day" && selectedDate
            ? `/schedules/period/${selectedPeriodId}/date/${selectedDate}`
            : `/schedules/period/${selectedPeriodId}`;

        const [scheduleResult, conflictResult] = await Promise.all([
          api.get<Schedule[]>(schedulePath),
          api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`),
        ]);

        if (!active) {
          return;
        }

        const nextSchedules = viewMode === "staff" && selectedStaffId
          ? (scheduleResult ?? []).filter((schedule) => schedule.periodId === selectedPeriodId)
          : (scheduleResult ?? []);

        setSchedules(nextSchedules);
        setConflictData(conflictResult);
      } catch (err) {
        if (!active) {
          return;
        }

        setSchedules([]);
        setConflictData(null);
        setMessage(getErrorMessage(err, "Không thể tải lịch tổng hợp."));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadSummaryData();

    return () => {
      active = false;
    };
  }, [selectedPeriodId, selectedDate, selectedStaffId, viewMode]);

  const conflictMap = useMemo(() => {
    const entries = (conflictData?.conflicts ?? []).map((detail) => [detail.scheduleId, detail] as const);
    return new Map<number, ConflictDetail>(entries);
  }, [conflictData]);

  const conflictSchedules = useMemo(() => {
    const selectedConflictIds = new Set(schedules.map((schedule) => schedule.id));
    return schedules.filter((schedule) => conflictMap.has(schedule.id) && selectedConflictIds.has(schedule.id));
  }, [schedules, conflictMap]);

  const stats = useMemo(() => {
    const uniqueDays = new Set(schedules.map((schedule) => schedule.workDate.split("T")[0])).size;
    const byType = schedules.reduce((accumulator, schedule) => {
      accumulator[schedule.shiftType.id] = (accumulator[schedule.shiftType.id] ?? 0) + 1;
      return accumulator;
    }, {} as Record<string, number>);
    return { uniqueDays, byType };
  }, [schedules]);

  const selectedPeriod = useMemo(
    () => periods.find((period) => period.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  const handleDayClick = (date: Date) => {
    setFocusDate(date.toLocaleDateString("vi-VN"));
  };

  async function refreshSummary() {
    if (!selectedPeriodId) {
      return;
    }

    try {
      const schedulePath = viewMode === "staff" && selectedStaffId
        ? `/schedules/staff/${selectedStaffId}`
        : viewMode === "day" && selectedDate
          ? `/schedules/period/${selectedPeriodId}/date/${selectedDate}`
          : `/schedules/period/${selectedPeriodId}`;

      const [scheduleResult, conflictResult] = await Promise.all([
        api.get<Schedule[]>(schedulePath),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`),
      ]);
      const nextSchedules = viewMode === "staff" && selectedStaffId
        ? (scheduleResult ?? []).filter((schedule) => schedule.periodId === selectedPeriodId)
        : (scheduleResult ?? []);
      setSchedules(nextSchedules);
      setConflictData(conflictResult);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể làm mới lịch tổng hợp."));
    }
  }

  async function handleExportExcel() {
    if (!selectedPeriodId) {
      setMessage("Chưa chọn kỳ lịch để xuất Excel.");
      return;
    }

    try {
      setExporting(true);
      setMessage(null);
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${selectedPeriodId}`,
        { credentials: "include" },
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `lich-cong-tac-${selectedPeriodId}.xlsx`;
      anchor.click();
      URL.revokeObjectURL(url);
      setMessage("Đã xuất báo cáo Excel của kỳ lịch đang chọn.");
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể xuất Excel lịch công tác."));
    } finally {
      setExporting(false);
    }
  }

  async function handleExportPdf() {
    if (!selectedPeriodId) {
      setMessage("Chưa chọn kỳ lịch để xuất PDF.");
      return;
    }

    try {
      setExportingPdf(true);
      setMessage(null);
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${selectedPeriodId}/pdf`,
        { credentials: "include" },
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `lich-cong-tac-${selectedPeriodId}.pdf`;
      anchor.click();
      URL.revokeObjectURL(url);
      setMessage("Đã xuất báo cáo PDF của kỳ lịch đang chọn.");
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể xuất PDF lịch công tác."));
    } finally {
      setExportingPdf(false);
    }
  }

  async function handlePrint() {
    try {
      setPrinting(true);
      setMessage(null);
      window.print();
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể mở chế độ in lịch."));
    } finally {
      setPrinting(false);
    }
  }

  return (
    <DashboardShell
      activeCode="M06"
      title="Tổng hợp lịch"
      description="Chế độ xem toàn cảnh lịch công tác và cảnh báo xung đột theo thời gian thực"
    >
      {message && (
        <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-on-surface">
          {message}
        </div>
      )}

      <div className="flex items-center justify-end gap-3">
        <button
          type="button"
          onClick={handlePrint}
          disabled={printing || loading}
          className="flex items-center gap-2 rounded-lg border border-outline-variant px-4 py-2 text-label-md text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-50"
        >
          <span className="material-symbols-outlined text-[16px]">print</span>
          {printing ? "Đang mở in..." : "In lịch"}
        </button>
        <button
          type="button"
          onClick={handleExportPdf}
          disabled={exportingPdf || loading || !selectedPeriodId}
          className="flex items-center gap-2 rounded-lg border border-primary px-4 py-2 text-label-md text-primary hover:bg-primary/5 transition-colors disabled:opacity-50"
        >
          <span className="material-symbols-outlined text-[16px]">picture_as_pdf</span>
          {exportingPdf ? "Đang xuất PDF..." : "Xuất PDF"}
        </button>
        <button
          type="button"
          onClick={handleExportExcel}
          disabled={exporting || loading || !selectedPeriodId}
          className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary hover:bg-primary/90 transition-colors shadow-sm disabled:opacity-50"
        >
          <span className="material-symbols-outlined text-[16px]">download</span>
          {exporting ? "Đang xuất..." : "Xuất Excel"}
        </button>
      </div>

      <div className="flex items-center gap-4 flex-wrap">
        <div className="flex items-center gap-2">
          <label className="shrink-0 text-label-md text-on-surface-variant">Kỳ lịch:</label>
          <div className="relative">
            <select
              className="h-9 appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest pl-3 pr-9 text-label-md text-on-surface cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
            >
              {periods.map((period) => (
                <option key={period.id} value={period.id}>
                  {period.periodName}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]">
              expand_more
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <label className="shrink-0 text-label-md text-on-surface-variant">Chế độ xem:</label>
          <div className="flex items-center rounded-lg border border-outline-variant bg-surface-container-lowest p-1">
            {[
              { value: "month", label: "Theo kỳ" },
              { value: "day", label: "Theo ngày" },
              { value: "staff", label: "Theo nhân sự" },
            ].map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setViewMode(option.value as SummaryViewMode)}
                className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
                  viewMode === option.value
                    ? "bg-primary text-on-primary"
                    : "text-on-surface-variant hover:bg-surface-container-low"
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {viewMode === "day" && (
          <div className="flex items-center gap-2">
            <label className="shrink-0 text-label-md text-on-surface-variant">Ngày:</label>
            <input
              type="date"
              className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
            />
          </div>
        )}

        {viewMode === "staff" && (
          <div className="flex items-center gap-2">
            <label className="shrink-0 text-label-md text-on-surface-variant">Nhân sự:</label>
            <div className="relative">
              <select
                className="h-9 min-w-[220px] appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest pl-3 pr-9 text-label-md text-on-surface cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                value={selectedStaffId ?? ""}
                onChange={(e) => setSelectedStaffId(e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">Chọn nhân sự</option>
                {staffOptions.map((member) => (
                  <option key={member.id} value={member.id}>{member.fullName}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]">
                expand_more
              </span>
            </div>
          </div>
        )}

        <div className="ml-auto flex items-center gap-2">
          <div className="rounded-full border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-label-md font-medium text-on-surface">
            <span className="font-semibold text-primary">{schedules.length}</span> lịch
          </div>
          <div className="rounded-full border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-label-md font-medium text-on-surface">
            <span className="font-semibold text-primary">{stats.uniqueDays}</span> ngày có lịch
          </div>
          <div className="rounded-full border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-label-md font-medium text-on-surface">
            <span className="font-semibold text-primary">{selectedPeriod?.status ?? "—"}</span>
          </div>
          {conflictSchedules.length > 0 && (
            <div className="rounded-full border border-red-200 bg-red-50 px-3 py-1.5 text-label-md font-medium text-error">
              <span className="font-semibold">{conflictSchedules.length}</span> xung đột
            </div>
          )}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : (
        <div className={`grid gap-4 transition-all duration-300 ${sidebarOpen ? "xl:grid-cols-[1fr_320px]" : "grid-cols-1"}`}>
          <div className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest" style={{ minHeight: "calc(100vh - 220px)" }}>
            <ScheduleCalendarWidget
              schedules={schedules}
              onDayClick={handleDayClick}
              onRefresh={refreshSummary}
            />
          </div>

          {!sidebarOpen ? (
            <button
              type="button"
              onClick={() => setSidebarOpen(true)}
              className="fixed right-4 top-20 z-40 rounded-full border border-outline-variant bg-surface-container-lowest p-2.5 shadow-lg hover:bg-surface-container-high transition-colors"
              aria-label="Mở sidebar"
            >
              <span className="material-symbols-outlined text-[20px] text-on-surface-variant">chevron_left</span>
            </button>
          ) : (
            <aside className="flex flex-col gap-4">
              <div className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest">
                <div className="flex items-center justify-between border-b border-outline-variant bg-red-50/50 px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[20px] text-error">warning</span>
                    <h3 className="text-label-md font-semibold text-error">Xung đột ({conflictSchedules.length})</h3>
                  </div>
                  <div className="flex items-center gap-1">
                    {conflictSchedules.length > 3 && (
                      <button
                        type="button"
                        onClick={() => setConflictExpand((value) => !value)}
                        className="rounded-md px-2 py-1 text-[11px] font-medium text-error hover:bg-red-100 transition-colors"
                      >
                        {conflictExpand ? "Thu gọn" : `Xem tất cả (${conflictSchedules.length})`}
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => setSidebarOpen(false)}
                      className="rounded-md p-1 hover:bg-surface-container-high transition-colors"
                      aria-label="Ẩn sidebar"
                    >
                      <span className="material-symbols-outlined text-[16px] text-on-surface-variant">close</span>
                    </button>
                  </div>
                </div>

                <div className="divide-y divide-outline-variant/50">
                  {(conflictExpand ? conflictSchedules : conflictSchedules.slice(0, 3)).map((schedule) => {
                    const detail = conflictMap.get(schedule.id);
                    if (!detail) {
                      return null;
                    }

                    return (
                      <button
                        key={schedule.id}
                        type="button"
                        onClick={() => setConflictDetail({ schedule, detail })}
                        className="w-full px-4 py-3 text-left hover:bg-surface-container-low transition-colors"
                      >
                        <div className="flex items-start gap-2">
                          <span className="material-symbols-outlined mt-0.5 shrink-0 text-[16px] text-error">warning</span>
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-label-sm font-semibold text-on-surface">{detail.staffName}</p>
                            <p className="mt-0.5 text-[11px] text-on-surface-variant">
                              {detail.shiftTypeName} · {new Date(detail.workDate).toLocaleDateString("vi-VN")}
                            </p>
                            <p className="mt-1 line-clamp-2 text-[11px] text-error">
                              {detail.conflictReasons.join(" • ")}
                            </p>
                          </div>
                          <span className="shrink-0 text-[11px] font-medium text-error">Xem</span>
                        </div>
                      </button>
                    );
                  })}

                  {conflictSchedules.length === 0 && (
                    <div className="flex items-center gap-2 px-4 py-6 text-label-md text-emerald-600">
                      <span className="material-symbols-outlined text-[20px]">check_circle</span>
                      Không có xung đột
                    </div>
                  )}
                </div>
              </div>

              <div className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest">
                <div className="border-b border-outline-variant px-4 py-3">
                  <h3 className="text-label-md font-semibold text-on-surface">Tổng quan</h3>
                </div>
                <div className="space-y-3 p-4">
                  {[
                    { label: "Tổng lịch", value: schedules.length, color: "text-on-surface" },
                    { label: "Ngày có lịch", value: stats.uniqueDays, color: "text-on-surface" },
                    { label: "Trực 24/24", value: stats.byType.L01 ?? 0, color: "text-blue-600" },
                    { label: "Thông tầm", value: stats.byType.L02 ?? 0, color: "text-emerald-600" },
                    { label: "Dịch vụ", value: stats.byType.L03 ?? 0, color: "text-amber-600" },
                    { label: "Chuyên gia", value: stats.byType.L04 ?? 0, color: "text-violet-600" },
                    { label: "Ngày đang xem", value: focusDate ?? "Chưa chọn", color: "text-primary" },
                  ].map((row) => (
                    <div key={row.label} className="flex items-center justify-between gap-3">
                      <span className="text-label-sm text-on-surface-variant">{row.label}</span>
                      <span className={`text-label-sm font-semibold ${row.color}`}>{row.value}</span>
                    </div>
                  ))}
                </div>
              </div>
            </aside>
          )}
        </div>
      )}

      <Modal
        open={!!conflictDetail}
        onClose={() => setConflictDetail(null)}
        title="Chi tiết xung đột"
        description={
          conflictDetail
            ? `${conflictDetail.detail.staffName} — ${new Date(conflictDetail.detail.workDate).toLocaleDateString("vi-VN")}`
            : ""
        }
        size="sm"
      >
        {conflictDetail ? (
          <div className="space-y-4">
            <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4">
              <span className="material-symbols-outlined mt-0.5 text-[20px] text-error">warning</span>
              <div>
                <p className="text-label-md font-semibold text-error">Xung đột lịch cần xử lý</p>
                <p className="mt-1 text-label-sm leading-relaxed text-on-surface-variant">
                  {conflictDetail.detail.staffName} đang có xung đột với lịch {conflictDetail.detail.shiftTypeName} vào ngày {new Date(conflictDetail.detail.workDate).toLocaleDateString("vi-VN")}. Xem danh sách lý do bên dưới để xử lý đúng nghiệp vụ.
                </p>
              </div>
            </div>

            <div className="space-y-2 rounded-lg bg-surface-container-low p-4">
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Nhân sự</span>
                <span className="text-right text-label-sm font-medium text-on-surface">{conflictDetail.detail.staffName}</span>
              </div>
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Loại lịch</span>
                <span className="text-right text-label-sm font-medium text-on-surface">{conflictDetail.detail.shiftTypeName}</span>
              </div>
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Ngày làm việc</span>
                <span className="text-right text-label-sm font-medium text-on-surface">{new Date(conflictDetail.detail.workDate).toLocaleDateString("vi-VN")}</span>
              </div>
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Mã lịch</span>
                <span className="text-right text-label-sm font-medium text-on-surface">#{conflictDetail.schedule.id}</span>
              </div>
            </div>

            <div className="space-y-2 rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
              <p className="text-label-sm font-semibold text-on-surface">Lý do xung đột</p>
              <ul className="space-y-2 text-label-sm text-on-surface-variant">
                {conflictDetail.detail.conflictReasons.map((reason) => (
                  <li key={reason} className="flex items-start gap-2">
                    <span className="material-symbols-outlined mt-0.5 text-[16px] text-error">error</span>
                    <span>{reason}</span>
                  </li>
                ))}
              </ul>
            </div>

            <ModalFooter>
              <button
                type="button"
                onClick={() => setConflictDetail(null)}
                className="rounded-lg border border-outline-variant px-4 py-2 text-label-md text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Đóng
              </button>
            </ModalFooter>
          </div>
        ) : null}
      </Modal>
    </DashboardShell>
  );
}
