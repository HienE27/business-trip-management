"use client";

/**
 * UnassignedReportCard — standalone component for M07-F06:
 * "Báo cáo ngày chưa phân công được."
 *
 * Fetches from GET /api/v1/auto-schedule/unassigned/{periodId}
 * and renders a warning card listing days with insufficient staff.
 *
 * Used in:
 *   - auto-scheduling/page.tsx (before and after preview)
 *   - reports/unassigned/page.tsx (future standalone route)
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/date";
import { Skeleton } from "@/components/ui/Skeleton";

interface UnassignedDayItem {
  workDate: string;
  dayOfWeek: string;
  shiftTypeId: string;
  shiftTypeName: string;
  requiredStaffCount: number;
  assignedStaffCount: number;
  missingCount: number;
}

interface UnassignedDayReport {
  totalUnassignedDays: number;
  unassignedDays: UnassignedDayItem[];
}

export interface UnassignedReportCardProps {
  periodId: number | null;
  /** Override the title text. */
  title?: string;
  /** Show a refresh button. Defaults to true. */
  showRefresh?: boolean;
  /** Max height for the scrollable list. Defaults to "max-h-48". */
  maxHeight?: string;
}

export function UnassignedReportCard({
  periodId,
  title,
  showRefresh = true,
  maxHeight = "max-h-48",
}: UnassignedReportCardProps) {
  const [report, setReport] = useState<UnassignedDayReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const ignoreRef = useRef(false);

  const load = useCallback(() => {
    if (!periodId) return;
    ignoreRef.current = false;
    setLoading(true);
    setMessage(null);
    api.getUnassignedDaysReport(periodId)
      .then((data) => { if (!ignoreRef.current && data) setReport(data); })
      .catch(() => {
        if (!ignoreRef.current) {
          setReport(null);
          setMessage("Không thể tải báo cáo ngày chưa phân công.");
        }
      })
      .finally(() => { if (!ignoreRef.current) setLoading(false); });
  }, [periodId]);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <Skeleton className="h-24 rounded-xl" />;

  if (message) {
    return (
      <div className="rounded-xl border border-error-container bg-error-container/10 p-5 flex items-center gap-3">
        <span className="material-symbols-outlined text-error text-[22px]">error</span>
        <p className="text-body-sm text-error">{message}</p>
      </div>
    );
  }

  if (!report || report.totalUnassignedDays == null || report.totalUnassignedDays === 0) {
    return (
      <div className="rounded-xl border border-secondary-container bg-secondary-container/10 p-5 flex items-center gap-3">
        <span className="material-symbols-outlined text-secondary text-[22px]">check_circle</span>
        <p className="text-body-sm text-on-surface">
          Tất cả các ca trong kỳ đã được phân công đủ.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-error-container bg-error-container/10 p-5">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-error text-[20px]">warning</span>
          <h3 className="font-label-md font-bold text-error">
            {title ?? `${report.totalUnassignedDays} ngày chưa phân đủ nhân sự`}
          </h3>
        </div>
        {showRefresh && (
          <button
            type="button"
            onClick={() => void load()}
            className="text-label-sm text-primary hover:underline"
          >
            Làm mới
          </button>
        )}
      </div>
      <div className={`space-y-2 overflow-y-auto ${maxHeight}`}>
        {(report.unassignedDays ?? []).map((day, i) => (
          <div
            key={i}
            className="flex items-center gap-3 p-2 rounded-lg bg-surface-container-lowest border border-outline-variant/30"
          >
            <span className="text-label-sm text-on-surface font-semibold w-20 shrink-0">
              {formatDate(day.workDate)}
            </span>
            <span className="text-label-sm text-on-surface-variant">{day.dayOfWeek}</span>
            <span className="text-label-sm text-primary px-2 py-0.5 bg-primary-fixed rounded shrink-0">
              {day.shiftTypeName}
            </span>
            <span className="text-label-sm text-on-surface ml-auto">
              <span className="font-bold text-error">{day.missingCount}</span>
              <span className="text-on-surface-variant">/{day.requiredStaffCount}</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
