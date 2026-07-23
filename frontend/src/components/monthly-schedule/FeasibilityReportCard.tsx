"use client";

import { memo, useCallback, useEffect, useState } from "react";
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

type AvailabilitySummary = {
  shiftTypeId: string;
  totalActiveStaff: number;
  eligibleStaff: number;
  averageDailyEligible: number;
  minDailyEligible: number;
  maxDailyEligible: number;
  utilizationRate: number;
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

const SHIFT_TYPE_COLORS: Record<string, { bg: string; border: string; text: string }> = {
  L01: { bg: "bg-red-50", border: "border-red-500", text: "text-red-800" },
  L02: { bg: "bg-blue-50", border: "border-blue-500", text: "text-blue-800" },
  L03: { bg: "bg-green-50", border: "border-green-500", text: "text-green-800" },
  L04: { bg: "bg-purple-50", border: "border-purple-500", text: "text-purple-800" },
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

  const checkFeasibility = useCallback(async () => {
    if (!periodId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await api.checkFeasibility(periodId);
      setReport(result);
    } catch (err) {
      setError("Không thể kiểm tra tính khả thi");
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [periodId]);

  useEffect(() => {
    if (periodId) {
      checkFeasibility();
    }
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
          <span className="material-symbols-outlined animate-spin text-primary">sync</span>
          <span className="text-on-surface-variant text-sm">Đang kiểm tra tính khả thi...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-error-container bg-error-container p-4">
        <p className="text-on-error-container text-sm">{error}</p>
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
          <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${
            report.feasible ? "bg-secondary-container" : "bg-error-container"
          }`}>
            <span className={`material-symbols-outlined text-[20px] ${
              report.feasible ? "text-secondary" : "text-error"
            }`}>
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
        <div className="px-4 py-3 bg-tertiary-fixed/50 border-b border-outline-variant">
          {report.warnings.map((warning, i) => (
            <div key={i} className="flex items-start gap-2 text-on-tertiary-fixed-variant text-sm">
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
            const colors = SHIFT_TYPE_COLORS[typeId] || { bg: "bg-gray-50", border: "border-gray-500", text: "text-gray-800" };
            const avgEligible = Math.round(summary.averageDailyEligible);
            const minEligible = summary.minDailyEligible;
            const hasBufferWarning = report.warnings.some(w =>
              w.includes("[CANH-BAO]") && w.includes(SHIFT_TYPE_LABELS[typeId] || typeId)
            );

            return (
              <div
                key={typeId}
                className={`rounded-lg border ${colors.border} ${colors.bg} p-3`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className={`text-label-md font-semibold ${colors.text}`}>
                    {SHIFT_TYPE_LABELS[typeId] || typeId}
                  </span>
                  <span className={`text-label-xs ${colors.text} opacity-75`}>{typeId}</span>
                </div>
                <div className="space-y-1">
                  <div className="flex justify-between text-label-sm">
                    <span className={`${colors.text} opacity-75`}>Trung bình eligible:</span>
                    <span className={`${colors.text} font-bold`}>{avgEligible}</span>
                  </div>
                  <div className="flex justify-between text-label-sm">
                    <span className={`${colors.text} opacity-75`}>Tối thiểu:</span>
                    <span className={`${colors.text} font-bold`}>{minEligible}</span>
                  </div>
                  {/* Buffer risk indicator */}
                  {hasBufferWarning ? (
                    <div className="flex items-center gap-1 mt-1.5">
                      <span className="material-symbols-outlined text-error text-[14px]" aria-hidden="true">warning</span>
                      <span className="text-[11px] text-error font-medium">Không có dự phòng</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-1 mt-1.5">
                      <span className="material-symbols-outlined text-secondary text-[14px]" aria-hidden="true" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
                      <span className="text-[11px] text-secondary font-medium">Có buffer</span>
                    </div>
                  )}
                </div>
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
            <h4 className="text-label-md text-error font-semibold">
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
                    className="flex items-start gap-3 p-3 rounded-lg bg-error-container/50 border border-error/20"
                  >
                    <span className="material-symbols-outlined text-error text-[16px] shrink-0 mt-0.5">
                      warning
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-label-md text-on-surface font-medium">{dateStr}</p>
                      <div className="mt-1 space-y-1">
                        {Object.values(day.shiftTypes)
                          .filter((st) => st.isUnderstaffed)
                          .map((st) => {
                            const colors = SHIFT_TYPE_COLORS[st.shiftTypeId] || { bg: "bg-gray-50", border: "border-gray-500", text: "text-gray-800" };
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
          <h4 className="text-label-md text-on-surface-variant mb-2">Gợi ý</h4>
          <ul className="space-y-1">
            {report.recommendations.slice(0, 3).map((rec, i) => {
              const cleaned = rec.replace(/^(\[[^\]]+\]\s*|[📋💡⚠️]\s*)/u, "");
              const isWarning = rec.startsWith("[CANH-BAO]") || rec.includes("thieu");
              return (
                <li key={i} className="flex items-start gap-2 text-label-sm text-on-surface">
                  <span
                    className={`material-symbols-outlined text-[14px] shrink-0 mt-0.5 ${
                      isWarning ? "text-error" : "text-primary"
                    }`}
                    style={{ fontVariationSettings: "'FILL' 1, 'wght' 500" }}
                  >
                    {isWarning ? "warning" : "lightbulb"}
                  </span>
                  <span>{cleaned}</span>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {/* Actions */}
      <div className="px-4 pb-4 flex gap-2">
        <Button variant="ghost" size="sm" onClick={checkFeasibility}>
          <span className="material-symbols-outlined text-[16px]">refresh</span>
          Kiểm tra lại
        </Button>
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
