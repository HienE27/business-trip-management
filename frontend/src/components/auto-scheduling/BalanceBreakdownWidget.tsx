"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

export interface BalanceBreakdownWidgetProps {
  periodId: number | null;
}

const TYPE_LABELS: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK Dịch vụ",
  L04: "PK Chuyên gia",
};

const TYPE_ACCENT: Record<string, { pill: string; bar: string }> = {
  L01: { pill: "bg-red-50 text-red-800 border-red-200", bar: "bg-red-500" },
  L02: { pill: "bg-blue-50 text-blue-800 border-blue-200", bar: "bg-blue-500" },
  L03: { pill: "bg-green-50 text-green-800 border-green-200", bar: "bg-green-500" },
  L04: { pill: "bg-purple-50 text-purple-800 border-purple-200", bar: "bg-purple-500" },
};

function cvTone(cv: number): {
  barClass: string;
  badgeTone: "success" | "info" | "warning" | "error";
  label: string;
} {
  if (cv <= 0.10) {
    return { barClass: "bg-secondary", badgeTone: "success", label: "Tốt" };
  }
  if (cv <= 0.20) {
    return { barClass: "bg-primary", badgeTone: "info", label: "Khá" };
  }
  if (cv <= 0.35) {
    return { barClass: "bg-tertiary", badgeTone: "warning", label: "Trung bình" };
  }
  return { barClass: "bg-error", badgeTone: "error", label: "Kém" };
}

/**
 * M07-F12 surface for the dashboard. Renders the per-pool CV table that
 * explains why {@code overall.score} is what it is — the "why" behind the
 * single fairness number.
 *
 * <p>Wired to {@code GET /api/v1/auto-schedule/balance-breakdown/{periodId}} —
 * the same response shape consumed by {@code BalanceAnalyticsService} on the
 * backend so the values are the canonical {@code ScheduleQualityScorer}
 * outputs.
 */
export function BalanceBreakdownWidget({ periodId }: BalanceBreakdownWidgetProps) {
  const [payload, setPayload] = useState<Awaited<ReturnType<typeof api.getBalanceBreakdown>> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchBreakdown = useCallback(async () => {
    if (!periodId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await api.getBalanceBreakdown(periodId);
      setPayload(result);
    } catch (err) {
      setError(getErrorMessage(err, "Tải dữ liệu thất bại"));
    } finally {
      setLoading(false);
    }
  }, [periodId]);

  useEffect(() => {
    void fetchBreakdown();
  }, [fetchBreakdown]);

  const overall = payload?.overall;
  const overallTone = useMemo(() => {
    if (!overall) return "neutral" as const;
    const cv = overall.cv;
    if (cv <= 0.10) return "success" as const;
    if (cv <= 0.20) return "info" as const;
    if (cv <= 0.35) return "warning" as const;
    return "error" as const;
  }, [overall]);

  if (!periodId) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Chọn một kỳ lịch để xem phân tích balance.
        </p>
      </div>
    );
  }
  if (loading) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">Đang tải…</p>
      </div>
    );
  }
  if (error) {
    return (
      <div className="rounded-lg border border-error-container bg-error-container p-4">
        <p className="font-body-sm text-body-sm text-on-error-container">{error}</p>
        <Button variant="secondary" size="sm" onClick={fetchBreakdown} className="mt-2">
          Thử lại
        </Button>
      </div>
    );
  }
  if (!overall) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Kỳ này chưa có dữ liệu balance.
        </p>
      </div>
    );
  }

  const pools = payload?.pools ?? [];
  const worstPool = pools.reduce<NonNullable<typeof payload>["pools"][number] | null>(
    (acc, p) => (acc === null || p.cv > acc.cv ? p : acc),
    null,
  );

  return (
    <div className="rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-fixed">
            <span
              className="material-symbols-outlined text-[20px] text-primary"
              aria-hidden="true"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              balance
            </span>
          </div>
          <div>
            <h3 className="font-headline-md text-headline-md text-on-surface">
              Phân tích cân bằng tải
            </h3>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              {pools.length} pool · {overall.totalSchedules} ca trên {overall.totalActiveStaff} nhân sự
            </p>
          </div>
        </div>
        <Badge tone={overallTone} size="md">
          Score: {overall.score.toFixed(2)}
        </Badge>
      </div>

      {/* Overall bar */}
      <div className="px-4 py-3 border-b border-outline-variant">
        <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1 font-body-sm text-body-sm text-on-surface-variant">
          <span>
            CV tổng: <span className="font-label-md text-label-md text-on-surface">{(overall.cv * 100).toFixed(2)}%</span>
          </span>
          <span>
            Target: <span className="font-label-md text-label-md text-on-surface">{((overall.targetCv ?? 0) * 100).toFixed(0)}%</span>
          </span>
          <span>
            Worst-case: <span className="font-label-md text-label-md text-on-surface">{((overall.worstCv ?? 0) * 100).toFixed(0)}%</span>
          </span>
          {worstPool && (
            <span>
              Pool yếu nhất: <span className="font-label-md text-label-md text-on-surface">
                {TYPE_LABELS[worstPool.shiftTypeId] ?? worstPool.shiftTypeId}
                {worstPool.specialtyName ? ` - ${worstPool.specialtyName}` : ""}
              </span>
            </span>
          )}
        </div>
      </div>

      {/* Per-pool table */}
      <div className="overflow-x-auto">
        <table className="min-w-full text-left border-collapse">
          <thead className="bg-surface-container-low">
            <tr>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                Pool
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant text-right">
                Staff
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant text-right">
                Mean
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant text-right">
                StdDev
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant text-right">
                CV
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                Phân bố CV
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant text-right">
                Weight
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant text-right">
                Đóng góp
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {pools.length === 0 ? (
              <tr>
                <td
                  colSpan={8}
                  className="py-3 px-3 font-body-sm text-body-sm text-on-surface-variant"
                >
                  Chưa có dữ liệu pool.
                </td>
              </tr>
            ) : (
              pools.map((p) => {
                const accent = TYPE_ACCENT[p.shiftTypeId] ?? TYPE_ACCENT.L04;
                const tone = cvTone(p.cv);
                const label = TYPE_LABELS[p.shiftTypeId] ?? p.shiftTypeId;
                const fullLabel = p.specialtyName ? `${label} - ${p.specialtyName}` : label;
                // CV bar: scale up to worst-case = full bar so worst pool is always saturated
                const widthPct = Math.min(100, (p.cv / (overall.worstCv ?? overall.cv)) * 100);
                return (
                  <tr key={p.typeKey} className="hover:bg-surface-container-low transition-colors">
                    <td className="py-2 px-3">
                      <span
                        className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full border text-label-sm font-semibold ${accent.pill}`}
                      >
                        {fullLabel}
                      </span>
                      <p className="mt-0.5 font-label-sm text-label-sm text-on-surface-variant">
                        {p.totalAssignments} phân công · max {p.actualMaxCount}/min {p.actualMinCount}
                      </p>
                    </td>
                    <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface text-right">
                      {p.poolSize}
                    </td>
                    <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface text-right">
                      {(p.mean ?? 0).toFixed(2)}
                    </td>
                    <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface text-right">
                      {(p.stdDev ?? 0).toFixed(2)}
                    </td>
                    <td className="py-2 px-3 font-label-md text-label-md text-on-surface text-right">
                      {(p.cv * 100).toFixed(1)}%
                    </td>
                    <td className="py-2 px-3 min-w-[160px]">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 h-2 rounded-full bg-surface-variant overflow-hidden">
                          <div
                            className={`h-2 rounded-full ${tone.barClass} transition-[width] duration-300`}
                            style={{ width: `${widthPct}%` }}
                            aria-hidden="true"
                          />
                        </div>
                        <Badge tone={tone.badgeTone} size="sm">
                          {tone.label}
                        </Badge>
                      </div>
                    </td>
                    <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface text-right">
                      {(p.weight ?? 0).toFixed(2)}
                    </td>
                    <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface text-right">
                      {((p.contributionToOverall ?? 0) * 100).toFixed(1)}%
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Recommendations */}
      {payload && payload.recommendations && payload.recommendations.length > 0 && (
        <div className="px-4 py-3 border-t border-outline-variant bg-surface-container-low">
          <h4 className="font-title-lg text-title-lg text-on-surface mb-2 flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">
              lightbulb
            </span>
            Gợi ý cải thiện
          </h4>
          <ul className="space-y-2">
            {payload.recommendations.slice(0, 5).map((rec, idx) => {
              const tone =
                rec.severity === "high"
                  ? "border-error-container bg-error-container/40 text-on-error-container"
                  : rec.severity === "medium"
                  ? "border-tertiary-container bg-tertiary-fixed text-on-tertiary-fixed-variant"
                  : "border-outline-variant bg-surface-container-lowest text-on-surface";
              return (
                <li
                  key={idx}
                  className={`rounded-lg border px-3 py-2 ${tone}`}
                >
                  <p className="font-label-md text-label-md">{rec.pool ?? rec.shiftTypeId}</p>
                  <p className="font-body-sm text-body-sm mt-0.5">{rec.issue ?? rec.message}</p>
                  {(rec.suggestions ?? []).length > 0 && (
                    <ul className="mt-1 ml-4 list-disc space-y-0.5 font-body-sm text-body-sm">
                      {(rec.suggestions ?? []).map((s: string, i: number) => (
                        <li key={i}>{s}</li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}

export default BalanceBreakdownWidget;
