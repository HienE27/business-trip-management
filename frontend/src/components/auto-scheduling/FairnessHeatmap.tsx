"use client";

import { memo } from "react";
import type { QualityReport, FairnessDetail } from "@/types/api";

type FairnessHeatmapProps = {
  qualityReport: QualityReport;
  activeStaff: Array<{ id: number; fullName: string; specialtyName?: string }>;
  /** Ngưỡng CV để hiển thị cảnh báo (default 0.20) */
  warningThreshold?: number;
};

const TYPE_LABELS: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK Dịch vụ",
  L04: "PK Chuyên gia",
};

const TYPE_COLORS: Record<string, string> = {
  L01: "bg-red-50 border-red-200",
  L02: "bg-blue-50 border-blue-200",
  L03: "bg-green-50 border-green-200",
  L04: "bg-purple-50 border-purple-200",
};

const TYPE_ACCENT: Record<string, string> = {
  L01: "border-l-4 border-l-red-400",
  L02: "border-l-4 border-l-blue-400",
  L03: "border-l-4 border-l-green-400",
  L04: "border-l-4 border-l-purple-400",
};

function getCvColor(coefficientOfVariation: number): { bg: string; text: string; border: string } {
  if (coefficientOfVariation <= 0.05) return { bg: "bg-secondary-container", text: "text-on-secondary-container", border: "border-secondary/20" };
  if (coefficientOfVariation <= 0.15) return { bg: "bg-primary-fixed", text: "text-primary", border: "border-primary/20" };
  if (coefficientOfVariation <= 0.30) return { bg: "bg-orange-50", text: "text-orange-700", border: "border-orange-300" };
  return { bg: "bg-error-container", text: "text-on-error-container", border: "border-error/30" };
}

function getCvLabel(coefficientOfVariation: number): string {
  if (coefficientOfVariation <= 0.05) return "Tốt";
  if (coefficientOfVariation <= 0.15) return "Khá";
  if (coefficientOfVariation <= 0.30) return "Trung bình";
  return "Kém";
}

export const FairnessHeatmap = memo(function FairnessHeatmap({
  qualityReport,
  activeStaff,
  warningThreshold = 0.20,
}: FairnessHeatmapProps) {
  const { fairnessByType, totalShiftsByStaff } = qualityReport;

  // Group by shift type (strip specialty suffix for L04)
  const groupedByType: Record<string, FairnessDetail[]> = {};
  for (const [key, detail] of Object.entries(fairnessByType)) {
    const typeId = key.includes(":") ? key.split(":")[0] : key;
    if (!groupedByType[typeId]) groupedByType[typeId] = [];
    groupedByType[typeId].push(detail);
  }

  // Per-staff total counts
  const staffTotals = Object.entries(totalShiftsByStaff ?? {})
    .sort(([, a], [, b]) => b - a);

  const overallCv = 1 - qualityReport.fairnessScore / 100;
  const hasWarning = overallCv > warningThreshold;

  return (
    <div className="space-y-4">
      {/* Summary banner */}
      <div className={`flex items-start gap-4 rounded-xl border p-4 ${hasWarning ? "bg-orange-50 border-orange-300" : "bg-secondary-container/30 border-secondary/20"}`}>
        <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${hasWarning ? "bg-orange-100" : "bg-secondary-container"}`}>
          <span className={`material-symbols-outlined text-[20px] ${hasWarning ? "text-orange-700" : "text-secondary"}`} aria-hidden="true"
            style={{ fontVariationSettings: "'FILL' 1" }}>
            {hasWarning ? "warning" : "verified"}
          </span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 flex-wrap">
            <p className={`font-semibold text-on-surface ${hasWarning ? "text-orange-900" : ""}`}>
              {hasWarning
                ? `Phân bổ chưa đều (CV=${(overallCv * 100).toFixed(0)}%)`
                : "Phân bổ đều trên toàn kỳ"}
            </p>
            <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold border ${
              hasWarning ? "bg-orange-100 text-orange-800 border-orange-200" : "bg-secondary-container text-on-secondary-container border-secondary/20"
            }`}>
              {getCvLabel(overallCv)} · CV {(overallCv * 100).toFixed(1)}%
            </span>
          </div>
          <p className={`text-label-sm mt-0.5 ${hasWarning ? "text-orange-700" : "text-on-surface-variant"}`}>
            {hasWarning
              ? "Một số loại ca hoặc chuyên khoa có chênh lệch ca trực giữa nhân sự. Cân nhắc dùng nút Cân bằng bên dưới."
              : "Tất cả loại ca được phân bổ tương đối đều giữa các nhân sự."}
          </p>
        </div>
      </div>

      {/* Per shift-type fairness cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {(["L01", "L02", "L03", "L04"] as const).map((typeId) => {
          const details = groupedByType[typeId] ?? [];
          const worstDetail = details.reduce<FairnessDetail | null>(
            (worst, d) => (!worst || d.coefficientOfVariation > worst.coefficientOfVariation ? d : worst), null
          );
          const avgCv = details.length > 0
            ? details.reduce((s, d) => s + d.coefficientOfVariation, 0) / details.length
            : 0;
          const colors = getCvColor(avgCv);

          return (
            <div
              key={typeId}
              className={`rounded-xl border p-3 ${TYPE_COLORS[typeId]} ${TYPE_ACCENT[typeId]}`}
            >
              <div className="flex items-center gap-2 mb-2">
                <span className="material-symbols-outlined text-[16px] text-on-surface-variant" aria-hidden="true">
                  {typeId === "L01" ? "emergency" : typeId === "L02" ? "schedule" : typeId === "L03" ? "medical_services" : "stethoscope"}
                </span>
                <span className="text-label-md font-semibold text-on-surface">{TYPE_LABELS[typeId]}</span>
              </div>

              {details.length === 0 ? (
                <p className="text-label-xs text-on-surface-variant">Không có dữ liệu</p>
              ) : (
                <>
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-bold border ${colors.bg} ${colors.text} ${colors.border}`}>
                      {getCvLabel(avgCv)} · CV {(avgCv * 100).toFixed(0)}%
                    </span>
                    {avgCv > warningThreshold && (
                      <span className="material-symbols-outlined text-[14px] text-orange-600" title="Chênh lệch cao"
                        style={{ fontVariationSettings: "'FILL' 1" }} aria-label="cảnh báo">warning</span>
                    )}
                  </div>
                  {worstDetail && (
                    <div className="space-y-1">
                      {details.map((d) => {
                        return (
                          <div key={d.shiftType} className="flex items-center justify-between gap-2">
                            <span className="text-label-xs text-on-surface-variant truncate">
                              {d.specialtyName ? `${d.specialtyName}` : "(tất cả)"}
                            </span>
                            <div className="flex items-center gap-1.5">
                              <div className="w-16 bg-surface-variant/50 rounded-full h-1.5 overflow-hidden">
                                <div
                                  className={`h-1.5 rounded-full ${d.coefficientOfVariation <= 0.05 ? "bg-secondary" : d.coefficientOfVariation <= 0.15 ? "bg-primary" : d.coefficientOfVariation <= 0.30 ? "bg-orange-500" : "bg-error"}`}
                                  style={{ width: `${Math.min(100, d.coefficientOfVariation * 300)}%` }}
                                />
                              </div>
                              <span className="text-[11px] font-mono tabular-nums text-on-surface-variant w-12 text-right">
                                {d.minShifts}–{d.maxShifts}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                  <div className="flex justify-between text-[11px] text-on-surface-variant mt-2 pt-2 border-t border-outline-variant/50">
                    <span>Trung bình</span>
                    <span className="font-mono tabular-nums">{avgCv.toFixed(2)} ca/NS</span>
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>

      {/* Per-staff workload bar chart */}
      <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4">
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">bar_chart</span>
          <h3 className="text-label-md font-semibold text-on-surface">Tải trọng theo nhân sự</h3>
        </div>
        <div className="space-y-1.5 max-h-64 overflow-y-auto">
          {staffTotals.map(([staffId, total]) => {
            const staff = activeStaff.find((s) => s.id === Number(staffId));
            const specialtyName = staff?.specialtyName ?? "";
            const maxTotal = staffTotals[0]?.[1] ?? total;
            const pct = maxTotal > 0 ? (total / maxTotal) * 100 : 0;
            return (
              <div key={staffId} className="flex items-center gap-2">
                <div className="w-32 shrink-0">
                  <p className="text-label-xs text-on-surface truncate" title={staff?.fullName}>
                    {staff?.fullName ?? `NS #${staffId}`}
                  </p>
                  <p className="text-[10px] text-on-surface-variant">{specialtyName}</p>
                </div>
                <div className="flex-1 bg-surface-variant/50 rounded-full h-4 overflow-hidden">
                  <div
                    className={`h-4 rounded-full transition-all ${
                      pct >= 90 ? "bg-red-400" : pct >= 70 ? "bg-orange-400" : pct >= 50 ? "bg-primary" : "bg-secondary"
                    }`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <span className="text-label-xs font-mono tabular-nums text-on-surface w-8 text-right shrink-0">
                  {total}
                </span>
              </div>
            );
          })}
        </div>
        <div className="flex items-center gap-4 mt-3 pt-3 border-t border-outline-variant/50 text-[11px] text-on-surface-variant">
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-secondary inline-block" /> Tối ưu
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-primary inline-block" /> Khá
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-orange-400 inline-block" /> Gần quá tải
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-red-400 inline-block" /> Quá tải
          </span>
        </div>
      </div>
    </div>
  );
});
