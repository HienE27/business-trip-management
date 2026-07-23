"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type { SandboxSession, SandboxStatus } from "@/types/api";

/**
 * v11.1.6.5 Decision Graph - List Page
 *
 * Shows all completed sessions with quick access to decision graph.
 */
export default function DecisionListPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<SandboxSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await api.getSandboxes();
      setSessions((data as SandboxSession[]) || []);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const completedSessions = sessions.filter(
    (s) => s.status === "COMPLETED" || s.status === "PROMOTED"
  );

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

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-surface mb-2">Decision Graph</h1>
          <p className="text-body-md text-on-surface-variant">
            Trực quan hóa quá trình ra quyết định của thuật toán
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => router.push("/digital-twin/replay")}>
            <span className="material-symbols-outlined text-[18px]">replay</span>
            Replay
          </Button>
          <Button variant="primary" onClick={() => router.push("/auto-scheduling")}>
            <span className="material-symbols-outlined text-[18px]">add</span>
            Tạo mô phỏng
          </Button>
        </div>
      </div>

      {/* Sessions */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">
          Sessions ({completedSessions.length})
        </h2>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-48 rounded-xl" />
            ))}
          </div>
        ) : completedSessions.length === 0 ? (
          <div className="p-12 bg-surface-container-lowest border border-outline-variant rounded-xl text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant">account_tree</span>
            <h3 className="mt-4 font-title-lg text-title-lg text-on-surface">Chưa có session nào</h3>
            <p className="mt-2 text-body-md text-on-surface-variant">
              Chạy một mô phỏng và hoàn thành để xem decision graph
            </p>
            <Button variant="primary" className="mt-6" onClick={() => router.push("/auto-scheduling")}>
              Tạo mô phỏng mới
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {completedSessions.map((session) => {
              const status = statusConfig[session.status];

              return (
                <div
                  key={session.sessionKey}
                  className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 hover:bg-surface-container-low transition-colors"
                >
                  <div className="flex items-start justify-between gap-2 mb-4">
                    <div className="flex-1 min-w-0">
                      <h3 className="font-title-lg text-title-lg text-on-surface truncate">{session.name}</h3>
                      <p className="text-label-sm text-on-surface-variant">
                        {new Date(session.createdAt).toLocaleDateString("vi-VN")}
                      </p>
                    </div>
                    <Badge tone={status.tone}>{status.label}</Badge>
                  </div>

                  {/* Quick metrics */}
                  <div className="grid grid-cols-2 gap-3 mb-4">
                    <div className="bg-surface-container-low rounded-lg p-3">
                      <div className="text-label-xs text-on-surface-variant">Coverage</div>
                      <div className="text-title-lg text-on-surface font-bold">
                        {session.coverageRate?.toFixed(1) ?? "—"}%
                      </div>
                    </div>
                    <div className="bg-surface-container-low rounded-lg p-3">
                      <div className="text-label-xs text-on-surface-variant">Fairness</div>
                      <div className="text-title-lg text-on-surface font-bold">
                        {session.fairnessCv?.toFixed(3) ?? "—"}
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2 mb-4 text-center">
                    <div>
                      <div className="text-title-lg text-on-surface font-bold">{session.iterations ?? 0}</div>
                      <div className="text-label-xs text-on-surface-variant">Iterations</div>
                    </div>
                    <div>
                      <div className="text-title-lg text-secondary font-bold">
                        {session.violations ?? 0}
                      </div>
                      <div className="text-label-xs text-on-surface-variant">Violations</div>
                    </div>
                  </div>

                  {/* Action buttons */}
                  <div className="flex gap-2">
                    <Button
                      variant="primary"
                      className="flex-1"
                      onClick={() => router.push(`/digital-twin/decision/${session.sessionKey}`)}
                    >
                      <span className="material-symbols-outlined text-[18px]">account_tree</span>
                      Decision Graph
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => router.push(`/digital-twin/replay/${session.sessionKey}`)}
                    >
                      <span className="material-symbols-outlined text-[16px]">replay</span>
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
