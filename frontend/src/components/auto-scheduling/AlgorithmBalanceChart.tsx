'use client';

/**
 * AlgorithmBalanceChart — lightweight SVG bar chart for the auto-scheduling
 * preview, driven by §M07-F09 of the spec:
 *   "Biểu đồ số ngày trực / số ngày làm của từng nhân sự
 *    trong tháng để quản lý xem xét mức độ phân bổ."
 *
 * Unlike WorkloadBalanceChart which fetches saved schedule data,
 * this component takes the in-memory `previewResult.schedules` array
 * so it reflects the *algorithm's own projection* — not the
 * already-committed data.
 *
 * Colour coding:
 *   - ratio ≤ 1.0 (within average)  → green  (balanced)
 *   - ratio > 1.0 && ≤ 1.5          → amber   (caution)
 *   - ratio >= 1.5                   → red     (overloaded)
 */

import { useMemo } from 'react';
import type { AutoScheduleSummary } from '@/types/api';

export interface AlgorithmBalanceChartProps {
  schedules: AutoScheduleSummary[];
  /** Caption used in the heading. */
  title?: string;
  /** Caption used in the footer. */
  subtitle?: string;
  limit?: number;
}

interface StaffAggregate {
  staffId: number;
  staffName: string;
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  total: number;
  avg: number;
  ratio: number;
  status: 'balanced' | 'caution' | 'overloaded';
}

function classify(ratio: number): StaffAggregate['status'] {
  if (ratio >= 1.5) return 'overloaded';
  if (ratio > 1) return 'caution';
  return 'balanced';
}

function barColor(status: StaffAggregate['status']): string {
  switch (status) {
    case 'overloaded': return 'var(--color-chart-24, #ef4444)';
    case 'caution':    return 'var(--color-chart-cg, #8b5cf6)';
    case 'balanced':   return 'var(--color-chart-tt, #10b981)';
  }
}

function StatusBadge({ status }: { status: StaffAggregate['status'] }) {
  const config = {
    overloaded: { icon: 'warning', label: 'Quá tải', bg: 'bg-error-container', text: 'text-on-error-container' },
    caution: { icon: 'horizontal_rule', label: 'Vượt nhẹ', bg: 'bg-tertiary-fixed', text: 'text-on-tertiary-fixed-variant' },
    balanced: { icon: 'check_circle', label: 'Cân bằng', bg: 'bg-secondary-container', text: 'text-on-secondary-container' },
  }[status];

  return (
    <span
      aria-label={config.label}
      className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-[11px] font-bold ${config.bg} ${config.text}`}
    >
      <span className="material-symbols-outlined text-[12px]" aria-hidden="true">{config.icon}</span>
      {config.label}
    </span>
  );
}

export function AlgorithmBalanceChart({
  schedules,
  title = 'Cân bằng tải',
  subtitle = 'Sắp xếp đề xuất trước khi xác nhận',
  limit = 12,
}: AlgorithmBalanceChartProps) {

  const rows = useMemo<StaffAggregate[]>(() => {
    const map = new Map<number, StaffAggregate>();

    for (const s of schedules) {
      if (!map.has(s.staffId)) {
        map.set(s.staffId, {
          staffId: s.staffId,
          staffName: s.staffName,
          L01: 0, L02: 0, L03: 0, L04: 0, total: 0,
          avg: 0,
          ratio: 0,
          status: 'balanced',
        });
      }
      const agg = map.get(s.staffId)!;
      switch (s.shiftTypeId) {
        case 'L01': agg.L01++; break;
        case 'L02': agg.L02++; break;
        case 'L03': agg.L03++; break;
        case 'L04': agg.L04++; break;
      }
      agg.total++;
    }

    const staffCount = map.size;
    const grandTotal = Array.from(map.values()).reduce((sum, a) => sum + a.total, 0);
    const avg = staffCount > 0 ? grandTotal / staffCount : 0;

    for (const agg of map.values()) {
      agg.avg = avg;
      agg.ratio = avg > 0 ? agg.total / avg : 0;
      agg.status = classify(agg.ratio);
    }

    return Array.from(map.values())
      .sort((a, b) => b.ratio - a.ratio)
      .slice(0, limit);
  }, [schedules, limit]);

  if (rows.length === 0) {
    return (
      <div
        role="status"
        data-testid="algo-balance-empty"
        className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-12 gap-3 text-on-surface-variant"
      >
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-surface-container-low">
          <span className="material-symbols-outlined text-[36px]">bar_chart</span>
        </div>
        <p className="text-label-sm">Chưa có dữ liệu phân bổ</p>
      </div>
    );
  }

  // maxRatio normalises the bar widths; the "avg" line is always at 100%
  const maxRatio = Math.max(...rows.map((r) => r.ratio), 1);

  return (
    <div
      role="img"
      aria-label={title}
      data-testid="algo-balance-chart"
      className="rounded-xl bg-surface-container-lowest overflow-hidden space-y-0"
    >
      {/* Header */}
      <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-fixed">
            <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">balance</span>
          </div>
          <div>
            <h3 className="text-title-sm font-semibold text-on-surface">{title}</h3>
            <p className="text-label-xs text-on-surface-variant">{subtitle}</p>
          </div>
        </div>
        <span className="text-label-xs text-on-surface-variant bg-surface-container-high px-2 py-1 rounded-full">
          {rows.length} nhân sự
        </span>
      </div>

      {/* Content */}
      <div className="p-4 space-y-3">
        <ul className="space-y-2" role="list">
          {rows.map((row) => {
            const barWidth = maxRatio > 0 ? Math.min(100, (row.ratio / maxRatio) * 100) : 0;
            const color = barColor(row.status);
            const pct = row.ratio * 100;

            return (
              <li
                key={row.staffId}
                data-testid="algo-balance-row"
                className="flex items-center gap-3 p-2 rounded-lg hover:bg-surface-container-low transition-colors"
              >
                {/* Staff name + count */}
                <div className="w-40 shrink-0">
                  <p className="text-label-sm font-medium text-on-surface truncate" title={row.staffName}>
                    {row.staffName}
                  </p>
                  <p className="text-label-xs text-on-surface-variant">
                    {row.total} ca / TB {row.avg.toFixed(1)}
                  </p>
                </div>

                {/* Progress bar */}
                <div className="flex-1 space-y-1">
                  <div className="relative h-5 bg-surface-variant rounded-full overflow-hidden">
                    {/* Average marker — always at 100% (represents avg) */}
                    <span
                      aria-hidden
                      className="absolute top-0 bottom-0 w-px bg-outline z-10"
                      style={{ left: `${Math.min(100, (1 / maxRatio) * 100)}%` }}
                    />
                    <svg
                      width="100%"
                      height={20}
                      viewBox="0 0 100 20"
                      preserveAspectRatio="xMinYMid meet"
                      role="presentation"
                      aria-hidden
                      data-testid="algo-bar"
                      className="block"
                    >
                      <rect
                        x={0}
                        y={2}
                        width={Math.min(100, barWidth)}
                        height={16}
                        rx={4}
                        fill={color}
                      />
                      <title>{`${row.staffName}: ${row.total} ca / TB ${row.avg.toFixed(1)} (${pct.toFixed(0)}%)`}</title>
                    </svg>
                  </div>
                </div>

                {/* Percentage */}
                <div className="w-14 shrink-0 text-right">
                  <span className="text-label-sm font-bold tabular-nums text-on-surface">
                    {pct.toFixed(0)}%
                  </span>
                </div>

                {/* Status badge */}
                <div className="w-24 shrink-0">
                  <StatusBadge status={row.status} />
                </div>
              </li>
            );
          })}
        </ul>

        {/* Legend */}
        <div className="flex flex-wrap items-center gap-4 pt-3 border-t border-outline-variant text-label-xs text-on-surface-variant">
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-[var(--color-chart-tt,#10b981)]" aria-hidden="true" />
            Cân bằng
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-[var(--color-chart-cg,#8b5cf6)]" aria-hidden="true" />
            Vượt nhẹ
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-[var(--color-chart-24,#ef4444)]" aria-hidden="true" />
            Quá tải
          </span>
          <span className="ml-auto flex items-center gap-1.5">
            <span className="w-px h-3 bg-outline" aria-hidden="true" />
            Đường TB
          </span>
        </div>
      </div>
    </div>
  );
}
