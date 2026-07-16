"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

export type HeatmapMetric = "load" | "weekend" | "consecutive";

export interface HeatmapPayload {
  periodId: number;
  metric: HeatmapMetric;
  periodDays: number;
  startDate?: string;
  endDate?: string;
  maxRaw: number;
  rows: Array<{
    staffId: number;
    displayName: string;
    rawTotal: number;
    intensities: number[];
  }>;
}

export interface HeatmapWidgetProps {
  periodId: number | null;
  metric?: HeatmapMetric;
}

const METRIC_LABEL: Record<HeatmapMetric, string> = {
  load: "Tổng số ca",
  weekend: "Ca cuối tuần",
  consecutive: "Chuỗi ngày liên tục",
};

/**
 * Phase 2.3 — ASCII-style workload heatmap widget.
 *
 * <p>Renders an intensity bar per staff with 1 character per day. Uses
 * existing UI tokens (primary, error, secondary) to colour high-load cells.
 */
export function HeatmapWidget({ periodId, metric = "load" }: HeatmapWidgetProps) {
  const [payload, setPayload] = useState<HeatmapPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchHeat = useCallback(async () => {
    if (!periodId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<{ data: HeatmapPayload }>(
        `/scheduling/heatmap/${periodId}?metric=${metric}`,
      );
      setPayload(res.data);
    } catch (err) {
      setError(getErrorMessage(err, "Tải dữ liệu thất bại"));
    } finally {
      setLoading(false);
    }
  }, [periodId, metric]);

  useEffect(() => {
    void fetchHeat();
  }, [fetchHeat]);

  const maxRaw = payload?.maxRaw ?? 0;

  const buckets = useMemo(() => {
    return bucketise(maxRaw);
  }, [maxRaw]);

  if (!periodId) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Chọn một kỳ lịch để xem bản đồ nhiệt.
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
        <Button variant="secondary" size="sm" onClick={fetchHeat}>Thử lại</Button>
      </div>
    );
  }

  const rows = payload?.rows ?? [];

  return (
    <div className="space-y-3 rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-headline-md text-headline-md text-on-surface">
          Bản đồ nhiệt — {METRIC_LABEL[metric]}
        </h3>
        <div className="flex items-center gap-2">
          <Badge tone="neutral">{rows.length} nhân sự</Badge>
          <Badge tone="neutral">{payload?.periodDays ?? 0} ngày</Badge>
          <Badge tone="info">Max: {Math.round(maxRaw)}</Badge>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full text-left border-collapse">
          <thead className="bg-surface-container-low">
            <tr>
              <th className="sticky left-0 z-10 bg-surface-container-low py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                Nhân sự
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                Tổng
              </th>
              <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                Phân bố
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {rows.length === 0 ? (
              <tr>
                <td colSpan={3} className="py-4 px-3 font-body-sm text-body-sm text-on-surface-variant">
                  Không có dữ liệu.
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={row.staffId} className="hover:bg-surface-container-low">
                  <td className="sticky left-0 z-10 bg-surface-container-lowest py-2 px-3 font-body-sm text-body-sm text-on-surface">
                    <span className="font-label-md text-label-md">{row.displayName}</span>
                    <span className="ml-2 font-body-sm text-body-sm text-on-surface-variant">
                      #{row.staffId}
                    </span>
                  </td>
                  <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface">
                    {row.rawTotal}
                  </td>
                  <td className="py-2 px-3">
                    <div className="flex flex-wrap gap-0.5">
                      {row.intensities.map((v, idx) => (
                        <span
                          key={idx}
                          className={`inline-block h-3 w-2 rounded-sm ${bucketClass(v, buckets)}`}
                          aria-hidden="true"
                        />
                      ))}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center gap-2 font-label-sm text-label-sm text-on-surface-variant">
        <span>Thấp</span>
        {buckets.map((b, idx) => (
          <span
            key={idx}
            className={`inline-block h-3 w-6 rounded-sm ${bucketClass(b.threshold, buckets)}`}
            aria-hidden="true"
          />
        ))}
        <span>Cao</span>
      </div>
    </div>
  );
}

function bucketise(max: number): Array<{ threshold: number; className: string }> {
  const high = Math.max(max, 1);
  return [
    { threshold: 0, className: "bg-surface-container-high" },
    { threshold: high * 0.25, className: "bg-primary-fixed" },
    { threshold: high * 0.5, className: "bg-secondary-container" },
    { threshold: high * 0.75, className: "bg-tertiary-container" },
    { threshold: high, className: "bg-error-container" },
  ];
}

function bucketClass(value: number, buckets: Array<{ threshold: number; className: string }>): string {
  // Pick the lowest bucket whose threshold >= value
  let chosen = buckets[0].className;
  for (const b of buckets) {
    if (value >= b.threshold - 1e-6) {
      chosen = b.className;
    }
  }
  return chosen;
}

export default HeatmapWidget;