"use client";

import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { api } from "@/lib/api";

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

const SHIFT_COLORS = {
  L01: { fill: "#ef4444", label: "Trực 24/24" },
  L02: { fill: "#10b981", label: "Thông tầm" },
  L03: { fill: "#f59e0b", label: "PK Dịch vụ" },
  L04: { fill: "#8b5cf6", label: "PK Chuyên gia" },
};

function Legend() {
  return (
    <div className="flex items-center gap-4 flex-wrap">
      {Object.entries(SHIFT_COLORS).map(([key, { fill, label }]) => (
        <div key={key} className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm inline-block" style={{ backgroundColor: fill }} />
          <span className="text-label-sm text-on-surface-variant">{label}</span>
        </div>
      ))}
    </div>
  );
}

interface HorizontalBarChartProps {
  data: WorkloadChartData;
}

function HorizontalBarChart({ data }: HorizontalBarChartProps) {
  const maxShift = Math.max(...data.staffWorkloadData.map((s) => s.totalShifts), 1);
  const labelWidth = 160;
  const barAreaWidth = 280;
  const valueWidth = 40;
  const rowH = 32;

  return (
    <div className="overflow-x-auto">
      <div style={{ minWidth: labelWidth + barAreaWidth + valueWidth + 40 }}>
        {/* Header */}
        <div className="flex items-center gap-3 mb-2">
          <div className="font-label-sm text-label-sm text-on-surface-variant" style={{ width: labelWidth }}>
            Nhân sự
          </div>
          <div className="flex-1 flex gap-0.5">
            {[0.25, 0.5, 0.75, 1].map((pct) => (
              <div key={pct} className="text-label-xs text-outline text-right" style={{ width: (barAreaWidth * pct) / 4 - 4 }}>
                {Math.round(maxShift * pct)}
              </div>
            ))}
          </div>
          <div className="font-label-sm text-label-sm text-on-surface-variant text-right" style={{ width: valueWidth }}>
            Ca
          </div>
        </div>

        {/* Bars */}
        <div className="space-y-1.5">
          {data.staffWorkloadData.map((staff) => {
            const pct = (staff.totalShifts / maxShift) * 100;
            const isOverAvg = data.averageWorkload > 0 && staff.totalShifts > data.averageWorkload * 1.3;
            return (
              <div key={staff.staffId} className="flex items-center gap-3">
                <div className="shrink-0 font-label-sm text-label-sm text-on-surface truncate" style={{ width: labelWidth }} title={staff.staffName}>
                  {staff.staffName}
                </div>
                <div className="relative flex-1 rounded-full h-7 bg-surface-container-low overflow-hidden" style={{ height: rowH }}>
                  <div
                    className="h-full rounded-full transition-all"
                    style={{
                      width: `${Math.max(pct, 3)}%`,
                      backgroundColor: isOverAvg ? "#ba1a1a" : "#004ac6",
                    }}
                  />
                  {/* Avg line */}
                  {data.averageWorkload > 0 && (
                    <div
                      className="absolute top-0 bottom-0 w-0.5 bg-tertiary opacity-70"
                      style={{ left: `${(data.averageWorkload / maxShift) * 100}%` }}
                      title={`Trung bình: ${data.averageWorkload.toFixed(1)}`}
                    />
                  )}
                </div>
                <div className="font-label-sm text-label-sm text-right shrink-0" style={{ width: valueWidth }}>
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
  );
}

interface StackedBarChartProps {
  data: WorkloadChartData;
}

function StackedBarChart({ data }: StackedBarChartProps) {
  const maxShift = Math.max(...data.staffWorkloadData.map((s) => s.totalShifts), 1);
  const rowH = 32;

  return (
    <div className="overflow-x-auto">
      <div style={{ minWidth: 480 }}>
        <div className="space-y-1.5">
          {data.staffWorkloadData.map((staff) => {
            const parts = [
              { key: "L01", count: staff.L01, color: SHIFT_COLORS.L01.fill },
              { key: "L02", count: staff.L02, color: SHIFT_COLORS.L02.fill },
              { key: "L03", count: staff.L03, color: SHIFT_COLORS.L03.fill },
              { key: "L04", count: staff.L04, color: SHIFT_COLORS.L04.fill },
            ].filter((p) => p.count > 0);

            return (
              <div key={staff.staffId} className="flex items-center gap-3">
                <div className="shrink-0 font-label-sm text-label-sm text-on-surface truncate w-40" title={staff.staffName}>
                  {staff.staffName}
                </div>
                <div className="flex-1 flex rounded-full h-7 bg-surface-container-low overflow-hidden" style={{ height: rowH }}>
                  {parts.map((part) => (
                    <div
                      key={part.key}
                      className="h-full flex items-center justify-center overflow-hidden"
                      style={{
                        width: `${(part.count / maxShift) * 100}%`,
                        backgroundColor: part.color,
                        minWidth: part.count > 0 ? 4 : 0,
                      }}
                      title={`${part.key}: ${part.count} ca`}
                    >
                      {part.count > 0 && (
                          <span className="text-label-sm font-bold text-white opacity-90 whitespace-nowrap px-1">
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
  );
}

interface WorkloadChartProps {
  periodId: number;
}

export function WorkloadChart({ periodId }: WorkloadChartProps) {
  const [chartData, setChartData] = useState<WorkloadChartData | null>(null);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<"bar" | "stacked">("bar");

  const load = useCallback(async () => {
    if (!periodId) return;
    setLoading(true);
    try {
      const res = await api.getWorkloadChartData(periodId);
      if (res) {
        const rawStaff = res.staffWorkloadData;
        const totalShifts = rawStaff.reduce((sum, s) => sum + s.totalShifts, 0);
        const totalStaff = rawStaff.length;
        const avg = totalStaff > 0 ? rawStaff.reduce((sum, s) => sum + s.workloadPercentage, 0) / totalStaff : 0;
        const maxW = rawStaff.reduce((max, s) => Math.max(max, s.workloadPercentage), 0);
        const minW = totalStaff > 0 ? Math.min(...rawStaff.map(s => s.workloadPercentage)) : 0;
        setChartData({
          periodId: periodId,
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
    } catch { /* silent */ }
    finally { setLoading(false); }
  }, [periodId]);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <Skeleton className="h-48 rounded-lg" />;

  if (!chartData) {
    return (
      <EmptyState
        icon="bar_chart"
        title="Không có dữ liệu workload cho kỳ lịch này"
        description="Chọn kỳ lịch đã có lịch trực để xem biểu đồ tải công việc."
      />
    );
  }

  return (
    <div className="space-y-4">
      {/* Summary row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="bg-surface-container-lowest rounded-lg p-3 border border-outline-variant">
          <p className="font-label-sm text-label-sm text-on-surface-variant">Tổng ca</p>
          <p className="font-headline-lg text-headline-lg text-on-surface font-bold mt-0.5">{chartData.totalSchedules}</p>
        </div>
        <div className="bg-surface-container-lowest rounded-lg p-3 border border-outline-variant">
          <p className="font-label-sm text-label-sm text-on-surface-variant">Trung bình</p>
          <p className="font-headline-lg text-headline-lg text-on-surface font-bold mt-0.5">{chartData.averageWorkload.toFixed(1)}</p>
        </div>
        <div className="bg-surface-container-lowest rounded-lg p-3 border border-outline-variant">
          <p className="font-label-sm text-label-sm text-on-surface-variant">Thấp nhất</p>
          <p className="font-headline-lg text-headline-lg text-secondary font-bold mt-0.5">{chartData.minWorkload.toFixed(1)}%</p>
        </div>
        <div className="bg-surface-container-lowest rounded-lg p-3 border border-outline-variant">
          <p className="font-label-sm text-label-sm text-on-surface-variant">Cao nhất</p>
          <p className="font-headline-lg text-headline-lg text-error font-bold mt-0.5">{chartData.maxWorkload.toFixed(1)}%</p>
        </div>
      </div>

      {/* View mode toggle */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <Legend />
        <div className="flex gap-1 p-0.5 bg-surface-container-low rounded-lg">
          {([["bar", "Theo ca"], ["stacked", "Theo loại"]] as const).map(([mode, label]) => (
            <button
              key={mode}
              type="button"
              onClick={() => setViewMode(mode)}
              className={`px-3 py-1.5 rounded-md text-label-sm transition-colors ${
                viewMode === mode
                  ? "bg-primary text-on-primary font-semibold"
                  : "text-on-surface-variant hover:text-on-surface"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Charts */}
      {viewMode === "bar" ? (
        <HorizontalBarChart data={chartData} />
      ) : (
        <StackedBarChart data={chartData} />
      )}

      {/* Legend note */}
      <div className="flex items-center gap-4 pt-1">
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-full bg-primary" />
          <span className="text-label-xs text-outline">Cao hơn TB ≥ 30%</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-0.5 h-4 bg-tertiary inline-block" />
          <span className="text-label-xs text-outline">Vạch dọc = trung bình</span>
        </div>
      </div>
    </div>
  );
}
