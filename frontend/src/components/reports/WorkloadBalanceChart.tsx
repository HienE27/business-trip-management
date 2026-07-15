'use client';

/**
 * WorkloadBalanceChart — pure SVG bar chart, no external chart lib.
 *
 * §M07-F09 of the spec asks for "Biểu đồ số ngày trực / số ngày làm của
 * từng nhân sự trong tháng để quản lý xem xét mức độ phân bổ".
 *
 * One horizontal bar per staff member, ordered by count desc so the
 * most loaded staff sits on top and the lightest at the bottom — easy
 * to scan for imbalance at a glance.
 *
 * Bars are colour-coded against the per-view cap:
 *   - <= cap           → primary (in range)
 *   - > cap            → tertiary (caution)
 *   - >= 1.5 * cap     → error (overloaded)
 *
 * Chart values are extracted via {@link pickShiftCount} so the chart
 * stays in sync with the table filter (ALL / L01 / L02 / L03 / L04).
 */

import { useMemo } from 'react';
import {
  pickCap,
  pickShiftCount,
  type StaffWorkloadRow,
  type WorkloadView,
} from './workloadUtils';

export interface WorkloadBalanceChartDatum {
  staffId: number;
  staffName: string;
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  total: number;
  maxShiftsPerMonth?: number | null;
}

export interface WorkloadBalanceChartProps {
  data: WorkloadBalanceChartDatum[];
  view: WorkloadView;
  /** Optional override for the cap when the spec says "view X" instead of cap. */
  maxBarWidth?: number;
  /** Cap the rendered list size to keep the chart readable. */
  limit?: number;
  emptyLabel?: string;
}

function barColor(value: number, cap: number): string {
  if (cap <= 0) return 'var(--color-chart-24, #ef4444)';
  const ratio = value / cap;
  if (ratio >= 1.5) return 'var(--color-chart-24, #ef4444)';
  if (ratio > 1) return 'var(--color-chart-cg, #8b5cf6)';
  return 'var(--color-chart-tt, #10b981)';
}

export function WorkloadBalanceChart({
  data,
  view,
  maxBarWidth = 360,
  limit = 12,
  emptyLabel = 'Chưa có dữ liệu tải cho kỳ này.',
}: WorkloadBalanceChartProps) {

  const sorted = useMemo(() => {
    const rows: StaffWorkloadRow[] = data.map((d) => ({
      staff: {
        id: d.staffId,
        fullName: d.staffName,
        maxShiftsPerMonth: d.maxShiftsPerMonth ?? null,
      },
      L01: d.L01,
      L02: d.L02,
      L03: d.L03,
      L04: d.L04,
      total: d.total,
    }));
    return rows
      .map((row) => ({
        row,
        value: pickShiftCount(row, view),
        cap: pickCap(row, view),
      }))
      .sort((a, b) => b.value - a.value)
      .slice(0, limit);
  }, [data, view, limit]);

  if (sorted.length === 0) {
    return (
      <div
        role="status"
        data-testid="balance-chart-empty"
        className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-12 gap-2 text-on-surface-variant"
      >
        <span className="material-symbols-outlined text-4xl">bar_chart</span>
        <p className="text-[13px]">{emptyLabel}</p>
      </div>
    );
  }

  const maxValue = Math.max(1, ...sorted.map((s) => s.value));
  const scale = maxBarWidth / Math.max(1, maxValue);

  return (
    <div
      role="img"
      aria-label={`Biểu đồ cân bằng tải ${VIEW_LABEL[view]}`}
      data-testid="balance-chart"
      className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm space-y-3"
    >
      <header className="flex items-baseline justify-between gap-2">
        <h3 className="text-[16px] font-semibold text-on-surface">
          Cân bằng tải — {VIEW_LABEL[view]}
        </h3>
        <span className="text-[12px] text-on-surface-variant">
          M07-F09 · {sorted.length} nhân sự
        </span>
      </header>

      <ul className="space-y-2.5">
        {sorted.map(({ row, value, cap }, index) => {
          const widthPx = Math.max(2, value * scale);
          const capPx = Math.max(2, cap * scale);
          const color = barColor(value, cap);
          return (
            <li
              key={row.staff.id}
              data-testid="balance-row"
              className="grid grid-cols-[160px_1fr_64px] items-center gap-3"
            >
              <span
                title={row.staff.fullName}
                className="truncate text-[13px] font-medium text-on-surface"
              >
                {row.staff.fullName}
              </span>
              <div className="relative h-5">
                {/* Capacity line — the manager's reference point. */}
                <span
                  aria-hidden
                  className="pointer-events-none absolute top-1/2 -translate-y-1/2 h-5 w-px bg-outline"
                  style={{ left: `${Math.min(maxBarWidth, capPx)}px` }}
                />
                <svg
                  width="100%"
                  height={20}
                  viewBox={`0 0 ${Math.max(maxBarWidth, widthPx)} 20`}
                  preserveAspectRatio="xMinYMid meet"
                  role="presentation"
                  aria-hidden
                  data-testid="balance-bar"
                >
                  <rect
                    x={0}
                    y={3}
                    width={Math.min(maxBarWidth, widthPx)}
                    height={14}
                    rx={4}
                    fill={color}
                    opacity={0.85}
                  />
                  <title>{`${row.staff.fullName}: ${value} ca (giới hạn ${cap})`}</title>
                </svg>
              </div>
              <span
                className="text-right text-[12px] font-semibold text-on-surface tabular-nums"
                data-testid="balance-value"
              >
                {value} <span className="text-outline">/ {cap}</span>
              </span>
              <span className="sr-only">Hạng {index + 1}</span>
            </li>
          );
        })}
      </ul>

      <footer className="flex flex-wrap items-center gap-3 pt-2 text-[12px] text-on-surface-variant">
        <LegendSwatch color="var(--color-chart-tt, #10b981)" label="Trong giới hạn" />
        <LegendSwatch color="var(--color-chart-cg, #8b5cf6)" label="Vượt nhẹ" />
        <LegendSwatch color="var(--color-chart-24, #ef4444)" label="Quá tải" />
        <span className="ml-auto">Cột gạch dọc = ngưỡng cho phép</span>
      </footer>
    </div>
  );
}

const VIEW_LABEL: Record<WorkloadView, string> = {
  ALL: 'tổng',
  L01: 'trực 24/24',
  L02: 'thông tầm',
  L03: 'phòng khám dịch vụ',
  L04: 'phòng khám chuyên gia',
};

function LegendSwatch({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        aria-hidden
        className="inline-block h-2.5 w-4 rounded-sm"
        style={{ backgroundColor: color }}
      />
      {label}
    </span>
  );
}