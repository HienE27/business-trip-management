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
 *   - within ~60% of the per-staff cap  → green  (balanced)
 *   - > cap                           → amber   (caution)
 *   - >= 1.5× cap                     → red     (overloaded)
 */

import { useId, useMemo } from 'react';
import type { AutoScheduleSummary } from '@/types/api';

export interface AlgorithmBalanceChartProps {
  schedules: AutoScheduleSummary[];
  /** Staff caps keyed by staffId. Falls back to 6 if absent. */
  staffCaps?: Record<number, number>;
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
  cap: number;
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

export function AlgorithmBalanceChart({
  schedules,
  staffCaps = {},
  title = 'Cân bằng tải — phương án thuật toán',
  subtitle = 'M07-F09 · sắp xếp đề xuất trước khi xác nhận',
  limit = 12,
}: AlgorithmBalanceChartProps) {
  const uid = useId();

  const rows = useMemo<StaffAggregate[]>(() => {
    const map = new Map<number, StaffAggregate>();

    for (const s of schedules) {
      if (!map.has(s.staffId)) {
        map.set(s.staffId, {
          staffId: s.staffId,
          staffName: s.staffName,
          L01: 0, L02: 0, L03: 0, L04: 0, total: 0,
          cap: staffCaps[s.staffId] ?? 6,
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

    for (const agg of map.values()) {
      agg.ratio = agg.cap > 0 ? agg.total / agg.cap : 0;
      agg.status = classify(agg.ratio);
    }

    return Array.from(map.values())
      .sort((a, b) => b.ratio - a.ratio)
      .slice(0, limit);
  }, [schedules, staffCaps, limit]);

  if (rows.length === 0) {
    return (
      <div
        role="status"
        data-testid="algo-balance-empty"
        className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-10 gap-2 text-on-surface-variant"
      >
        <span className="material-symbols-outlined text-4xl">bar_chart</span>
        <p className="text-[13px]">Chưa có dữ liệu phân bổ.</p>
      </div>
    );
  }

  const maxRatio = Math.max(...rows.map((r) => r.ratio), 1);

  return (
    <div
      role="img"
      aria-label={title}
      data-testid="algo-balance-chart"
      className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm space-y-3"
    >
      <header className="flex items-baseline justify-between gap-2">
        <h3 className="text-[16px] font-semibold text-on-surface">{title}</h3>
        <span className="text-[12px] text-on-surface-variant">{subtitle}</span>
      </header>

      <ul className="space-y-2.5" role="list">
        {rows.map((row, i) => {
          const barWidth = maxRatio > 0 ? Math.min(100, (row.ratio / maxRatio) * 100) : 0;
          const capPos = maxRatio > 0 ? (row.cap / maxRatio) * 100 : 0;
          const color = barColor(row.status);
          const pct = row.ratio * 100;
          return (
            <li
              key={row.staffId}
              data-testid="algo-balance-row"
              className="grid grid-cols-[1fr_48px] items-center gap-3"
            >
              <div className="space-y-0.5">
                <div className="flex items-center justify-between gap-2">
                  <span
                    title={row.staffName}
                    className="truncate text-[13px] font-medium text-on-surface"
                  >
                    {row.staffName}
                  </span>
                  <span className="shrink-0 text-right text-[12px] font-semibold tabular-nums text-on-surface">
                    {row.total}
                    <span className="text-outline"> / {row.cap}</span>
                  </span>
                </div>
                {/* SVG bar */}
                <div className="relative h-4 bg-surface-variant rounded-full overflow-hidden">
                  {/* Cap marker */}
                  <span
                    aria-hidden
                    className="absolute top-0 bottom-0 w-px bg-outline-variant z-10"
                    style={{ left: `${Math.min(100, capPos)}%` }}
                  />
                  <svg
                    width="100%"
                    height={16}
                    viewBox="0 0 100 16"
                    preserveAspectRatio="xMinYMid meet"
                    role="presentation"
                    aria-hidden
                    data-testid="algo-bar"
                  >
                    <rect
                      x={0}
                      y={2}
                      width={Math.min(100, barWidth)}
                      height={12}
                      rx={4}
                      fill={color}
                    />
                    <title>{`${row.staffName}: ${row.total} ca / giới hạn ${row.cap} (${pct.toFixed(0)}%)`}</title>
                  </svg>
                </div>
              </div>

              {/* Badge */}
              <span
                aria-label={`${row.status === 'overloaded' ? 'Quá tải' : row.status === 'caution' ? 'Vượt nhẹ' : 'Cân bằng'}`}
                data-testid="algo-balance-badge"
                className={`shrink-0 inline-flex items-center justify-center w-10 h-6 rounded-full text-[10px] font-bold ${
                  row.status === 'overloaded'
                    ? 'bg-error-container text-on-error-container'
                    : row.status === 'caution'
                    ? 'bg-tertiary-fixed text-on-tertiary-container'
                    : 'bg-secondary-container text-on-secondary-container'
                }`}
              >
                {row.status === 'overloaded' ? '⚠' : row.status === 'caution' ? '~' : '✓'}
              </span>
            </li>
          );
        })}
      </ul>

      <footer className="flex flex-wrap items-center gap-3 pt-2 text-[12px] text-on-surface-variant border-t border-outline-variant">
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-4 rounded-sm bg-[var(--color-chart-tt,#10b981)]" />
          Cân bằng
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-4 rounded-sm bg-[var(--color-chart-cg,#8b5cf6)]" />
          Vượt nhẹ
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-4 rounded-sm bg-[var(--color-chart-24,#ef4444)]" />
          Quá tải
        </span>
        <span className="ml-auto">Cột dọc = ngưỡng cho phép mỗi nhân sự</span>
      </footer>
    </div>
  );
}
