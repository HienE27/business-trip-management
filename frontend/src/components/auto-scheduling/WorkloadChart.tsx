"use client";

import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/ToastProvider";
import { api } from "@/lib/api";
import type { AutoScheduleSummary } from "@/types/api";

/* ── WorkloadChart ──
 *
 * Dual-mode (bar / stacked) workload visualization for staff scheduling.
 * Uses CSS custom properties for colors — update globals.css to change palette.
 * Accessible: keyboard navigation for toggle, aria-label on charts.
 */

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
  periodId: number;
  periodName: string;
  startDate: string;
  endDate: string;
  totalSchedules: number;
  totalStaff: number;
  averageWorkload: number;
  minWorkload: number;
  maxWorkload: number;
  staffWorkloadData: WorkloadStaffData[];
}

const CHART_COLORS = {
  L01: "var(--color-chart-24)",
  L02: "var(--color-chart-tt)",
  L03: "var(--color-chart-dv)",
  L04: "var(--color-chart-cg)",
} as const;

const SHIFT_LABELS = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK Dịch vụ",
  L04: "PK Chuyên gia",
} as const;

/* ── Tooltip overlay ── */
function Tooltip({
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
      className="fixed z-50 pointer-events-none"
      style={{ left: x + 12, top: y - 8, transform: "translateY(-100%)" }}
    >
      <div className="bg-on-surface text-surface rounded-lg px-3 py-2 shadow-xl whitespace-nowrap border border-outline-variant/30">
        {content}
      </div>
    </div>
  );
}

/* ── Legend ── */
function Legend() {
  return (
    <div className="flex items-center gap-4 flex-wrap" aria-label="Chú thích màu biểu đồ">
      {(Object.entries(CHART_COLORS) as [keyof typeof CHART_COLORS, string][]).map(([key, fill]) => (
        <div key={key} className="flex items-center gap-1.5">
          <span
            className="w-3 h-3 rounded-sm inline-block shrink-0"
            style={{ backgroundColor: fill }}
            aria-hidden="true"
          />
          <span className="text-label-sm text-on-surface-variant">{SHIFT_LABELS[key]}</span>
        </div>
      ))}
    </div>
  );
}

/* ── Horizontal Bar Chart (per-staff total) ── */
function HorizontalBarChart({ data }: { data: WorkloadChartData }) {
  const [tooltip, setTooltip] = useState({ visible: false, x: 0, y: 0, content: null as React.ReactNode });
  const maxShift = Math.max(...data.staffWorkloadData.map((s) => s.totalShifts), 1);
  const avgShift = data.averageWorkload;

  const labelWidth = 160;
  const barAreaWidth = 280;
  const valueWidth = 40;

  return (
    <>
      <div className="overflow-x-auto" role="img" aria-label="Biểu đồ tải công việc theo nhân sự">
        <div style={{ minWidth: labelWidth + barAreaWidth + valueWidth + 40 }}>
          {/* Scale markers */}
          <div className="flex items-center gap-3 mb-2" aria-hidden="true">
            <div style={{ width: labelWidth }} />
            <div className="flex-1 flex gap-0.5">
              {[0.25, 0.5, 0.75, 1].map((pct) => (
                <div
                  key={pct}
                  className="text-label-xs text-outline text-right"
                  style={{ width: (barAreaWidth * pct) / 4 - 4 }}
                >
                  {Math.round(maxShift * pct)}
                </div>
              ))}
            </div>
            <div style={{ width: valueWidth }} />
          </div>

          {/* Rows */}
          <div className="space-y-2">
            {data.staffWorkloadData.map((staff) => {
              const pct = (staff.totalShifts / maxShift) * 100;
              const isOverAvg = avgShift > 0 && staff.totalShifts > avgShift * 1.3;
              return (
                <div key={staff.staffId} className="flex items-center gap-3">
                  <div
                    className="shrink-0 font-label-sm text-label-sm text-on-surface truncate"
                    style={{ width: labelWidth }}
                    title={staff.staffName}
                  >
                    {staff.staffName}
                  </div>

                  <div
                    className="relative flex-1 rounded-full h-8 bg-surface-container-low overflow-hidden cursor-default"
                    onMouseEnter={(e) =>
                      setTooltip({
                        visible: true,
                        x: e.clientX,
                        y: e.clientY,
                        content: (
                          <span>
                            <strong>{staff.staffName}</strong>
                            <br />
                            {staff.totalShifts} ca — {staff.totalShifts > avgShift ? "cao hơn TB" : "dưới TB"}
                          </span>
                        ),
                      })
                    }
                    onMouseMove={(e) =>
                      setTooltip((t) =>
                        t.visible
                          ? { ...t, x: e.clientX, y: e.clientY }
                          : t
                      )
                    }
                    onMouseLeave={() => setTooltip((t) => ({ ...t, visible: false }))}
                  >
                    <div
                      className="h-full rounded-full transition-all duration-500 ease-out"
                      style={{
                        width: `${Math.max(pct, 3)}%`,
                        backgroundColor: isOverAvg ? "var(--color-error)" : "var(--color-primary)",
                      }}
                    />

                    {/* Average line */}
                    {avgShift > 0 && (
                      <div
                        className="absolute top-0 bottom-0 w-0.5 bg-tertiary opacity-70"
                        style={{ left: `${(avgShift / maxShift) * 100}%` }}
                        title={`Trung bình: ${avgShift.toFixed(1)}`}
                      />
                    )}
                  </div>

                  <div
                    className="font-label-sm text-label-sm text-right shrink-0 tabular-nums"
                    style={{ width: valueWidth }}
                  >
                    <span className={isOverAvg ? "text-error font-bold" : "text-on-surface"}>
                      {staff.totalShifts}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <Tooltip {...tooltip} />
    </>
  );
}

/* ── Stacked Bar Chart (breakdown by shift type) ── */
function StackedBarChart({ data }: { data: WorkloadChartData }) {
  const [tooltip, setTooltip] = useState({ visible: false, x: 0, y: 0, content: null as React.ReactNode });
  const maxShift = Math.max(...data.staffWorkloadData.map((s) => s.totalShifts), 1);

  return (
    <>
      <div className="overflow-x-auto" role="img" aria-label="Biểu đồ phân bổ ca theo loại lịch">
        <div style={{ minWidth: 480 }}>
          <div className="space-y-2">
            {data.staffWorkloadData.map((staff) => {
              const parts = (
                [
                  { key: "L01", count: staff.L01, color: CHART_COLORS.L01 },
                  { key: "L02", count: staff.L02, color: CHART_COLORS.L02 },
                  { key: "L03", count: staff.L03, color: CHART_COLORS.L03 },
                  { key: "L04", count: staff.L04, color: CHART_COLORS.L04 },
                ] as { key: keyof typeof CHART_COLORS; count: number; color: string }[]
              ).filter((p) => p.count > 0);

              return (
                <div key={staff.staffId} className="flex items-center gap-3">
                  <div
                    className="shrink-0 font-label-sm text-label-sm text-on-surface truncate w-40"
                    title={staff.staffName}
                  >
                    {staff.staffName}
                  </div>

                  <div
                    className="flex-1 flex rounded-full h-8 bg-surface-container-low overflow-hidden"
                    onMouseEnter={(e) =>
                      setTooltip({
                        visible: true,
                        x: e.clientX,
                        y: e.clientY,
                        content: (
                          <span>
                            <strong>{staff.staffName}</strong>
                            <br />
                            {staff.L01 > 0 && <>{SHIFT_LABELS.L01}: {staff.L01}<br /></>}
                            {staff.L02 > 0 && <>{SHIFT_LABELS.L02}: {staff.L02}<br /></>}
                            {staff.L03 > 0 && <>{SHIFT_LABELS.L03}: {staff.L03}<br /></>}
                            {staff.L04 > 0 && <>{SHIFT_LABELS.L04}: {staff.L04}</>}
                          </span>
                        ),
                      })
                    }
                    onMouseMove={(e) =>
                      setTooltip((t) =>
                        t.visible ? { ...t, x: e.clientX, y: e.clientY } : t
                      )
                    }
                    onMouseLeave={() => setTooltip((t) => ({ ...t, visible: false }))}
                  >
                    {parts.map((part) => (
                      <div
                        key={part.key}
                        className="h-full flex items-center justify-center overflow-hidden"
                        style={{
                          width: `${(part.count / maxShift) * 100}%`,
                          backgroundColor: part.color,
                          minWidth: part.count > 0 ? 4 : 0,
                        }}
                      >
                        {part.count > 0 && (
                          <span className="text-label-sm font-bold text-[var(--color-on-primary)] opacity-90 whitespace-nowrap px-1 tabular-nums">
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
        </div>
      </div>

      <Tooltip {...tooltip} />
    </>
  );
}

/* ── Staff aggregate builder ── */
function buildStaffAggregates(schedules: AutoScheduleSummary[]): WorkloadStaffData[] {
  const map = new Map<number, WorkloadStaffData>();
  for (const s of schedules) {
    if (!map.has(s.staffId)) {
      map.set(s.staffId, {
        staffId: s.staffId,
        staffName: s.staffName,
        specialty: null,
        totalShifts: 0,
        L01: 0, L02: 0, L03: 0, L04: 0,
        workloadPercentage: 0,
      });
    }
    const agg = map.get(s.staffId)!;
    switch (s.shiftTypeId) {
      case "L01": agg.L01++; break;
      case "L02": agg.L02++; break;
      case "L03": agg.L03++; break;
      case "L04": agg.L04++; break;
    }
    agg.totalShifts++;
  }
  return Array.from(map.values());
}

/* ── Main Export ── */
interface WorkloadChartProps {
  periodId: number;
  /** When provided, WorkloadChart shows this preview data instead of fetching saved DB data. */
  previewSchedules?: AutoScheduleSummary[];
}

export function WorkloadChart({ periodId, previewSchedules }: WorkloadChartProps) {
  const [chartData, setChartData] = useState<WorkloadChartData | null>(null);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<"bar" | "stacked">("bar");
  const toast = useToast();

  const load = useCallback(async () => {
    if (previewSchedules && previewSchedules.length > 0) {
      // Compute chart data from preview schedules (in-memory — no API call)
      const rawStaff = buildStaffAggregates(previewSchedules);
      const totalShifts = rawStaff.reduce((sum, s) => sum + s.totalShifts, 0);
      const totalStaff = rawStaff.length;
      const avg = totalStaff > 0
        ? rawStaff.reduce((sum, s) => sum + (s.totalShifts / (totalShifts / totalStaff || 1) * 100), 0) / totalStaff
        : 0;
      const maxW = rawStaff.length > 0 ? Math.max(...rawStaff.map((s) => s.totalShifts)) : 0;
      const minW = rawStaff.length > 0 ? Math.min(...rawStaff.map((s) => s.totalShifts)) : 0;
      setChartData({
        periodId,
        periodName: "Bản xem trước",
        startDate: "",
        endDate: "",
        totalSchedules: totalShifts,
        totalStaff,
        averageWorkload: avg,
        minWorkload: minW,
        maxWorkload: maxW,
        staffWorkloadData: rawStaff,
      });
      return;
    }
    if (!periodId) return;
    setLoading(true);
    try {
      const res = await api.getWorkloadChartData(periodId);
      if (res) {
        const rawStaff = res.staffWorkloadData;
        const totalShifts = rawStaff.reduce((sum, s) => sum + s.totalShifts, 0);
        const totalStaff = rawStaff.length;
        const avg =
          totalStaff > 0
            ? rawStaff.reduce((sum, s) => sum + (s.workloadPercentage / totalStaff), 0)
            : 0;
        const maxW = rawStaff.reduce((max, s) => Math.max(max, s.workloadPercentage), 0);
        const minW =
          totalStaff > 0 ? Math.min(...rawStaff.map((s) => s.workloadPercentage)) : 0;

        setChartData({
          periodId,
          periodName: "",
          startDate: "",
          endDate: "",
          totalSchedules: totalShifts,
          totalStaff,
          averageWorkload: avg,
          minWorkload: minW,
          maxWorkload: maxW,
          staffWorkloadData: rawStaff.map((s) => ({
            staffId: s.staffId,
            staffName: s.staffName,
            specialty: s.specialty,
            totalShifts: s.totalShifts,
            L01: s.L01 ?? 0,
            L02: s.L02 ?? 0,
            L03: s.L03 ?? 0,
            L04: s.L04 ?? 0,
            workloadPercentage: s.workloadPercentage,
          })),
        });
      }
    } catch {
      toast.error("Không thể tải dữ liệu tải công việc. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, [periodId, previewSchedules, toast]);

  useEffect(() => {
    void load();
  }, [load]);

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

  return (
    <div className="space-y-4">
      {/* KPI summary */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: "Tổng ca", value: chartData.totalSchedules, icon: "event_available" },
          { label: "Trung bình", value: chartData.averageWorkload.toFixed(1), icon: "trending_flat" },
          { label: "Thấp nhất", value: `${chartData.minWorkload.toFixed(1)}%`, icon: "arrow_downward" },
          { label: "Cao nhất", value: `${chartData.maxWorkload.toFixed(1)}%`, icon: "arrow_upward" },
        ].map((kpi) => (
          <div key={kpi.label} className="flex items-center gap-3 bg-surface-container-low rounded-xl p-3 border border-outline-variant">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-surface-container-high">
              <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">{kpi.icon}</span>
            </div>
            <div>
              <p className="font-label-xs text-on-surface-variant">{kpi.label}</p>
              <p className="font-bold text-[20px] text-on-surface tabular-nums">{kpi.value}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Controls */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <Legend />
        <div
          role="group"
          aria-label="Chế độ hiển thị biểu đồ"
          className="flex gap-1 p-0.5 bg-surface-container-low rounded-lg"
        >
          {(
            [
              ["bar", "Theo ca", "horizontal_distribute"],
              ["stacked", "Theo loại", "stacked_bar_chart"],
            ] as const
          ).map(([mode, label, icon]) => (
            <button
              key={mode}
              type="button"
              onClick={() => setViewMode(mode)}
              aria-pressed={viewMode === mode}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-label-sm transition-all ${
                viewMode === mode
                  ? "bg-primary text-on-primary font-semibold shadow-sm"
                  : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high"
              }`}
            >
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{icon}</span>
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Chart */}
      {viewMode === "bar" ? (
        <HorizontalBarChart data={chartData} />
      ) : (
        <StackedBarChart data={chartData} />
      )}

      {/* Chart notes */}
      <div className="flex items-center gap-4 pt-2 border-t border-outline-variant" aria-label="Chú thích biểu đồ">
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-primary" aria-hidden="true" />
          <span className="text-label-xs text-outline">Cao hơn TB ≥ 30%</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-0.5 h-4 bg-tertiary inline-block" aria-hidden="true" />
          <span className="text-label-xs text-outline">Vạch dọc = trung bình</span>
        </div>
      </div>
    </div>
  );
}
