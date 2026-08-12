"use client";

import { memo, useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

type ShiftTypeAnalysis = {
  shiftTypeId: string;
  required: number;
  eligibleStaff: number;
  activeStaff: number;
  onLeave: number;
  onCompensation: number;
  coverageRate: number;
  isUnderstaffed: boolean;
  issue: string | null;
};

type DayAnalysis = {
  date: string;
  shiftTypes: Record<string, ShiftTypeAnalysis>;
};

type BufferRisk = "NONE" | "LOW" | "MEDIUM" | "HIGH";

type StaffBackup = {
  staffId: number;
  staffName: string;
  specialtyName: string;
  daysAvailable: number;
};

type AvailabilitySummary = {
  shiftTypeId: string;
  totalActiveStaff: number;
  eligibleStaff: number;
  averageDailyEligible: number;
  minDailyEligible: number;
  maxDailyEligible: number;
  utilizationRate: number;
  bufferMin: number;
  bufferRisk: BufferRisk;
  noBufferDays: number;
  totalDays: number;
  backups: StaffBackup[];
};

type FeasibilityReport = {
  feasible: boolean;
  totalDays: number;
  feasibleDays: number;
  understaffedDays: number;
  coverageRate: number;
  dailyAnalysis: DayAnalysis[];
  availabilityByShiftType: Record<string, AvailabilitySummary>;
  warnings: string[];
  recommendations: string[];
};

const SHIFT_TYPE_LABELS: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "Phòng khám dịch vụ",
  L04: "Phòng khám chuyên gia",
};

const SHIFT_TYPE_COLORS: Record<string, { border: string; text: string }> = {
  L01: { border: "border-red-300", text: "text-red-800" },
  L02: { border: "border-emerald-300", text: "text-emerald-800" },
  L03: { border: "border-amber-300", text: "text-amber-800" },
  L04: { border: "border-purple-300", text: "text-purple-800" },
};

interface FeasibilityReportCardProps {
  periodId: number | null;
  onRunScheduling?: () => void;
}

export const FeasibilityReportCard = memo(function FeasibilityReportCard({
  periodId,
  onRunScheduling,
}: FeasibilityReportCardProps) {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState<FeasibilityReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const checkFeasibility = useCallback(async () => {
    if (!periodId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await api.checkFeasibility(periodId);
      setReport(result);
      setLastUpdated(new Date());
    } catch (err) {
      setError("Không thể kiểm tra tính khả thi");
    } finally {
      setLoading(false);
    }
  }, [periodId]);

  const loadingRef = useRef(false);
  useEffect(() => {
    loadingRef.current = loading;
  }, [loading]);

  useEffect(() => {
    if (!periodId) return;
    void checkFeasibility();
    const onFocus = () => {
      if (!loadingRef.current) void checkFeasibility();
    };
    window.addEventListener("focus", onFocus);
    const id = window.setInterval(() => {
      if (!document.hidden && !loadingRef.current) void checkFeasibility();
    }, 60_000);
    return () => {
      window.removeEventListener("focus", onFocus);
      window.clearInterval(id);
    };
  }, [periodId, checkFeasibility]);

  if (!periodId) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="text-on-surface-variant text-sm">Chọn kỳ lịch để kiểm tra</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined animate-spin text-blue-800">sync</span>
          <span className="text-on-surface-variant text-sm">Đang kiểm tra tính khả thi...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-300 p-4">
        <p className="text-red-800 text-sm">{error}</p>
        <Button variant="ghost" size="sm" onClick={checkFeasibility} className="mt-2">
          Thử lại
        </Button>
      </div>
    );
  }

  if (!report) return null;

  const coverageRate = Math.round(report.coverageRate);
  const coverageTone = coverageRate >= 80 ? "success" : coverageRate >= 50 ? "warning" : "error";

  const understaffedDays = report.dailyAnalysis.filter((day) =>
    Object.values(day.shiftTypes).some((st) => st.isUnderstaffed)
  );

  return (
    <div className="rounded-lg border border-outline-variant bg-surface-container-lowest overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-outline-variant">
        <div className="flex items-center gap-3">
          <div className={`flex h-10 w-10 items-center justify-center rounded-lg border ${
            report.feasible ? "border-emerald-300 text-emerald-800" : "border-red-300 text-red-800"
          }`}>
            <span className="material-symbols-outlined text-[20px]">
              {report.feasible ? "check_circle" : "warning"}
            </span>
          </div>
          <div>
            <h3 className="font-title-lg text-title-lg text-on-surface">
              Kiểm tra tính khả thi
            </h3>
            <p className="text-label-sm text-on-surface-variant">
              {report.totalDays} ngày • {report.feasibleDays} khả thi • {report.understaffedDays} thiếu nhân sự
            </p>
          </div>
        </div>
        <Badge tone={coverageTone} size="md">
          {coverageRate}% khả thi
        </Badge>
      </div>

      {/* Warnings */}
      {report.warnings.length > 0 && (
        <div className="px-4 py-3 border-b border-amber-300">
          {report.warnings.map((warning, i) => (
            <div key={i} className="flex items-start gap-2 text-amber-800 text-sm">
              <span className="material-symbols-outlined text-[16px] shrink-0 mt-0.5">warning</span>
              <span>{warning}</span>
            </div>
          ))}
        </div>
      )}

      {/* Summary by Shift Type */}
      <div className="p-4 border-b border-outline-variant">
        <h4 className="text-label-md text-on-surface-variant mb-3">Tình trạng theo loại lịch</h4>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {Object.entries(report.availabilityByShiftType).map(([typeId, summary]) => {
            const colors = SHIFT_TYPE_COLORS[typeId] || { border: "border-outline-variant", text: "text-on-surface" };
            const avgEligible = Math.round(summary.averageDailyEligible);
            const typicalRequired = avgEligible - summary.bufferMin;
            const buffer = summary.bufferMin;
            const hasBuffer = buffer > 0;

            const riskConfig: Record<BufferRisk, { label: string; text: string; icon: string; border: string }> = {
              NONE: { label: "An toàn", text: "text-emerald-800", border: "border-emerald-300", icon: "check_circle" },
              LOW: { label: "Thấp", text: "text-blue-800", border: "border-blue-300", icon: "info" },
              MEDIUM: { label: "Trung bình", text: "text-amber-800", border: "border-amber-300", icon: "warning" },
              HIGH: { label: "Nguy hiểm", text: "text-red-800", border: "border-red-300", icon: "error" },
            };
            const risk = (summary.bufferRisk as BufferRisk) || "NONE";
            const riskCfg = riskConfig[risk];

            const pct = summary.totalDays > 0
              ? Math.round((summary.totalDays - summary.noBufferDays) / summary.totalDays * 100)
              : 100;

            return (
              <div
                key={typeId}
                className={`rounded-lg border ${colors.border} p-3 flex flex-col gap-2`}
              >
                {/* Header */}
                <div className="flex items-center justify-between">
                  <span className={`text-label-md font-semibold ${colors.text}`}>
                    {SHIFT_TYPE_LABELS[typeId] || typeId}
                  </span>
                  <span className={`text-label-xs ${colors.text} opacity-75`}>{typeId}</span>
                </div>

                {/* Required vs Eligible */}
                <div className="flex items-center justify-between">
                  <span className="text-label-sm text-on-surface-variant">Eligible/Required:</span>
                  <span className={`text-label-sm font-bold ${colors.text}`}>
                    {avgEligible}/{typicalRequired > 0 ? typicalRequired : "?"}
                  </span>
                </div>

                {/* Buffer progress bar */}
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <span className="text-label-xs text-on-surface-variant">Buffer dự phòng</span>
                    <span className={`text-label-xs font-semibold ${
                      buffer >= 1 ? "text-emerald-800" : buffer === 0 ? "text-red-800" : "text-amber-800"
                    }`}>
                      {buffer > 0 ? `+${buffer}` : buffer === 0 ? "0" : buffer}
                    </span>
                  </div>
                  <div className="w-full rounded-full h-2 bg-surface-container-high">
                    <div
                      className={`h-2 rounded-full transition-all ${
                        buffer > 0 ? "bg-emerald-500" : buffer === 0 ? "bg-amber-500" : "bg-blue-500"
                      }`}
                      style={{ width: `${Math.min(100, pct)}%` }}
                      title={`${summary.noBufferDays}/${summary.totalDays} ngày không có buffer`}
                    />
                  </div>
                  {summary.noBufferDays > 0 && (
                    <p className="text-[10px] mt-0.5 text-on-surface-variant">
                      {summary.noBufferDays}/{summary.totalDays} ngày không có dự phòng
                    </p>
                  )}
                </div>

                {/* Risk level badge - chỉ viền */}
                <div className={`flex items-center justify-between px-2 py-1 rounded-md border ${riskCfg.border}`}>
                  <div className="flex items-center gap-1.5">
                    <span className={`material-symbols-outlined text-[14px] ${riskCfg.text}`} style={{ fontVariationSettings: "'FILL' 1" }}>{riskCfg.icon}</span>
                    <span className={`text-[11px] font-semibold ${riskCfg.text}`}>Rủi ro: {riskCfg.label}</span>
                  </div>
                </div>

                {/* Backup staff section */}
                {summary.backups && summary.backups.length > 0 && (
                  <details className="group">
                    <summary className={`cursor-pointer text-[11px] text-on-surface-variant hover:text-on-surface list-none flex items-center gap-1`}>
                      <span className="material-symbols-outlined text-[12px] group-open:rotate-90 transition-transform">chevron_right</span>
                      Xem {summary.backups.length} nhân sự dự phòng
                    </summary>
                    <div className="mt-1.5 space-y-1 max-h-60 overflow-y-auto pr-1">
                      {summary.backups.map((b) => (
                        <div key={b.staffId} className="flex items-center justify-between bg-surface-container-low rounded px-2 py-1">
                          <div className="min-w-0">
                            <p className={`text-[11px] font-medium truncate text-on-surface`}>{b.staffName}</p>
                            <p className={`text-[10px] text-on-surface-variant`}>{b.specialtyName}</p>
                          </div>
                          <span className={`text-[10px] font-semibold shrink-0 ml-1 ${b.daysAvailable > 0 ? "text-emerald-800" : "text-on-surface-variant"}`}>
                            {b.daysAvailable}d
                          </span>
                        </div>
                      ))}
                    </div>
                  </details>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Understaffed Days */}
      {understaffedDays.length > 0 && (
        <div className="p-4">
          <button
            onClick={() => setExpanded(!expanded)}
            className="flex items-center justify-between w-full text-left"
          >
            <h4 className="text-label-md text-red-800 font-semibold">
              Các ngày thiếu nhân sự ({understaffedDays.length})
            </h4>
            <span className="material-symbols-outlined text-on-surface-variant text-[20px]">
              {expanded ? "expand_less" : "expand_more"}
            </span>
          </button>

          {expanded && (
            <div className="mt-3 space-y-2 max-h-60 overflow-y-auto">
              {understaffedDays.map((day) => {
                const date = new Date(day.date);
                const dateStr = date.toLocaleDateString("vi-VN", {
                  weekday: "short",
                  day: "numeric",
                  month: "numeric",
                });

                return (
                  <div
                    key={day.date}
                    className="flex items-start gap-3 p-3 rounded-lg border border-red-300"
                  >
                    <span className="material-symbols-outlined text-red-800 text-[16px] shrink-0 mt-0.5">
                      warning
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-label-md text-on-surface font-medium">{dateStr}</p>
                      <div className="mt-1 space-y-1">
                        {Object.values(day.shiftTypes)
                          .filter((st) => st.isUnderstaffed)
                          .map((st) => {
                            const colors = SHIFT_TYPE_COLORS[st.shiftTypeId] || { border: "border-outline-variant", text: "text-on-surface" };
                            return (
                              <div
                                key={st.shiftTypeId}
                                className={`flex items-center justify-between text-label-sm ${colors.text}`}
                              >
                                <span>{SHIFT_TYPE_LABELS[st.shiftTypeId] || st.shiftTypeId}</span>
                                <span className="font-semibold">
                                  {st.eligibleStaff}/{st.required}
                                </span>
                              </div>
                            );
                          })}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Recommendations */}
      {report.recommendations.length > 0 && (
        <div className="px-4 pb-4">
          <h4 className="text-label-md text-on-surface-variant mb-2">Gợi ý hành động</h4>
          <div className="space-y-2">
            {report.recommendations.map((rec, i) => {
              const isWarning = rec.includes("CANH-BAO") || rec.includes("thieu") || rec.includes("không");
              const isAction = rec.includes("GOI-Y") || rec.includes("bật") || rec.includes("thêm") || rec.includes("giảm");
              const labelMatch = rec.match(/\[([^\]]+)\]/);
              const label = labelMatch ? labelMatch[1] : null;
              const content = rec.replace(/\[[^\]]+\]\s*/g, "");

              const cardBorder = isWarning
                ? "border-red-300"
                : isAction
                ? "border-blue-300"
                : "border-emerald-300";
              const iconColor = isWarning
                ? "text-red-800"
                : isAction
                ? "text-blue-800"
                : "text-emerald-800";
              const iconName = isWarning ? "warning" : isAction ? "bolt" : "info";

              return (
                <div
                  key={i}
                  className={`flex items-start gap-3 rounded-lg p-3 text-label-sm border ${cardBorder}`}
                >
                  <span
                    className={`material-symbols-outlined text-[16px] shrink-0 mt-0.5 ${iconColor}`}
                    style={{ fontVariationSettings: "'FILL' 1" }}
                  >
                    {iconName}
                  </span>
                  <div className="flex-1 min-w-0">
                    {label && (
                      <span className={`inline-block text-[10px] font-bold uppercase tracking-wide mb-0.5 ${iconColor}`}>
                        {label}
                      </span>
                    )}
                    <p className="text-on-surface">{content}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="px-4 pb-4 flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={checkFeasibility}>
          <span className="material-symbols-outlined text-[16px]">refresh</span>
          Kiểm tra lại
        </Button>
        {lastUpdated && (
          <span className="text-label-xs text-on-surface-variant opacity-75" title={lastUpdated.toISOString()}>
            Cập nhật lúc {lastUpdated.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
          </span>
        )}
        {!report.feasible && onRunScheduling && (
          <Button variant="primary" size="sm" onClick={onRunScheduling}>
            <span className="material-symbols-outlined text-[16px]">play_arrow</span>
            Vẫn chạy
          </Button>
        )}
      </div>
    </div>
  );
});
