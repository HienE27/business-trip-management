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
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-100">
              <span className="material-symbols-outlined text-[28px] text-blue-800" aria-hidden="true">
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
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100">
            <span className="material-symbols-outlined text-[18px] text-blue-800" aria-hidden="true">
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
              className="pl-3 pr-8 py-2.5 bg-surface-container-low rounded-lg border border-transparent focus:border-blue-300 focus:bg-surface-container-lowest focus:ring-1 focus:ring-blue-300 focus:outline-none font-body-sm text-body-sm text-on-surface appearance-none cursor-pointer min-w-[280px]"
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

      {/* KPI strip */}
      {/* BUGFIX (dashboard live-vs-cache drift): the strip below pulls its
          primary "Schedule Coverage" value from the LIVE /auto-schedule/coverage
          endpoint so it reflects what is actually in the DB right now. The
          previous behaviour read algorithm_metrics.coverage_rate which can be
          stale by hours when successive apply runs overwrite each other or when
          a transaction rolled back. We still show the cached value (runToken +
          relative time) but explicitly mark it as "lần chạy gần nhất". */}
      <KpiStripLatest
        latestRun={latestRun}
        loading={latestRunLoading}
        periodId={selectedPeriodId}
      />

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
        <Link className="text-blue-800 font-label-md hover:underline" href="/auto-scheduling/history">
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
        <Link className="text-blue-800 font-label-md hover:underline" href="/auto-scheduling">
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

/**
 * BUGFIX (dashboard live coverage): KPI strip that sources Schedule Coverage
 * from the live /auto-schedule/coverage/{periodId} endpoint (matches DB row
 * count) instead of the cached algorithm_metrics.coverage_rate. Adds an extra
 * "Live from DB" KPI showing the per-shift-type breakdown so the user can see
 * which shift type is understaffed.
 */
interface KpiStripLatestProps {
  latestRun: AlgorithmMetrics | null;
  loading: boolean;
  periodId: number | null;
}

interface LiveCoverageByShiftType {
  shiftTypeId: string;
  shiftTypeName: string;
  requiredCapacity: number;
  assignedCount: number;
  shortfall: number;
  coverageRate: number;
}

interface LiveCoverage {
  periodId: number;
  totalSchedules: number;
  totalRequiredCapacity: number;
  coverageRate: number;
  byShiftType: Record<string, LiveCoverageByShiftType>;
  byDay: Record<string, unknown>;
  distinctDaysWithSchedules: number;
  totalPeriodDays: number;
  computedAt: string;
}

function KpiStripLatest({ latestRun, loading, periodId }: KpiStripLatestProps) {
  const [live, setLive] = useState<LiveCoverage | null>(null);
  const [liveLoading, setLiveLoading] = useState(false);

  useEffect(() => {
    if (periodId === null) {
      setLive(null);
      return;
    }
    let cancelled = false;
    setLiveLoading(true);
    (async () => {
      try {
        const res = (await api.getLiveCoverage(periodId)) as LiveCoverage;
        if (!cancelled) setLive(res);
      } catch (e) {
        if (!cancelled) {
          setLive(null);
        }
      } finally {
        if (!cancelled) setLiveLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [periodId]);

  if (loading && !live) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
      </div>
    );
  }

  const liveCoverage = live?.coverageRate ?? latestRun?.coverageRate ?? 0;
  const liveTone = liveCoverage >= 90 ? "success" : liveCoverage >= 70 ? "info" : liveCoverage >= 50 ? "warning" : "error";
  const balance = parseNumber(latestRun?.balanceScore ?? 0);
  const balanceTone = balance >= 75 ? "success" : balance >= 50 ? "warning" : "error";

  const breakdownEntries = live
    ? Object.values(live.byShiftType).sort((a, b) =>
        a.shiftTypeId.localeCompare(b.shiftTypeId)
      )
    : [];

  return (
    <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
      <KpiTile
        // BUGFIX (UX): this label now reads "Coverage (live)" to make it
        // obvious that the value comes from the DB snapshot at page load, not
        // from the cached algorithm_metrics row.
        label="Coverage (live DB)"
        value={`${Number(liveCoverage).toFixed(1)}%`}
        tone={liveTone}
        icon="check_circle"
        tooltip="Tỷ lệ slot requirement đã có ca trong DB. Tính trực tiếp từ bảng schedule + shift_requirement; không phụ thuộc cache algorithm_metrics. Cập nhật mỗi khi mở Dashboard."
        caption={
          live
            ? `${live.totalSchedules}/${live.totalRequiredCapacity} ca`
            : liveLoading
            ? "đang tải…"
            : "—"
        }
      />
      <KpiTile
        label="Fairness (lần chạy gần nhất)"
        value={latestRun ? Number(latestRun.balanceScore ?? 0).toFixed(1) : "—"}
        tone={balanceTone}
        icon="balance"
        caption={latestRun?.algorithmType ?? ""}
      />
      <KpiTile
        label="Conflicts"
        value={String(latestRun?.conflictCount ?? 0)}
        tone={(latestRun?.conflictCount ?? 0) === 0 ? "success" : "error"}
        icon="gpp_maybe"
        caption="từ lần chạy gần nhất"
      />
      {/* BUGFIX (UX): new card that surfaces the per-shift-type breakdown
          inline so the user can see WHICH shift type is understaffed instead
          of staring at one misleading low percentage. */}
      <KpiTile
        label="Ngày có lịch"
        value={
          live
            ? `${live.distinctDaysWithSchedules}/${live.totalPeriodDays}`
            : "—"
        }
        tone={
          live && live.distinctDaysWithSchedules >= live.totalPeriodDays
            ? "success"
            : "warning"
        }
        icon="calendar_month"
        tooltip="Số ngày trong kỳ có ít nhất 1 schedule được lưu trong DB."
      />
      <KpiTile
        label="Lần chạy gần nhất"
        value={latestRun ? formatRelativeTime(latestRun.createdAt) : "—"}
        tone={latestRun?.runToken ? "info" : "neutral"}
        icon="schedule"
        caption={
          latestRun?.runToken
            ? `runToken ${latestRun.runToken.slice(0, 8)}…`
            : latestRun?.algorithmType ?? ""
        }
      />
      {breakdownEntries.length > 0 ? (
        <div className="col-span-2 lg:col-span-5 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="font-label-md text-label-md text-on-surface">
              Phân bổ ca theo loại lịch (live DB)
            </h3>
            <span className="font-body-sm text-body-sm text-on-surface-variant">
              {live ? `${live.totalSchedules}/${live.totalRequiredCapacity} ca tổng` : ""}
            </span>
          </div>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {breakdownEntries.map((s) => {
              const pct = s.requiredCapacity > 0
                ? Math.round((s.assignedCount / s.requiredCapacity) * 100)
                : 0;
              const tone =
                pct >= 90 ? "success" : pct >= 70 ? "info" : pct >= 50 ? "warning" : "error";
              return (
                <div
                  key={s.shiftTypeId}
                  className="rounded-lg border border-outline-variant bg-surface-container-low p-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-label-md text-label-md text-on-surface">
                      {s.shiftTypeId}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 font-label-sm text-label-sm ${
                        tone === "success"
                          ? "bg-emerald-100 text-emerald-800 border border-emerald-300"
                          : tone === "info"
                          ? "bg-blue-100 text-blue-800"
                          : tone === "warning"
                          ? "bg-amber-100 text-amber-800 border border-amber-300"
                          : "bg-red-100 text-red-800 border border-red-300"
                      }`}
                    >
                      {pct}%
                    </span>
                  </div>
                  <p className="mt-1 font-body-sm text-body-sm text-on-surface-variant">
                    {s.shiftTypeName}
                  </p>
                  <div className="mt-2 flex items-baseline gap-2">
                    <span className="font-headline-md text-headline-md text-on-surface">
                      {s.assignedCount}
                    </span>
                    <span className="font-body-sm text-body-sm text-on-surface-variant">
                      / {s.requiredCapacity} cần
                    </span>
                  </div>
                  {s.shortfall > 0 ? (
                    <p className="mt-1 font-body-sm text-body-sm text-red-800">
                      Thiếu {s.shortfall} ca
                    </p>
                  ) : (
                    <p className="mt-1 font-body-sm text-body-sm text-emerald-800">
                      Đủ ca
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
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
    success: "bg-emerald-100 border-emerald-300",
    info: "bg-blue-100 border-primary",
    warning: "bg-amber-100 text-amber-800 border border-amber-300",
    error: "bg-red-100 text-red-800 border border-red-300",
    neutral: "bg-surface-container border-outline-variant",
  };
  const toneIconBg: Record<KpiTileProps["tone"], string> = {
    success: "bg-emerald-100 text-emerald-800",
    info: "bg-blue-100 text-blue-800",
    warning: "bg-amber-100 text-amber-800",
    error: "bg-red-100 text-red-800",
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
