"use client";

import { Suspense, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { Skeleton } from "@/components/ui/Skeleton";
import { Badge } from "@/components/ui/Badge";
import { BackButton } from "@/components/ui/BackButton";
import { api } from "@/lib/api";
import { parseNumber } from "@/lib/number-utils";
import { useRole } from "@/hooks/useRole";
import type { SchedulePeriod, AlgorithmMetrics } from "@/types/api";
import { formatRelativeTime } from "@/lib/date";

const FeasibilityReportCard = dynamic(
  () => import("@/components/monthly-schedule/FeasibilityReportCard").then((m) => m.FeasibilityReportCard),
  { loading: () => <Skeleton className="h-64 rounded-xl" /> },
);
const BalanceBreakdownWidget = dynamic(
  () => import("@/components/auto-scheduling/BalanceBreakdownWidget").then((m) => m.BalanceBreakdownWidget),
  { loading: () => <Skeleton className="h-96 rounded-xl" /> },
);
const ConstraintReportTable = dynamic(
  () => import("@/components/auto-scheduling/ConstraintReportTable").then((m) => m.ConstraintReportTable),
  { loading: () => <Skeleton className="h-64 rounded-xl" /> },
);
const DashboardHeatmapPanel = dynamic(
  () => import("@/components/auto-scheduling/DashboardHeatmapPanel").then((m) => m.DashboardHeatmapPanel),
  { loading: () => <Skeleton className="h-96 rounded-xl" /> },
);

function PageHeaderSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-12 w-full rounded-xl" />
      <Skeleton className="h-64 w-full rounded-xl" />
    </div>
  );
}

export default function AutoSchedulingDashboardPage() {
  return (
    <Suspense fallback={<PageHeaderSkeleton />}>
      <DashboardContent />
    </Suspense>
  );
}

function DashboardContent() {
  const role = useRole();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [latestRun, setLatestRun] = useState<AlgorithmMetrics | null>(null);
  const [latestRunLoading, setLatestRunLoading] = useState(false);

  // Load periods and pick a default (first DRAFT, else first)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.getAllPeriods();
        if (cancelled) return;
        const list: SchedulePeriod[] = (res as unknown as { data: SchedulePeriod[] })?.data ?? [];
        setPeriods(list);
        const firstDraft = list.find((p) => p.status === "DRAFT");
        const defaultId = firstDraft?.id ?? list[0]?.id ?? null;
        setSelectedPeriodId(defaultId);
      } catch (err) {
        console.error("Không tải được danh sách kỳ:", err);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Fetch latest metrics for the selected period (KPIs strip)
  useEffect(() => {
    if (selectedPeriodId === null) return;
    let cancelled = false;
    setLatestRunLoading(true);
    (async () => {
      try {
        const list = await api.getMetricsByPeriod(selectedPeriodId);
        if (cancelled) return;
        // newest first
        const sorted = [...list].sort((a, b) => b.id - a.id);
        setLatestRun(sorted[0] ?? null);
      } catch (err) {
        if (!cancelled) {
          console.warn("Không tải được metrics cho period", selectedPeriodId, err);
          setLatestRun(null);
        }
      } finally {
        if (!cancelled) setLatestRunLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedPeriodId]);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;

  return (
    <div className="space-y-4">
      {/* Header */}
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-2" />

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-headline-lg font-bold text-on-surface flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-fixed">
              <span className="material-symbols-outlined text-[28px] text-primary" aria-hidden="true">
                monitoring
              </span>
            </div>
            Dashboard vận hành M07
          </h1>
          <p className="font-body-sm text-body-sm text-on-surface-variant ml-15 mt-1">
            Quan sát khả thi · cân bằng · ràng buộc · phân bố ca trực theo kỳ
          </p>
        </div>
        <Link
          href={`/auto-scheduling/history?periodId=${selectedPeriodId ?? ""}`}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-lowest border border-outline-variant font-label-md text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          <span className="material-symbols-outlined text-[16px]" aria-hidden="true">history</span>
          Lịch sử chạy
        </Link>
      </div>

      {/* Period selector + role-aware note */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 px-4 py-3 border-b border-outline-variant bg-surface-container-low">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-fixed">
            <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">
              calendar_month
            </span>
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="font-title-lg text-title-lg text-on-surface">Kỳ lịch đang quan sát</h2>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Tất cả widget bên dưới dùng chung kỳ này.
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3 px-4 py-3">
          <label htmlFor="dashboard-period-select" className="font-label-md text-label-md text-on-surface-variant">
            Chọn kỳ:
          </label>
          <div className="relative">
            <select
              id="dashboard-period-select"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(Number(e.target.value) || null)}
              className="pl-3 pr-8 py-2.5 bg-surface-container-low rounded-lg border border-transparent focus:border-primary focus:bg-surface-container-lowest focus:ring-1 focus:ring-primary focus:outline-none font-body-sm text-body-sm text-on-surface appearance-none cursor-pointer min-w-[280px]"
            >
              {periods.length === 0 ? (
                <option value="">(đang tải…)</option>
              ) : (
                periods.map((p) => (
                  <option key={p.id} value={p.id}>
                    #{p.id} · {p.periodName} ({p.status})
                  </option>
                ))
              )}
            </select>
            <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">
              expand_more
            </span>
          </div>
          {selectedPeriod && (
            <Badge tone={selectedPeriod.status === "DRAFT" ? "warning" : "neutral"}>
              {selectedPeriod.status}
            </Badge>
          )}
          <span className="ml-auto font-label-sm text-label-sm text-on-surface-variant">
            Vai trò: <span className="font-semibold">{role}</span>
          </span>
        </div>
      </div>

      {/* KPI strip from latest run */}
      <KpiStrip latestRun={latestRun} loading={latestRunLoading} />

      {/* Widgets in priority order */}
      <section aria-label="Kiểm tra tính khả thi">
        <FeasibilityReportCard periodId={selectedPeriodId} />
      </section>

      <section aria-label="Phân tích cân bằng tải">
        <BalanceBreakdownWidget periodId={selectedPeriodId} />
      </section>

      <section aria-label="Báo cáo vi phạm ràng buộc">
        <ConstraintReportTable periodId={selectedPeriodId} />
      </section>

      <section aria-label="Bản đồ nhiệt ca trực">
        <DashboardHeatmapPanel periodId={selectedPeriodId} />
      </section>

      {/* Footer tip */}
      <div className="rounded-xl border border-outline-variant bg-surface-container-low px-4 py-3 font-body-sm text-body-sm text-on-surface-variant">
        Dashboard cập nhật theo kỳ đang chọn. Sau khi chạy scheduler xong, quay lại đây để xem fairness
        đã cải thiện ở pool nào. Lịch sử metric chi tiết có ở{" "}
        <Link className="text-primary font-label-md hover:underline" href="/auto-scheduling/history">
          tab History
        </Link>
        .
      </div>
    </div>
  );
}

interface KpiStripProps {
  latestRun: AlgorithmMetrics | null;
  loading: boolean;
}

function KpiStrip({ latestRun, loading }: KpiStripProps) {
  if (loading) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
      </div>
    );
  }
  if (!latestRun) {
    return (
      <div className="rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 font-body-sm text-body-sm text-on-surface-variant">
        Kỳ này chưa có lịch sử chạy thuật toán. Hãy vào{" "}
        <Link className="text-primary font-label-md hover:underline" href="/auto-scheduling">
          Xếp lịch tự động
        </Link>{" "}
        để chạy lần đầu.
      </div>
    );
  }
  const coverage = parseNumber(latestRun.coverageRate);
  const coverageTone = coverage >= 90
    ? "success"
    : coverage >= 70
    ? "info"
    : coverage >= 50
    ? "warning"
    : "error";
  const balance = parseNumber(latestRun.balanceScore);
  const balanceTone = balance >= 75
    ? "success"
    : balance >= 50
    ? "warning"
    : "error";
  const conflictTone = (latestRun.conflictCount ?? 0) === 0 ? "success" : "error";

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <KpiTile
        // UAT-011: renamed "Coverage" → "Schedule Coverage" so it cannot be
        // confused with the "Feasibility Coverage" widget below (different metric,
        // different denominator — see tooltip for the explanation).
        label="Schedule Coverage"
        value={`${Number(latestRun.coverageRate ?? 0).toFixed(1)}%`}
        tone={coverageTone}
        icon="check_circle"
        tooltip="% slot requirement đã được scheduler gán nhân sự SAU khi chạy. KHÁC với Feasibility Coverage ở widget bên dưới (% ngày có đủ nhân sự eligible TRƯỚC khi chạy)."
      />
      <KpiTile
        label="Fairness"
        value={Number(latestRun.balanceScore ?? 0).toFixed(1)}
        tone={balanceTone}
        icon="balance"
      />
      <KpiTile
        label="Conflicts"
        value={String(latestRun.conflictCount ?? 0)}
        tone={conflictTone}
        icon="gpp_maybe"
      />
      <KpiTile
        label="Lần chạy gần nhất"
        value={formatRelativeTime(latestRun.createdAt)}
        tone={latestRun.runToken ? "info" : "neutral"}
        icon="schedule"
        caption={latestRun.runToken ? `runToken ${latestRun.runToken.slice(0, 8)}…` : latestRun.algorithmType}
      />
    </div>
  );
}

interface KpiTileProps {
  label: string;
  value: string;
  tone: "success" | "info" | "warning" | "error" | "neutral";
  icon: string;
  caption?: string;
  /**
   * UAT-011: short explanation shown on hover so first-time hospital managers
   * don't confuse "Feasibility Coverage" vs "Schedule Coverage". Tooltip text
   * should be ≤ 140 chars, action-oriented, and end with a noun phrase.
   */
  tooltip?: string;
}

function KpiTile({ label, value, tone, icon, caption, tooltip }: KpiTileProps) {
  /** Full-tile tone backgrounds — light tints from the surface system. */
  const toneBg: Record<KpiTileProps["tone"], string> = {
    success: "bg-secondary-container border-secondary",
    info: "bg-primary-fixed border-primary",
    warning: "bg-tertiary-container border-tertiary",
    error: "bg-error-container border-error",
    neutral: "bg-surface-container border-outline-variant",
  };
  const toneIconBg: Record<KpiTileProps["tone"], string> = {
    success: "bg-secondary text-on-secondary",
    info: "bg-primary text-on-primary",
    warning: "bg-tertiary text-on-tertiary",
    error: "bg-error text-on-error",
    neutral: "bg-surface-container-high text-on-surface-variant",
  };

  return (
    <div className={`rounded-xl border shadow-sm p-4 flex flex-col gap-2 ${toneBg[tone]}`}>
      <div className="flex items-center justify-between">
        <span
          className="font-label-sm text-label-sm uppercase"
          // UAT-011: surface the tooltip on both hover (mouse) and focus
          // (keyboard) so the explanation reaches both Manager and screen-reader users.
          title={tooltip}
          aria-label={tooltip ? `${label} — ${tooltip}` : undefined}
        >
          {label}
        </span>
        <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${toneIconBg[tone]}`}>
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
            {icon}
          </span>
        </span>
      </div>
      <span className="font-display-lg text-display-lg text-on-surface">{value}</span>
      {caption && (
        <span className="font-label-sm text-label-sm text-on-surface-variant font-mono truncate">
          {caption}
        </span>
      )}
    </div>
  );
}
