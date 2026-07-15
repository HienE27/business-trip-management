"use client";

/**
 * WorkloadChart — visualization cho staff workload, 3 view modes:
 *
 *  - `bar`     — Tổng ca trực / nhân sự (theo ca)
 *  - `stacked` — Phân bổ ca theo từng loại lịch (L01-L04)
 *  - `balance` — Cân bằng tải theo ratio vs trung bình (chỉ khi có
 *                previewSchedules — tính từ thuật toán auto-scheduling)
 *
 * Nguồn dữ liệu:
 *  1. **Preview** — từ `previewSchedules` (in-memory, no API)
 *  2. **Saved** — fetch từ `/api/v1/auto-schedule/workload-chart/{periodId}`
 *
 * Dùng `--color-chart-*` CSS tokens (sửa ở globals.css để đổi palette).
 * Accessible: keyboard nav cho toggle, aria-label trên chart, focus tooltip.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/ToastProvider";
import {
  aggregateByStaff,
  BALANCE_THRESHOLDS,
  SHIFT_LABELS,
  topN,
  type ShiftTypeId,
  type StaffAggregate,
  type StaffBalanceStatus,
} from "@/lib/schedule-aggregates";
import { api } from "@/lib/api";
import type { AutoScheduleSummary } from "@/types/api";

/* ── Internal types ── */
interface WorkloadStaffData {
  staffId: number;
  staffName: string;
  specialty: string | null;
  totalShifts: number;
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  workloadPercentage: number;
}

interface WorkloadChartData {
  totalShifts: number;
  totalStaff: number;
  averageWorkload: number;
  minWorkload: number;
  maxWorkload: number;
  staffWorkloadData: WorkloadStaffData[];
}

type ViewMode = "bar" | "stacked" | "balance";

// Eligible specialties for filtering
const ELIGIBLE_SPECIALTIES = ["Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"];

const CHART_COLOR_CLASS: Record<ShiftTypeId, string> = {
  L01: "bg-chart-24",
  L02: "bg-chart-tt",
  L03: "bg-chart-dv",
  L04: "bg-chart-cg",
};

const STATUS_DOT_CLASS: Record<StaffBalanceStatus, string> = {
  balanced: "bg-chart-tt",
  caution: "bg-chart-cg",
  overloaded: "bg-chart-24",
};

const STATUS_BADGE: Record<
  StaffBalanceStatus,
  { icon: string; text: string; bg: string; color: string }
> = {
  overloaded: { icon: "warning", text: "Quá tải", bg: "bg-error-container", color: "text-on-error-container" },
  caution:    { icon: "horizontal_rule", text: "Vượt nhẹ", bg: "bg-tertiary-fixed", color: "text-on-tertiary-fixed-variant" },
  balanced:   { icon: "check_circle", text: "Cân bằng", bg: "bg-secondary-container", color: "text-on-secondary-container" },
};

/* ── Accessible tooltip (hover + focus) ── */
function AccessibleTooltip({
  visible,
  x,
  y,
  content,
}: {
  visible: boolean;
  x: number;
  y: number;
  content: React.ReactNode;
}) {
  if (!visible) return null;
  return (
    <div
      role="tooltip"
      className="fixed z-50 pointer-events-none"
      style={{ left: x + 12, top: y - 8, transform: "translateY(-100%)" }}
    >
      <div className="bg-on-surface text-surface rounded-lg px-3 py-2 shadow-xl whitespace-nowrap border border-outline-variant/30 text-label-sm">
        {content}
      </div>
    </div>
  );
}

/* ── Legend ── */
function Legend() {
  return (
    <div className="flex items-center gap-4 flex-wrap" aria-label="Chú thích màu biểu đồ">
      {(Object.entries(CHART_COLOR_CLASS) as [ShiftTypeId, string][]).map(([key, fill]) => (
        <div key={key} className="flex items-center gap-1.5">
          <span className={`w-3 h-3 rounded-sm inline-block shrink-0 ${fill}`} aria-hidden="true" />
          <span className="text-label-sm text-on-surface-variant">{SHIFT_LABELS[key]}</span>
        </div>
      ))}
    </div>
  );
}

/* ── Hover/leave tooltip helper ── */
type TooltipState = {
  visible: boolean;
  x: number;
  y: number;
  content: React.ReactNode;
};
const EMPTY_TOOLTIP: TooltipState = { visible: false, x: 0, y: 0, content: null };

function pointFromEvent(e: React.MouseEvent | React.FocusEvent): { x: number; y: number } {
  if ("clientX" in e) return { x: e.clientX, y: e.clientY };
  return { x: window.innerWidth / 2, y: window.innerHeight / 2 };
}

/* ════════════════════════════════════════════════════════════════
 * HORIZONTAL BAR — Tổng ca / nhân sự
 * ════════════════════════════════════════════════════════════════ */
function HorizontalBarChart({ data }: { data: WorkloadChartData }) {
  const [tooltip, setTooltip] = useState<TooltipState>(EMPTY_TOOLTIP);
  const maxShift = Math.max(...data.staffWorkloadData.map((s) => s.totalShifts), 1);
  const avgShift = data.averageWorkload;

  const buildContent = (staff: WorkloadStaffData) => (
    <span>
      <strong>{staff.staffName}</strong>
      <br />
      {staff.totalShifts} ca — {staff.totalShifts > avgShift ? "cao hơn TB" : "dưới TB"}
    </span>
  );

  return (
    <>
      <div role="img" aria-label="Biểu đồ tải công việc theo nhân sự" className="space-y-2">
        {data.staffWorkloadData.map((staff) => {
          const pct = (staff.totalShifts / maxShift) * 100;
          const isOverAvg = avgShift > 0 && staff.totalShifts > avgShift * 1.3;
          return (
            <div
              key={staff.staffId}
              tabIndex={0}
              role="button"
              onMouseEnter={(e) => setTooltip({ visible: true, ...pointFromEvent(e), content: buildContent(staff) })}
              onMouseMove={(e) => setTooltip((t) => (t.visible ? { ...t, x: e.clientX, y: e.clientY } : t))}
              onMouseLeave={() => setTooltip((t) => ({ ...t, visible: false }))}
              onFocus={(e) => setTooltip({ visible: true, ...pointFromEvent(e), content: buildContent(staff) })}
              onBlur={() => setTooltip((t) => ({ ...t, visible: false }))}
              aria-label={`${staff.staffName}: ${staff.totalShifts} ca, ${staff.totalShifts > avgShift ? "cao hơn" : "dưới"} trung bình`}
              className="grid grid-cols-[minmax(0,1fr)_56px] sm:grid-cols-[160px_minmax(0,1fr)_64px] items-center gap-3 rounded-lg p-1 hover:bg-surface-container-low/50 focus-within:bg-surface-container-low/50 transition-colors"
            >
              <span
                title={staff.staffName}
                className="text-label-sm font-medium text-on-surface truncate hidden sm:block"
              >
                {staff.staffName}
              </span>
              <span className="text-label-sm font-medium text-on-surface truncate sm:hidden col-span-2">
                {staff.staffName}
              </span>

              <div className="relative flex-1 rounded-full h-8 bg-surface-container-low overflow-hidden col-span-2 sm:col-span-1">
                <div
                  className={`h-full rounded-full transition-all duration-500 ease-out ${isOverAvg ? "bg-chart-24" : "bg-primary"}`}
                  style={{ width: `${Math.max(pct, 3)}%` }}
                />
                {avgShift > 0 && (
                  <div
                    className="absolute top-0 bottom-0 w-0.5 bg-tertiary opacity-70"
                    style={{ left: `${(avgShift / maxShift) * 100}%` }}
                    aria-hidden="true"
                  />
                )}
              </div>

              <span
                className={`text-right text-label-sm font-bold tabular-nums ${isOverAvg ? "text-error" : "text-on-surface"}`}
                data-testid="balance-value"
              >
                {staff.totalShifts}
              </span>
            </div>
          );
        })}
      </div>

      <AccessibleTooltip visible={tooltip.visible} x={tooltip.x} y={tooltip.y} content={tooltip.content} />
    </>
  );
}

/* ════════════════════════════════════════════════════════════════
 * STACKED BAR — Breakdown theo loại lịch
 * ════════════════════════════════════════════════════════════════ */
function StackedBarChart({ data }: { data: WorkloadChartData }) {
  const [tooltip, setTooltip] = useState<TooltipState>(EMPTY_TOOLTIP);
  const maxShift = Math.max(...data.staffWorkloadData.map((s) => s.totalShifts), 1);

  return (
    <>
      <div role="img" aria-label="Biểu đồ phân bổ ca theo loại lịch" className="space-y-2">
        {data.staffWorkloadData.map((staff) => {
          const parts: { key: ShiftTypeId; count: number }[] = (
            [
              { key: "L01" as const, count: staff.L01 },
              { key: "L02" as const, count: staff.L02 },
              { key: "L03" as const, count: staff.L03 },
              { key: "L04" as const, count: staff.L04 },
            ] as const
          ).filter((p) => p.count > 0);

          if (parts.length === 0) return null;

          const content = (
            <span>
              <strong>{staff.staffName}</strong>
              <br />
              {parts.map((p) => (
                <span key={p.key}>
                  {SHIFT_LABELS[p.key]}: {p.count}
                  <br />
                </span>
              ))}
            </span>
          );

          return (
            <div
              key={staff.staffId}
              tabIndex={0}
              role="button"
              onMouseEnter={(e) => setTooltip({ visible: true, ...pointFromEvent(e), content })}
              onMouseMove={(e) => setTooltip((t) => (t.visible ? { ...t, x: e.clientX, y: e.clientY } : t))}
              onMouseLeave={() => setTooltip((t) => ({ ...t, visible: false }))}
              onFocus={(e) => setTooltip({ visible: true, ...pointFromEvent(e), content })}
              onBlur={() => setTooltip((t) => ({ ...t, visible: false }))}
              aria-label={`${staff.staffName}: ${parts.map((p) => `${SHIFT_LABELS[p.key]} ${p.count}`).join(", ")}`}
              className="grid grid-cols-[minmax(0,1fr)] sm:grid-cols-[160px_minmax(0,1fr)] items-center gap-3 rounded-lg p-1 hover:bg-surface-container-low/50 focus-within:bg-surface-container-low/50 transition-colors"
            >
              <span title={staff.staffName} className="text-label-sm font-medium text-on-surface truncate">
                {staff.staffName}
              </span>
              <div className="flex rounded-full h-8 bg-surface-container-low overflow-hidden col-span-2 sm:col-span-1">
                {parts.map((part) => (
                  <div
                    key={part.key}
                    className={`h-full flex items-center justify-center overflow-hidden ${CHART_COLOR_CLASS[part.key]}`}
                    style={{
                      width: `${(part.count / maxShift) * 100}%`,
                      minWidth: part.count > 0 ? 8 : 0,
                    }}
                  >
                    {part.count > 0 && (
                      <span className="text-label-sm font-bold text-white opacity-95 whitespace-nowrap px-1 tabular-nums">
                        {part.count}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      <AccessibleTooltip visible={tooltip.visible} x={tooltip.x} y={tooltip.y} content={tooltip.content} />
    </>
  );
}

/* ════════════════════════════════════════════════════════════════
 * BALANCE VIEW — Cân bằng tải theo ratio (chỉ preview)
 *
 * §M07-F09 của spec: "Biểu đồ số ngày trực / số ngày làm của từng
 * nhân sự trong tháng để quản lý xem xét mức độ phân bổ."
 * ════════════════════════════════════════════════════════════════ */

function BalanceStatusBadge({ status }: { status: StaffBalanceStatus }) {
  const cfg = STATUS_BADGE[status];
  return (
    <span
      aria-label={cfg.text}
      className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-[11px] font-bold whitespace-nowrap ${cfg.bg} ${cfg.color}`}
    >
      <span className="material-symbols-outlined text-[12px]" aria-hidden="true">{cfg.icon}</span>
      <span className="hidden sm:inline">{cfg.text}</span>
    </span>
  );
}

function BalanceBreakdown({ row }: { row: StaffAggregate }) {
  const shifts: { id: ShiftTypeId; count: number }[] = (
    [
      { id: "L01" as const, count: row.L01 },
      { id: "L02" as const, count: row.L02 },
      { id: "L03" as const, count: row.L03 },
      { id: "L04" as const, count: row.L04 },
    ] as const
  ).filter((s) => s.count > 0);

  if (shifts.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-1 mt-1" aria-label="Phân bổ theo loại lịch">
      {shifts.map((s) => (
        <span
          key={s.id}
          className="inline-flex items-center gap-0.5 text-[10px] font-medium text-on-surface-variant tabular-nums"
        >
          <span className={`w-1.5 h-1.5 rounded-full ${CHART_COLOR_CLASS[s.id]}`} aria-hidden />
          <span>{s.id}</span>
          <span className="font-bold text-on-surface">{s.count}</span>
        </span>
      ))}
    </div>
  );
}

function BalanceView({ rows, hidden }: { rows: StaffAggregate[]; hidden: number }) {
  const maxRatio = Math.max(...rows.map((r) => r.ratio), 1);

  return (
    <div role="img" aria-label="Biểu đồ cân bằng tải theo từng nhân sự" className="space-y-2">
      <ul className="space-y-2" role="list">
        {rows.map((row, index) => {
          const barWidth = maxRatio > 0 ? Math.min(100, (row.ratio / maxRatio) * 100) : 0;
          const avgMarker = maxRatio > 0 ? Math.min(100, (1 / maxRatio) * 100) : 0;
          const pct = row.ratio * 100;

          return (
            <li
              key={row.staffId}
              data-testid="balance-row"
              className="grid grid-cols-[minmax(0,1fr)_2fr_56px_28px] sm:grid-cols-[120px_minmax(0,1fr)_56px_96px] items-center gap-2 sm:gap-3 p-2 rounded-lg hover:bg-surface-container-low transition-colors"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-1.5">
                  <p className="text-label-sm font-semibold text-on-surface truncate" title={row.staffName}>
                    {row.staffName}
                  </p>
                  <span className="hidden md:inline-flex h-4 min-w-4 items-center justify-center rounded bg-surface-container-high text-[9px] font-bold text-on-surface-variant px-1 tabular-nums">
                    #{index + 1}
                  </span>
                </div>
                <p className="text-[11px] text-on-surface-variant tabular-nums">
                  {row.total} ca / TB {row.avg.toFixed(1)}
                </p>
                <BalanceBreakdown row={row} />
              </div>

              <div className="relative h-6 bg-surface-variant rounded-full overflow-hidden">
                <span
                  aria-hidden
                  className="absolute top-0 bottom-0 w-0.5 bg-outline z-10"
                  style={{ left: `${avgMarker}%` }}
                />
                <svg
                  width="100%"
                  height="100%"
                  viewBox="0 0 100 24"
                  preserveAspectRatio="none"
                  role="presentation"
                  aria-hidden
                  data-testid="balance-bar"
                  className="absolute inset-0 block"
                >
                  <rect
                    x={0}
                    y={4}
                    width={barWidth}
                    height={16}
                    rx={4}
                    fill={`var(--color-chart-${STATUS_DOT_CLASS[row.status].replace("bg-chart-", "")})`}
                    className="origin-left transition-[width] duration-700 ease-out"
                  />
                  <title>{`${row.staffName}: ${row.total} ca / TB ${row.avg.toFixed(1)} (${pct.toFixed(0)}%)`}</title>
                </svg>
              </div>

              <div className="text-right">
                <span
                  className={`text-label-sm font-bold tabular-nums ${
                    row.status === "overloaded"
                      ? "text-error"
                      : row.status === "caution"
                        ? "text-tertiary"
                        : "text-on-surface"
                  }`}
                  aria-label={`${pct.toFixed(0)} phần trăm so với trung bình`}
                >
                  {pct.toFixed(0)}%
                </span>
              </div>

              <div className="flex justify-end">
                <BalanceStatusBadge status={row.status} />
              </div>
            </li>
          );
        })}
      </ul>

      {hidden > 0 && (
        <p className="text-[11px] text-center text-on-surface-variant">
          +{hidden} nhân sự chưa hiển thị
        </p>
      )}

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 pt-3 border-t border-outline-variant text-label-xs text-on-surface-variant">
        <span className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-chart-tt" aria-hidden="true" />
          ≤ {(BALANCE_THRESHOLDS.CAUTION_RATIO * 100).toFixed(0)}% — Cân bằng
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-chart-cg" aria-hidden="true" />
          {(BALANCE_THRESHOLDS.CAUTION_RATIO * 100).toFixed(0)}–{(BALANCE_THRESHOLDS.OVERLOADED_RATIO * 100).toFixed(0)}% — Vượt nhẹ
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-chart-24" aria-hidden="true" />
          ≥ {(BALANCE_THRESHOLDS.OVERLOADED_RATIO * 100).toFixed(0)}% — Quá tải
        </span>
        <span className="ml-auto flex items-center gap-1.5">
          <span className="w-0.5 h-3.5 bg-outline inline-block" aria-hidden="true" />
          Đường dọc = TB
        </span>
      </div>
    </div>
  );
}

/* ── KPI summary ── */
function KpiSummary({ data }: { data: WorkloadChartData }) {
  const kpis = [
    { label: "Tổng ca", value: data.totalShifts.toString(), icon: "event_available" },
    { label: "TB / nhân sự", value: data.averageWorkload.toFixed(1), icon: "trending_flat" },
    { label: "Thấp nhất", value: data.minWorkload.toString(), icon: "arrow_downward" },
    { label: "Cao nhất", value: data.maxWorkload.toString(), icon: "arrow_upward" },
  ];
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      {kpis.map((kpi) => (
        <div
          key={kpi.label}
          className="flex items-center gap-3 bg-surface-container-low rounded-xl p-3 border border-outline-variant"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-surface-container-high">
            <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">
              {kpi.icon}
            </span>
          </div>
          <div>
            <p className="text-label-xs text-on-surface-variant">{kpi.label}</p>
            <p className="font-bold text-[20px] text-on-surface tabular-nums">{kpi.value}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ── Build chart data ── */
function buildFromPreview(schedules: AutoScheduleSummary[]): WorkloadChartData {
  const aggregates = aggregateByStaff(schedules);
  const totalShifts = aggregates.reduce((sum, s) => sum + s.total, 0);
  const totalStaff = aggregates.length;
  const avg = totalStaff > 0 ? totalShifts / totalStaff : 0;
  const maxWorkload = totalStaff > 0 ? Math.max(...aggregates.map((s) => s.total)) : 0;
  const minWorkload = totalStaff > 0 ? Math.min(...aggregates.map((s) => s.total)) : 0;

  return {
    totalShifts,
    totalStaff,
    averageWorkload: avg,
    minWorkload,
    maxWorkload,
    staffWorkloadData: aggregates.map((s) => ({
      staffId: s.staffId,
      staffName: s.staffName,
      specialty: null,
      totalShifts: s.total,
      L01: s.L01,
      L02: s.L02,
      L03: s.L03,
      L04: s.L04,
      workloadPercentage: avg > 0 ? (s.total / avg) * 100 : 0,
    })),
  };
}

function buildFromApi(res: Awaited<ReturnType<typeof api.getWorkloadChartData>>): WorkloadChartData {
  const rawStaff = res.staffWorkloadData;
  const totalShifts = rawStaff.reduce((sum, s) => sum + s.totalShifts, 0);
  const totalStaff = rawStaff.length;
  const avg = totalStaff > 0 ? totalShifts / totalStaff : 0;
  const maxWorkload = totalStaff > 0 ? Math.max(...rawStaff.map((s) => s.totalShifts)) : 0;
  const minWorkload = totalStaff > 0 ? Math.min(...rawStaff.map((s) => s.totalShifts)) : 0;

  return {
    totalShifts,
    totalStaff,
    averageWorkload: avg,
    minWorkload,
    maxWorkload,
    staffWorkloadData: rawStaff.map((s) => ({
      staffId: s.staffId,
      staffName: s.staffName,
      specialty: s.specialty ?? null,
      totalShifts: s.totalShifts,
      L01: s.L01 ?? 0,
      L02: s.L02 ?? 0,
      L03: s.L03 ?? 0,
      L04: s.L04 ?? 0,
      workloadPercentage: s.workloadPercentage,
    })),
  };
}

/* ── Main Export ── */
export interface WorkloadChartProps {
  periodId: number;
  /** Khi cung cấp, dùng in-memory preview thay vì gọi API */
  previewSchedules?: AutoScheduleSummary[];
  /** Giới hạn số dòng cho balance view. Mặc định hiện tất cả nhân sự. */
  balanceLimit?: number;
}

export function WorkloadChart({ periodId, previewSchedules, balanceLimit }: WorkloadChartProps) {
  const [chartData, setChartData] = useState<WorkloadChartData | null>(null);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>("bar");
  const [showOnlyEligible, setShowOnlyEligible] = useState(false);
  const toast = useToast();

  const hasPreview = !!previewSchedules && previewSchedules.length > 0;
  const balanceData = useMemo(
    () => (hasPreview ? topN(aggregateByStaff(previewSchedules!), balanceLimit ?? Infinity) : null),
    [hasPreview, previewSchedules, balanceLimit],
  );

  // Filter chart data to show only eligible staff
  const filteredChartData = useMemo(() => {
    if (!chartData || !showOnlyEligible) return chartData;
    return {
      ...chartData,
      staffWorkloadData: chartData.staffWorkloadData.filter(
        (s) => s.specialty && ELIGIBLE_SPECIALTIES.includes(s.specialty)
      ),
    };
  }, [chartData, showOnlyEligible]);

  const load = useCallback(async () => {
    if (hasPreview) {
      setChartData(buildFromPreview(previewSchedules!));
      return;
    }
    if (!periodId) return;
    setLoading(true);
    try {
      const res = await api.getWorkloadChartData(periodId);
      if (res) {
        setChartData(buildFromApi(res));
      }
    } catch {
      toast.error("Không thể tải dữ liệu tải công việc. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, [periodId, hasPreview, previewSchedules, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  // Auto-switch về "bar" nếu user đang ở "balance" mà preview bị gỡ
  useEffect(() => {
    if (!hasPreview && viewMode === "balance") setViewMode("bar");
  }, [hasPreview, viewMode]);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-20 rounded-lg" />
        <Skeleton className="h-48 rounded-lg" />
      </div>
    );
  }

  if (!chartData) {
    return (
      <EmptyState
        icon="bar_chart"
        title="Không có dữ liệu workload cho kỳ lịch này"
        description="Chọn kỳ lịch đã có lịch trực để xem biểu đồ tải công việc."
        size="compact"
      />
    );
  }

  const viewModes: Array<[ViewMode, string, string, boolean]> = [
    ["bar", "Theo ca", "horizontal_distribute", true],
    ["stacked", "Theo loại", "stacked_bar_chart", true],
    ["balance", "Cân bằng", "balance", hasPreview],
  ];

  return (
    <div className="space-y-4">
      {/* KPI summary */}
      <KpiSummary data={filteredChartData ?? chartData!} />

      {/* Controls */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-4 flex-wrap">
          <Legend />
          {/* Filter: Chỉ hiển thị nhân sự eligible */}
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={showOnlyEligible}
              onChange={(e) => setShowOnlyEligible(e.target.checked)}
              className="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary cursor-pointer"
            />
            <span className="text-label-sm text-on-surface-variant">
              Chỉ eligible
            </span>
            <span className="text-[11px] text-outline">(Ngoại, Nội, Sản, Nhi, Mắt, Răng)</span>
          </label>
        </div>
        <div
          role="group"
          aria-label="Chế độ hiển thị biểu đồ"
          className="flex gap-1 p-0.5 bg-surface-container-low rounded-lg"
        >
          {viewModes.map(([mode, label, icon, enabled]) => (
            <button
              key={mode}
              type="button"
              onClick={() => enabled && setViewMode(mode)}
              disabled={!enabled}
              aria-pressed={viewMode === mode}
              aria-disabled={!enabled}
              title={
                enabled
                  ? undefined
                  : "Chạy thuật toán để xem phân tích cân bằng tải"
              }
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-label-sm transition-all ${
                viewMode === mode
                  ? "bg-primary text-on-primary font-semibold shadow-sm"
                  : enabled
                    ? "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high"
                    : "text-outline opacity-50 cursor-not-allowed"
              }`}
            >
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{icon}</span>
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Chart */}
      {viewMode === "bar" && <HorizontalBarChart data={chartData} />}
      {viewMode === "stacked" && <StackedBarChart data={chartData} />}
      {viewMode === "balance" && balanceData && (
        <BalanceView {...balanceData} />
      )}

      {/* Eligibility explanation panel */}
      {chartData && (() => {
        const eligibleCount = chartData.staffWorkloadData.filter(
          (s) => s.specialty && ELIGIBLE_SPECIALTIES.includes(s.specialty)
        ).length;
        const nonEligibleCount = chartData.staffWorkloadData.length - eligibleCount;
        if (nonEligibleCount === 0) return null;
        return (
          <div
            className="flex items-start gap-3 p-4 rounded-xl bg-tertiary-container/30 border border-tertiary/30"
            role="note"
            aria-label="Giải thích phân bổ theo eligibility"
          >
            <span
              className="material-symbols-outlined text-tertiary text-[20px] shrink-0 mt-0.5"
              aria-hidden="true"
            >
              info
            </span>
            <div className="flex-1 min-w-0 text-body-sm text-on-surface">
              <p className="font-semibold text-on-surface mb-1">
                Tại sao có sự chênh lệch giữa các nhân sự?
              </p>
              <p className="text-on-surface-variant leading-relaxed">
                <strong>{eligibleCount}</strong> nhân sự thuộc 6 chuyên khoa Ngoại, Nội, Sản, Nhi, Mắt, Răng
                được phân các ca <strong>L01 (Trực 24/24)</strong>, <strong>L02 (Thông tầm)</strong>,{" "}
                <strong>L03 (PK Dịch vụ)</strong> và <strong>L04 (PK Chuyên gia)</strong>.
              </p>
              <p className="text-on-surface-variant leading-relaxed mt-1">
                <strong>{nonEligibleCount}</strong> nhân sự còn lại (Dược sĩ, KTV, hoặc chuyên khoa khác)
                chỉ eligible cho <strong>L04 (PK Chuyên gia)</strong> theo quy định bệnh viện — không thể
                nhận L01/L02/L03 vì cần bác sĩ/điều dưỡng thực sự.
              </p>
              <p className="text-on-surface-variant leading-relaxed mt-2 text-[12px]">
                💡 Để cải thiện fairness, hãy <em>tuyển thêm bác sĩ/điều dưỡng</em> cho 6 khoa eligible
                hoặc <em>mở rộng pool eligibility</em> (cần quyết định từ phía quản lý).
              </p>
            </div>
            <button
              type="button"
              onClick={() => setShowOnlyEligible((v) => !v)}
              className="shrink-0 inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-surface-container-lowest border border-outline-variant text-label-sm font-medium text-on-surface hover:bg-surface-container-low transition-colors"
              title={showOnlyEligible ? "Bỏ lọc" : "Chỉ xem nhân sự eligible"}
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                {showOnlyEligible ? "visibility_off" : "visibility"}
              </span>
              {showOnlyEligible ? "Bỏ lọc" : "Lọc eligible"}
            </button>
          </div>
        );
      })()}

      {/* Footer notes */}
      <div className="flex items-center gap-4 pt-2 border-t border-outline-variant flex-wrap" aria-label="Chú thích biểu đồ">
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-primary" aria-hidden="true" />
          <span className="text-label-xs text-on-surface-variant">Trong ngưỡng TB</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-chart-24" aria-hidden="true" />
          <span className="text-label-xs text-on-surface-variant">Cao hơn TB ≥ 30%</span>
        </div>
        <div className="flex items-center gap-1.5 ml-auto">
          <span className="w-0.5 h-4 bg-tertiary inline-block" aria-hidden="true" />
          <span className="text-label-xs text-on-surface-variant">Vạch dọc = trung bình</span>
        </div>
        {showOnlyEligible && filteredChartData && chartData && (
          <div className="w-full flex items-center justify-between text-[11px] text-tertiary">
            <span>
              Đang lọc: hiển thị {filteredChartData.staffWorkloadData.length}/{chartData.staffWorkloadData.length} nhân sự eligible
            </span>
            <span className="bg-tertiary-container text-on-tertiary-container px-2 py-0.5 rounded-full font-medium">
              Ẩn {chartData.staffWorkloadData.length - filteredChartData.staffWorkloadData.length} không eligible
            </span>
          </div>
        )}
      </div>
    </div>
  );
}