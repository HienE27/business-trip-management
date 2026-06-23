"use client";

import { memo, useEffect, useMemo, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { AutoScheduleMatrixGrid } from "./AutoScheduleMatrixGrid";
import { SectionCard } from "@/components/ui/SectionCard";
import type { AutoScheduleResult, SchedulePeriod } from "@/types/api";
import type { Staff } from "@/types/api";
import { ALGORITHM_OPTIONS } from "./constants";
import type { ScheduleTab } from "./types";
import type { ViewMode } from "./AutoScheduleMatrixGrid";


type AlgorithmType = "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING";

type EditedPreview = Array<{ workDate: string; shiftTypeId: string; staffId: number }>;

export type AutoSchedulePanelProps = {
  previewResult: AutoScheduleResult | null;
  editedPreview: EditedPreview;
  activeStaff: Staff[];
  applyingPreview: boolean;
  runningAutoSchedule: boolean;
  message: string | null;
  algorithmType: AlgorithmType;
  selectedPeriod: SchedulePeriod | null;
  selectedPeriodId: number | null;
  selectedPeriodStatus?: string;
  conflictKeys: Set<string>;
  onPreview: () => void;
  onApplyPreview: () => void;
  onResetEdits: () => void;
  onEditStaff: (workDate: string, shiftTypeId: string, staffId: number) => void;
  onSetAlgorithmType: (type: AlgorithmType) => void;
  /** Mở modal lưu mẫu lịch (M07-F10) */
  onSaveTemplate?: () => void;
  /** Mở modal áp dụng mẫu lịch (M07-F10) */
  onApplyTemplate?: () => void;
  /** Có quyền quản lý — để ẩn/hiện nút template */
  isManager?: boolean;
};

const ALGORITHM_CARDS: Record<AlgorithmType, { icon: string; color: string; description: string; badge: string }> = {
  GREEDY: {
    icon: "bolt",
    color: "text-amber-600 bg-amber-50 border-amber-200",
    description: "Ưu tiên nhân sự ít ca nhất mỗi ngày. Nhanh, phù hợp kỳ ngắn.",
    badge: "bg-amber-100 text-amber-800 border-amber-300",
  },
  ROUND_ROBIN: {
    icon: "autorenew",
    color: "text-blue-600 bg-blue-50 border-blue-200",
    description: "Luân chuyển đều theo vòng tròn. Đảm bảo công bằng tuyệt đối.",
    badge: "bg-blue-100 text-blue-800 border-blue-300",
  },
  BACKTRACKING: {
    icon: "route",
    color: "text-purple-600 bg-purple-50 border-purple-200",
    description: "Tìm kiếm tất cả phương án, chọn kết quả tốt nhất. Chậm hơn nhưng tối ưu hơn.",
    badge: "bg-purple-100 text-purple-800 border-purple-300",
  },
};

function KpiCard({
  icon,
  label,
  value,
  suffix,
  trend,
  variant = "default",
}: {
  icon: string;
  label: string;
  value: string | number;
  suffix?: string;
  trend?: "up" | "down" | "neutral";
  variant?: "default" | "success" | "warning" | "error";
}) {
  const variantClasses = {
    default: "bg-surface-container-lowest border-outline-variant",
    success: "bg-secondary-container border-secondary/30",
    warning: "bg-amber-50 border-amber-200",
    error: "bg-error-container border-error/30",
  };

  const iconBg = {
    default: "bg-primary-fixed text-primary",
    success: "bg-secondary-container text-secondary",
    warning: "bg-amber-50 text-amber-600",
    error: "bg-error-container text-error",
  };

  return (
    <div className={`relative overflow-hidden rounded-xl border p-4 shadow-sm ${variantClasses[variant]} transition-all hover:shadow-md`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-label-sm font-medium uppercase tracking-wide text-on-surface-variant">{label}</p>
          <div className="mt-1.5 flex items-baseline gap-1.5">
            <span className="text-2xl font-bold tabular-nums text-on-surface">{value}</span>
            {suffix && <span className="text-label-sm font-medium text-on-surface-variant">{suffix}</span>}
          </div>
          {trend && (
            <div className={`mt-1 flex items-center gap-1 text-label-xs font-medium ${
              trend === "up" ? "text-secondary" : trend === "down" ? "text-error" : "text-on-surface-variant"
            }`}>
              <span className="material-symbols-outlined text-[12px]">
                {trend === "up" ? "arrow_upward" : trend === "down" ? "arrow_downward" : "remove"}
              </span>
            </div>
          )}
        </div>
        <div className={`flex shrink-0 items-center justify-center rounded-lg p-2 ${iconBg[variant]}`}>
          <span className="material-symbols-outlined text-[20px]" style={{ fontVariationSettings: "'FILL' 0" }}>{icon}</span>
        </div>
      </div>
    </div>
  );
}

function CoverageBar({ rate }: { rate: number }) {
  const pct = Math.min(100, Math.max(0, rate));
  const color = pct >= 90 ? "bg-secondary" : pct >= 70 ? "bg-amber-500" : "bg-error";

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-label-xs text-on-surface-variant">
        <span>Tỷ lệ phủ bì</span>
        <span className="font-semibold text-on-surface">{pct}%</span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-variant">
        <div
          className={`h-full rounded-full transition-all duration-500 ${color}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

function UnassignedBadge({ missing }: { missing: number }) {
  if (missing === 0) return (
    <span className="inline-flex items-center gap-1 rounded-full bg-secondary-container px-2.5 py-0.5 text-label-xs font-semibold text-on-secondary-container">
      <span className="material-symbols-outlined text-[12px]">check_circle</span>
      Đủ
    </span>
  );
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-error-container px-2.5 py-0.5 text-label-xs font-semibold text-on-error-container">
      <span className="material-symbols-outlined text-[12px]">warning</span>
      Thiếu {missing}
    </span>
  );
}

export const AutoSchedulePanel = memo(function AutoSchedulePanel({
  previewResult,
  editedPreview,
  activeStaff,
  applyingPreview,
  runningAutoSchedule,
  message,
  algorithmType,
  selectedPeriod,
  selectedPeriodId,
  selectedPeriodStatus,
  conflictKeys,
  onPreview,
  onApplyPreview,
  onResetEdits,
  onEditStaff,
  onSetAlgorithmType,
  onSaveTemplate,
  onApplyTemplate,
  isManager = true,
}: AutoSchedulePanelProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("month");
  const [selectedStaffIds, setSelectedStaffIds] = useState<Set<number>>(new Set());
  const [staffFilterOpen, setStaffFilterOpen] = useState(false);
  const [showUnassigned, setShowUnassigned] = useState(false);
  const isDraft = selectedPeriodStatus === "DRAFT";

  // Reset view/filters when period changes
  useEffect(() => {
    setViewMode("month");
    setSelectedStaffIds(new Set());
  }, [selectedPeriodId]);

  const algoCard = ALGORITHM_CARDS[algorithmType];

  // Unassigned days from response
  const unassignedDays = previewResult?.unassignedDays ?? [];
  const totalMissing = unassignedDays.reduce((sum: number, d: any) => sum + (d.missingCount ?? 0), 0);

  const statusMessageIsSuccess = message?.toLowerCase().includes("thành công") || message?.toLowerCase().includes("đã áp dụng") || message?.toLowerCase().includes("đã hủy") || message?.toLowerCase().includes("đã làm mới");

  const coverageRate = previewResult ? Math.round(previewResult.coverageRate) : 0;

  return (
    <SectionCard
      title="Xếp lịch tự động"
      description="Chọn thuật toán, chạy preview và áp dụng phương án vào kỳ lịch."
      action={
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onPreview}
            className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary shadow-sm transition-all hover:bg-primary/90 hover:shadow disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            disabled={runningAutoSchedule || !selectedPeriodId || !isDraft}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">play_arrow</span>
            {runningAutoSchedule ? "Đang chạy…" : previewResult ? "Làm mới" : "Chạy ngay"}
          </button>
        </div>
      }
    >
      {/* Algorithm selector - always visible */}
      <div className="border-b border-outline-variant p-5">
        <p className="mb-3 text-label-sm font-medium uppercase tracking-wide text-on-surface-variant">Chọn thuật toán</p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {(Object.keys(ALGORITHM_CARDS) as AlgorithmType[]).map((type) => {
            const card = ALGORITHM_CARDS[type];
            const isSelected = algorithmType === type;
            return (
              <button
                key={type}
                type="button"
                onClick={() => onSetAlgorithmType(type)}
                className={`group relative cursor-pointer rounded-xl border-2 p-4 text-left transition-all ${
                  isSelected
                    ? `${card.color} border-current shadow-sm`
                    : "border-outline-variant bg-surface-container-lowest hover:border-on-surface-variant hover:bg-surface-container-low"
                }`}
              >
                {isSelected && (
                  <span className="absolute right-3 top-3 material-symbols-outlined text-[18px] text-current" aria-hidden="true">check_circle</span>
                )}
                <div className="flex items-center gap-2.5 mb-2">
                  <span className={`material-symbols-outlined text-[22px] ${isSelected ? "text-current" : "text-on-surface-variant group-hover:text-on-surface"}`} aria-hidden="true">{card.icon}</span>
                  <span className="font-semibold text-on-surface">{type.replace("_", " ")}</span>
                </div>
                <p className={`text-label-sm leading-relaxed ${isSelected ? "text-current" : "text-on-surface-variant"}`}>
                  {card.description}
                </p>
              </button>
            );
          })}
        </div>
        {runningAutoSchedule && (
          <div className="mt-3 flex items-center gap-2 text-label-sm text-primary" aria-live="polite">
            <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-hidden="true" />
            Đang chạy thuật toán…
          </div>
        )}
        {!isDraft && (
          <div className="mt-3 flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-label-sm text-amber-700 border border-amber-200">
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">info</span>
            Chỉ kỳ lịch ở trạng thái <strong className="font-semibold">DRAFT</strong> mới có thể xếp tự động
          </div>
        )}
        {message && (
          <div
            className={`mt-3 flex items-center gap-2 rounded-lg border px-4 py-2.5 text-label-sm ${
              statusMessageIsSuccess
                ? "bg-secondary-container border-secondary/30 text-secondary"
                : "bg-error-container border-error/30 text-error"
            }`}
            role="status"
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
              {statusMessageIsSuccess ? "check_circle" : "error"}
            </span>
            {message}
          </div>
        )}
      </div>

      {/* Results */}
      {previewResult ? (
        <div className="p-5 space-y-5">
          {/* KPI Row */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <KpiCard
              icon="event_available"
              label="Đã tạo"
              value={previewResult.totalSchedulesCreated}
              tone="success"
            />
            <KpiCard
              icon="donut_large"
              label="Phủ bì"
              value={coverageRate}
              suffix="%"
              tone={coverageRate >= 90 ? "success" : coverageRate >= 70 ? "warning" : "error"}
            />
            <KpiCard
              icon="balance"
              label="Cân bằng"
              value={typeof previewResult.balanceScore === "number" ? Math.round(previewResult.balanceScore) : 0}
              suffix="%"
              tone="neutral"
            />
            <KpiCard
              icon={previewResult.conflictCount > 0 ? "warning" : "check_circle"}
              label="Xung đột"
              value={previewResult.conflictCount}
              tone={previewResult.conflictCount > 0 ? "error" : "success"}
            />
          </div>

          {/* Coverage bar */}
          <CoverageBar rate={coverageRate} />

          {/* View toggle + staff filter */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            {/* View mode toggle */}
            <div className="flex items-center gap-1 rounded-lg border border-outline-variant bg-surface-container-low p-1">
              <button
                type="button"
                onClick={() => setViewMode("week")}
                className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all ${
                  viewMode === "week"
                    ? "bg-primary text-on-primary shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface"
                }`}
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">view_week</span>
                Tuần
              </button>
              <button
                type="button"
                onClick={() => setViewMode("month")}
                className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-label-sm font-medium transition-all ${
                  viewMode === "month"
                    ? "bg-primary text-on-primary shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface"
                }`}
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">calendar_view_month</span>
                Tháng
              </button>
            </div>

            {/* Staff filter */}
            <div className="relative">
              <button
                type="button"
                onClick={() => setStaffFilterOpen(!staffFilterOpen)}
                className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-label-sm font-medium transition-all ${
                  selectedStaffIds.size > 0
                    ? "border-primary bg-primary-fixed/30 text-primary"
                    : "border-outline-variant bg-surface-container-low text-on-surface hover:bg-surface-container-high"
                }`}
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">filter_list</span>
                {selectedStaffIds.size > 0 ? `Nhân sự (${selectedStaffIds.size})` : "Tất cả nhân sự"}
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{staffFilterOpen ? "expand_less" : "expand_more"}</span>
              </button>

              {staffFilterOpen && (
                <div className="absolute right-0 top-full z-50 mt-1.5 w-64 rounded-xl border border-outline-variant bg-surface-container-lowest p-3 shadow-lg">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-label-sm font-semibold text-on-surface">Lọc nhân sự</p>
                    {selectedStaffIds.size > 0 && (
                      <button
                        type="button"
                        onClick={() => setSelectedStaffIds(new Set())}
                        className="text-label-xs text-primary hover:underline"
                      >
                        Bỏ lọc
                      </button>
                    )}
                  </div>
                  <div className="max-h-48 space-y-1 overflow-y-auto">
                    {activeStaff.map((staff) => {
                      const isSelected = selectedStaffIds.has(staff.id);
                      return (
                        <label
                          key={staff.id}
                          className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-surface-container-low"
                        >
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => {
                              setSelectedStaffIds((prev) => {
                                const next = new Set(prev);
                                if (next.has(staff.id)) next.delete(staff.id);
                                else next.add(staff.id);
                                return next;
                              });
                            }}
                            className="h-4 w-4 rounded border-outline text-primary accent-primary"
                          />
                          <span className="text-label-sm text-on-surface">{staff.fullName}</span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Unassigned days alert */}
          {unassignedDays.length > 0 && (
            <div className={`rounded-xl border p-4 transition-all ${
              totalMissing > 0 ? "bg-error-container border-error/30" : "bg-secondary-container border-secondary/30"
            }`}>
              <button
                type="button"
                onClick={() => setShowUnassigned(!showUnassigned)}
                className="flex w-full items-center justify-between text-left"
              >
                <div className="flex items-center gap-2">
                  <span className={`material-symbols-outlined text-[20px] ${totalMissing > 0 ? "text-error" : "text-secondary"}`} aria-hidden="true">
                    {totalMissing > 0 ? "warning" : "check_circle"}
                  </span>
                  <div>
                    <p className={`font-semibold text-on-surface ${totalMissing > 0 ? "text-error" : "text-secondary"}`}>
                      {totalMissing > 0 ? `${unassignedDays.length} ngày chưa đủ nhân sự` : "Tất cả ngày đã đủ nhân sự"}
                    </p>
                    {totalMissing > 0 && (
                      <p className="text-label-sm text-on-surface-variant">Tổng thiếu {totalMissing} nhân sự trên {unassignedDays.length} ngày</p>
                    )}
                  </div>
                </div>
                <span className={`material-symbols-outlined text-on-surface-variant transition-transform ${showUnassigned ? "rotate-180" : ""}`} aria-hidden="true">expand_more</span>
              </button>
              {showUnassigned && (
                <div className="mt-3 space-y-2">
                  {unassignedDays.map((day: any, idx: number) => (
                    <div key={idx} className="flex items-center justify-between rounded-lg bg-surface-container-lowest px-3 py-2 text-label-sm">
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-on-surface">{new Date(day.workDate).toLocaleDateString("vi-VN", { weekday: "short", day: "numeric", month: "short" })}</span>
                        <span className={`rounded border px-1.5 py-0.5 text-label-xs font-semibold ${
                          day.shiftTypeId === "L01" ? "bg-red-100 text-red-700 border-red-200" :
                          day.shiftTypeId === "L02" ? "bg-blue-100 text-blue-700 border-blue-200" :
                          day.shiftTypeId === "L03" ? "bg-emerald-100 text-emerald-700 border-emerald-200" :
                          "bg-purple-100 text-purple-700 border-purple-200"
                        }`}>{day.shiftTypeName}</span>
                      </div>
                      <span className="text-error font-semibold">Thiếu {day.missingCount}/{day.requiredStaffCount}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Schedule matrix: rows=dates, cols=staff (same pattern as ScheduleMatrixGrid) */}
          <div className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
            <div className="flex items-center justify-between border-b border-outline-variant bg-surface-container-low px-4 py-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-primary" aria-hidden="true">grid_view</span>
                <div>
                  <p className="font-semibold text-on-surface">Phương án phân công</p>
                  <p className="text-label-xs text-on-surface-variant">
                    {new Set(previewResult.schedules.map((s) => s.workDate.split("T")[0])).size} ngày · {previewResult.totalSchedulesCreated} ca trực
                  </p>
                </div>
              </div>
              {editedPreview.length > 0 && (
                <button
                  type="button"
                  onClick={onResetEdits}
                  className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-label-sm font-medium text-error transition-colors hover:bg-error-container"
                >
                  <span className="material-symbols-outlined text-[14px]" aria-hidden="true">undo</span>
                  Hủy thay đổi ({editedPreview.length})
                </button>
              )}
            </div>

            <div className="p-3">
              <AutoScheduleMatrixGrid
                key={viewMode}
                schedules={previewResult.schedules}
                activeStaff={activeStaff}
                year={selectedPeriod ? new Date(selectedPeriod.startDate).getFullYear() : new Date().getFullYear()}
                month={selectedPeriod ? new Date(selectedPeriod.startDate).getMonth() : new Date().getMonth()}
                viewMode={viewMode}
                filteredStaffIds={selectedStaffIds}
                editedPreview={editedPreview}
              />
            </div>
          </div>

          {/* Action bar */}
          <div className="flex items-center justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-low p-4">
            <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
              {editedPreview.length > 0 ? (
                <>
                  <span className="material-symbols-outlined text-[16px] text-amber-600" aria-hidden="true">edit</span>
                  <span><strong className="text-on-surface">{editedPreview.length}</strong> thay đổi đang chờ áp dụng</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">info</span>
                  <span>Xem trước phương án trước khi áp dụng vào kỳ lịch</span>
                </>
              )}
            </div>
            <div className="flex items-center gap-2">
              {/* M07-F10: Template actions */}
              {isManager && previewResult && (
                <>
                  <button
                    type="button"
                    onClick={onSaveTemplate}
                    className="flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-label-sm font-medium text-on-surface transition-colors hover:bg-surface-container-high disabled:opacity-50"
                    title="Lưu phương án hiện tại thành mẫu để sử dụng lại sau"
                  >
                    <span className="material-symbols-outlined text-[16px]" aria-hidden="true">bookmark_add</span>
                    Lưu mẫu
                  </button>
                  <button
                    type="button"
                    onClick={onApplyTemplate}
                    className="flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-label-sm font-medium text-on-surface transition-colors hover:bg-surface-container-high disabled:opacity-50"
                    title="Áp dụng mẫu lịch đã lưu vào kỳ hiện tại"
                  >
                    <span className="material-symbols-outlined text-[16px]" aria-hidden="true">download</span>
                    Áp dụng mẫu
                  </button>
                </>
              )}
              <button
                type="button"
                onClick={onApplyPreview}
                disabled={applyingPreview || !previewResult}
                className="flex items-center gap-2 rounded-lg bg-secondary px-5 py-2.5 text-label-md font-semibold text-on-secondary shadow-sm transition-all hover:bg-secondary/90 hover:shadow disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-secondary"
              >
                {applyingPreview ? (
                  <>
                    <div className="size-4 animate-spin rounded-full border-2 border-on-secondary border-t-transparent" aria-hidden="true" />
                    Đang áp dụng…
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[18px]" aria-hidden="true">check_circle</span>
                    Áp dụng phương án
                    {editedPreview.length > 0 && <span className="ml-1 rounded bg-white/20 px-1.5 py-0.5 text-label-xs">+{editedPreview.length}</span>}
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-4 p-10">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary-fixed">
            <span className="material-symbols-outlined text-[36px] text-primary" aria-hidden="true">auto_mode</span>
          </div>
          <div className="text-center">
            <h3 className="text-headline-md font-semibold text-on-surface">Sẵn sàng xếp lịch tự động</h3>
            <p className="mt-1 text-body-sm text-on-surface-variant max-w-sm">
              Chọn thuật toán phù hợp và nhấn <strong>Chạy ngay</strong> để hệ thống tự động phân bổ ca trực cho kỳ lịch.
            </p>
          </div>
          <div className="flex flex-col items-center gap-1.5 text-label-sm text-on-surface-variant">
            <div className="flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">check_circle</span>
              Tự động phát hiện xung đột lịch trực
            </div>
            <div className="flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">check_circle</span>
              Tạo ngày nghỉ bù cho ca trực 24/24
            </div>
            <div className="flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">check_circle</span>
              Cân bằng tải đều cho 20 nhân sự
            </div>
          </div>
        </div>
      )}
    </SectionCard>
  );
});
