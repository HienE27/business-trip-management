"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { BackButton } from "@/components/ui/BackButton";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type {
  SandboxSession,
  SandboxTimeline,
  SandboxPromotionDiff,
  SandboxStatus,
} from "@/types/api";
import { ScoreChart } from "../ScoreChart";
import { MoveStatistics } from "../MoveStatistics";
import { ConstraintImprovement } from "../ConstraintImprovement";
import { BeforeAfterTable } from "../BeforeAfterTable";
import { DeltaCards } from "../DeltaCards";

/**
 * v11.1.2 Compare Dashboard - Results Page
 *
 * Displays simulation results from a sandbox session:
 * 1. Executive Summary - Key metrics at a glance
 * 2. Before/After Table - Detailed comparison
 * 3. Delta Cards - Visual improvement indicators
 * 4. Score Chart - Score progression over iterations
 * 5. Move Statistics - Move type breakdown
 * 6. Constraint Improvement - Constraint violation changes
 */
export default function CompareResultsPage() {
  const params = useParams();
  const router = useRouter();
  const sessionKey = params.sessionKey as string;

  const [session, setSession] = useState<SandboxSession | null>(null);
  const [timeline, setTimeline] = useState<SandboxTimeline | null>(null);
  const [diff, setDiff] = useState<SandboxPromotionDiff | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!sessionKey) return;

    setLoading(true);
    setError(null);

    try {
      const [sessionData, timelineData, diffData] = await Promise.all([
        api.getSandboxByKey(sessionKey),
        api.getSandboxTimeline(sessionKey),
        api.getSandboxDiff(sessionKey),
      ]);

      setSession(sessionData as SandboxSession | null);
      setTimeline(timelineData as SandboxTimeline);
      setDiff(diffData as SandboxPromotionDiff);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [sessionKey]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handlePromote = async () => {
    if (!confirm("Bạn có chắc muốn áp dụng kết quả này vào lịch thực?")) return;

    try {
      await api.promoteSandbox(sessionKey);
      alert("Đã áp dụng thành công!");
      router.push("/auto-scheduling");
    } catch (err) {
      alert("Lỗi: " + getErrorMessage(err, "Có lỗi xảy ra"));
    }
  };

  if (loading) {
    return (
      <div className="p-margin-desktop space-y-6">
        <Skeleton className="h-12 w-64" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-96 rounded-xl" />
      </div>
    );
  }

  if (error || !session) {
    return (
      <div className="p-margin-desktop">
        <BackButton href="/digital-twin/compare" />
        <div className="mt-6 p-6 bg-error-container rounded-lg text-center">
          <p className="text-on-error-container">Không tìm thấy phiên mô phỏng</p>
          {error && <p className="text-label-sm mt-2 text-on-error-container/70">{error}</p>}
        </div>
      </div>
    );
  }

  const statusConfig: Record<SandboxStatus, { label: string; tone: "success" | "warning" | "error" | "info" }> = {
    CREATED: { label: "Đã tạo", tone: "info" },
    CLONING: { label: "Đang sao chép", tone: "info" },
    READY: { label: "Sẵn sàng", tone: "info" },
    RUNNING: { label: "Đang chạy", tone: "info" },
    PAUSED: { label: "Tạm dừng", tone: "warning" },
    COMPLETED: { label: "Hoàn thành", tone: "success" },
    FAILED: { label: "Thất bại", tone: "error" },
    PROMOTED: { label: "Đã áp dụng", tone: "success" },
    CANCELLED: { label: "Đã hủy", tone: "warning" },
    EXPIRED: { label: "Đã hết hạn", tone: "error" },
    DELETED: { label: "Đã xóa", tone: "error" },
  };

  const status = statusConfig[session.status];
  const coverageDelta = (session.coverageRate ?? 0) - (session.initialScore ?? 0);

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <BackButton href="/digital-twin/compare" />
            <h1 className="font-display-lg text-display-lg text-on-surface">{session.name}</h1>
            <Badge tone={status.tone}>{status.label}</Badge>
          </div>
          <p className="text-body-sm text-on-surface-variant">
            Phiên mô phỏng • Kỳ #{session.sourcePeriodId} • {new Date(session.createdAt).toLocaleString("vi-VN")}
          </p>
        </div>

        {session.status === "COMPLETED" && (
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => router.push(`/digital-twin/replay?session=${sessionKey}`)}>
              <span className="material-symbols-outlined text-[18px]">replay</span>
              Xem lại
            </Button>
            <Button variant="primary" onClick={handlePromote}>
              <span className="material-symbols-outlined text-[18px]">check_circle</span>
              Áp dụng
            </Button>
          </div>
        )}
      </div>

      {/* 1. Executive Summary */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">Kết quả mô phỏng</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-lg p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-label-sm text-on-surface-variant">Coverage</span>
              <span className="material-symbols-outlined text-primary bg-primary-fixed p-1.5 rounded-md text-[20px]">
                verified_user
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface">
                {session.coverageRate?.toFixed(1) ?? "—"}%
              </span>
              {coverageDelta !== 0 && (
                <span className={`text-label-sm font-semibold ${coverageDelta >= 0 ? "text-secondary" : "text-error"}`}>
                  {coverageDelta >= 0 ? "+" : ""}{coverageDelta.toFixed(1)}%
                </span>
              )}
            </div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-lg p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-label-sm text-on-surface-variant">Fairness (CV)</span>
              <span className="material-symbols-outlined text-secondary bg-secondary-container p-1.5 rounded-md text-[20px]">
                balance
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface">
                {session.fairnessCv?.toFixed(3) ?? "—"}
              </span>
              <span className="text-label-sm text-on-surface-variant">Thấp = Tốt</span>
            </div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-lg p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-label-sm text-on-surface-variant">Violations</span>
              <span className="material-symbols-outlined text-error bg-error-container p-1.5 rounded-md text-[20px]">
                warning
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className={`font-display-lg text-display-lg ${session.violations === 0 ? "text-secondary" : "text-error"}`}>
                {session.violations ?? 0}
              </span>
              <span className="text-label-sm text-on-surface-variant">Xung đột</span>
            </div>
          </div>

          <div className="bg-surface-container-lowest border border-outline-variant rounded-lg p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-label-sm text-on-surface-variant">Runtime</span>
              <span className="material-symbols-outlined text-on-surface-variant bg-surface-container-high p-1.5 rounded-md text-[20px]">
                timer
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface">
                {session.runtimeSeconds ?? 0}s
              </span>
              <span className="text-label-sm text-on-surface-variant">{session.iterations ?? 0} iterations</span>
            </div>
          </div>
        </div>
      </section>

      {/* 2. Before/After Table */}
      {diff && (
        <section>
          <h2 className="font-headline-md text-headline-md text-on-surface mb-4">Trước vs Sau</h2>
          <BeforeAfterTable
            before={{
              coverage: session.initialScore ?? 0,
              fairness: 0,
              violations: diff.totalChanges ?? 0,
              changes: 0,
            }}
            after={{
              coverage: session.coverageRate ?? 0,
              fairness: session.fairnessCv ?? 0,
              violations: session.violations ?? 0,
              changes: diff.totalChanges ?? 0,
            }}
          />
        </section>
      )}

      {/* 3. Delta Cards */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">Cải thiện</h2>
        <DeltaCards
          coverageDelta={coverageDelta}
          fairnessDelta={-(session.fairnessCv ?? 0) * 100}
          violationsDelta={-(session.violations ?? 0)}
          changes={diff?.totalChanges ?? 0}
        />
      </section>

      {/* 4. Score Chart */}
      {timeline && timeline.iterations && timeline.iterations.length > 0 && (
        <section>
          <h2 className="font-headline-md text-headline-md text-on-surface mb-4">Điểm số theo Iteration</h2>
          <ScoreChart iterations={timeline.iterations} />
        </section>
      )}

      {/* 5. Move Statistics */}
      {timeline && timeline.iterations && timeline.iterations.length > 0 && (
        <section>
          <h2 className="font-headline-md text-headline-md text-on-surface mb-4">Thống kê Move</h2>
          <MoveStatistics iterations={timeline.iterations} />
        </section>
      )}

      {/* 6. Constraint Improvement */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">Cải thiện Constraint</h2>
        <ConstraintImprovement diff={diff} />
      </section>

      {/* Promotion Preview */}
      {diff && (diff.totalChanges ?? 0) > 0 && (
        <section className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
          <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Thay đổi sẽ được áp dụng</h3>
          <div className="grid grid-cols-3 gap-4 text-center">
            <div className="bg-secondary-container rounded-lg p-4">
              <div className="text-display-lg text-secondary font-bold">{(diff.added ?? diff.addedSchedules ?? []).length}</div>
              <div className="text-label-sm text-on-secondary-container">Thêm mới</div>
            </div>
            <div className="bg-primary-fixed rounded-lg p-4">
              <div className="text-display-lg text-primary font-bold">{(diff.modified ?? []).length}</div>
              <div className="text-label-sm text-on-primary-fixed-variant">Thay đổi</div>
            </div>
            <div className="bg-tertiary-fixed rounded-lg p-4">
              <div className="text-display-lg text-tertiary font-bold">{(diff.removed ?? diff.removedSchedules ?? []).length}</div>
              <div className="text-label-sm text-on-tertiary-fixed-variant">Xóa</div>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
