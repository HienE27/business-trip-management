"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { ExportControls } from "@/components/reports/ExportControls";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import type { SchedulePeriod, ConflictCheckResponse, ConflictDetail } from "@/types/api";

export default function ReportsConflictsPage() {
  const { success: toastSuccess, error: toastError } = useToast();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<SchedulePeriod | null>(null);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const fetchPeriods = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<SchedulePeriod[]>("/periods");
      setPeriods(data ?? []);
      const active = data?.find((p) => p.status === "PUBLISHED" || p.status === "DRAFT");
      if (active) setSelectedPeriod(active);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải danh sách kỳ lịch."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchPeriods();
  }, [fetchPeriods]);

  const checkConflicts = useCallback(async (periodId: number) => {
    try {
      setChecking(true);
      setMessage(null);
      const data = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
      setConflictData(data);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi kiểm tra xung đột."));
      setConflictData(null);
    } finally {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    if (selectedPeriod) void checkConflicts(selectedPeriod.id);
  }, [selectedPeriod, checkConflicts]);

  useAutoDismiss(message, () => setMessage(null));

  const conflictsByType = useMemo(() => {
    if (!conflictData) return {};
    const grouped: Record<string, ConflictDetail[]> = {};
    for (const c of conflictData.conflicts) {
      const key = c.conflictReasons.join(" + ") || "Không xác định";
      if (!grouped[key]) grouped[key] = [];
      grouped[key].push(c);
    }
    return grouped;
  }, [conflictData]);

  return (
    <DashboardShell
      activeSection="reports"
      title="Báo cáo xung đột"
      description="Phân tích các xung đột lịch trực, nguyên nhân và mức độ ảnh hưởng."
    >
      {/* Period selector */}
      <section className="flex items-center justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="material-symbols-outlined text-[22px] text-primary">warning</span>
          <div>
            <h2 className="text-[16px] font-semibold text-on-surface">Kiểm tra xung đột</h2>
            <p className="text-[12px] text-on-surface-variant">Chọn kỳ lịch để phân tích xung đột.</p>
          </div>
        </div>
        <div className="relative min-w-[280px]">
          <label htmlFor="report-conflicts-period" className="sr-only">Chọn kỳ lịch</label>
          <select
            id="report-conflicts-period"
            className="w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-[14px] text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer pr-10"
            value={selectedPeriod?.id ?? ""}
            onChange={(e) => {
              const p = periods.find((x) => x.id === Number(e.target.value));
              if (p) setSelectedPeriod(p);
            }}
          >
            <option value="">Chọn kỳ lịch</option>
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.periodName} ({p.status === "PUBLISHED" ? "Đã công bố" : p.status === "DRAFT" ? "Nháp" : "Đã lưu trữ"})
              </option>
            ))}
          </select>
          <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
        </div>
      </section>

      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {!selectedPeriod && !loading ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
          <span className="material-symbols-outlined text-5xl text-outline">warning</span>
          <p className="text-on-surface-variant">Chọn một kỳ lịch để kiểm tra xung đột.</p>
        </div>
      ) : loading || checking ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-outline-variant bg-surface-container-lowest py-20 gap-4">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-on-surface-variant">{loading ? "Đang tải kỳ lịch..." : "Đang kiểm tra xung đột..."}</p>
        </div>
      ) : conflictData ? (
        <div className="space-y-6">
          {/* Summary */}
          <section className="flex items-center justify-between gap-4 flex-wrap">
          <section className="grid gap-4 md:grid-cols-3 flex-1">
            <article className={`flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm ${conflictData.hasConflicts ? "border-l-4 border-l-error" : "border-l-4 border-l-secondary"}`}>
              <div className="flex justify-between items-start">
                <p className="text-label-sm text-on-surface-variant">Tổng xung đột</p>
                <span className={`material-symbols-outlined p-1.5 rounded-md ${conflictData.hasConflicts ? "bg-error-container text-error" : "bg-secondary-container text-secondary"} text-[18px]`}>
                  {conflictData.hasConflicts ? "warning" : "check_circle"}
                </span>
              </div>
              <p className="mt-3 text-display-lg font-bold text-on-surface">{conflictData.totalConflicts}</p>
              <p className="mt-1 text-[12px] text-on-surface-variant">
                {conflictData.hasConflicts ? "Cần xử lý trước khi publish" : "Không phát hiện xung đột"}
              </p>
            </article>
            <article className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm border-l-4 border-l-primary">
              <div className="flex justify-between items-start">
                <p className="text-label-sm text-on-surface-variant">Kỳ lịch</p>
                <span className="material-symbols-outlined p-1.5 rounded-md bg-primary-fixed text-primary text-[18px]">calendar_month</span>
              </div>
              <p className="mt-3 text-[20px] font-bold text-on-surface">{selectedPeriod?.periodName}</p>
              <p className="mt-1 text-[12px] text-on-surface-variant">{selectedPeriod?.status}</p>
            </article>
            <article className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm border-l-4 border-l-tertiary">
              <div className="flex justify-between items-start">
                <p className="text-label-sm text-on-surface-variant">Nhân sự bị ảnh hưởng</p>
                <span className="material-symbols-outlined p-1.5 rounded-md bg-tertiary-fixed text-tertiary text-[18px]">groups</span>
              </div>
              <p className="mt-3 text-display-lg font-bold text-on-surface">
                {new Set(conflictData.conflicts.map((c) => c.staffName)).size}
              </p>
              <p className="mt-1 text-[12px] text-on-surface-variant">Người có xung đột lịch</p>
            </article>
          </section>
          {selectedPeriod && (
            <ExportControls
              periodId={selectedPeriod.id}
              onSuccess={(m) => toastSuccess(m)}
              onError={(m) => {
                setMessage(m);
                toastError(m);
              }}
            />
          )}
          </section>

          {/* No conflicts */}
          {!conflictData.hasConflicts && (
            <section className="rounded-xl border border-secondary/30 bg-secondary-container/10 p-10 shadow-sm text-center">
              <span className="material-symbols-outlined text-5xl text-secondary">check_circle</span>
              <h3 className="mt-4 text-[18px] font-semibold text-secondary">Không phát hiện xung đột</h3>
              <p className="mt-2 text-on-surface-variant max-w-md mx-auto">
                Kỳ lịch hiện tại không có xung đột. Có thể publish hoặc tiếp tục chỉnh sửa an toàn.
              </p>
            </section>
          )}

          {/* Conflicts by type */}
          {conflictData.hasConflicts && (
            <>
              <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
                <h3 className="text-[16px] font-semibold text-on-surface mb-4">Xung đột theo nguyên nhân</h3>
                <div className="space-y-4">
                  {Object.entries(conflictsByType).map(([type, items]) => (
                    <div key={type} className="rounded-lg border border-l-4 border-l-error bg-surface p-4">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="text-[14px] font-semibold text-on-surface">{type}</h4>
                        <span className="rounded-full bg-error-container px-3 py-0.5 text-[12px] font-bold text-error">
                          {items.length} lịch
                        </span>
                      </div>
                      <div className="space-y-2">
                        {items.map((c) => (
                          <div key={c.scheduleId} className="flex items-center justify-between rounded-lg bg-surface-container-lowest px-3 py-2">
                            <div>
                              <p className="text-[13px] font-medium text-on-surface">{c.staffName}</p>
                              <p className="text-[12px] text-on-surface-variant">
                                {new Date(c.workDate).toLocaleDateString("vi-VN")} — {c.shiftTypeName}
                              </p>
                            </div>
                            <div className="text-right">
                              <span className="inline-flex items-center gap-1 rounded-full bg-error-container px-2.5 py-0.5 text-[11px] font-semibold text-error">
                                <span className="h-1.5 w-1.5 rounded-full bg-error" />
                                Có xung đột
                              </span>
                              <p className="mt-1 text-[11px] text-outline">
                                #{c.scheduleId}
                              </p>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </section>

              {/* Recommendation */}
              <section className="rounded-xl border border-tertiary/30 bg-tertiary-fixed/10 p-5">
                <h3 className="text-[16px] font-semibold text-on-surface mb-3 flex items-center gap-2">
                  <span className="material-symbols-outlined text-[20px] text-tertiary">tips_and_updates</span>
                  Khuyến nghị
                </h3>
                <ul className="space-y-2">
                  <li className="flex items-start gap-2 text-[13px] text-on-surface">
                    <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">arrow_forward</span>
                    Xử lý tất cả xung đột trước khi publish kỳ lịch.
                  </li>
                  <li className="flex items-start gap-2 text-[13px] text-on-surface">
                    <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">arrow_forward</span>
                    Kiểm tra ngày nghỉ bù trùng với lịch thông tầm hoặc dịch vụ.
                  </li>
                  <li className="flex items-start gap-2 text-[13px] text-on-surface">
                    <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">arrow_forward</span>
                    Cân đối tải trọng nhân sự nếu cùng người xuất hiện nhiều xung đột.
                  </li>
                </ul>
              </section>
            </>
          )}
        </div>
      ) : null}
    </DashboardShell>
  );
}
