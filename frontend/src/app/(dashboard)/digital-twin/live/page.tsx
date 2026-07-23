"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type { SandboxSession, SandboxStatus } from "@/types/api";

/**
 * v11.1.5 Live Timeline - List Page
 *
 * Shows all active/running sandbox sessions with quick access to live monitoring.
 */
export default function LiveTimelineListPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<SandboxSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Generation counter: a slow prior fetch MUST NOT clobber a fresher one when
  // the 5s poll interval fires faster than the API can answer.
  const latestSessionsRequestRef = useRef(0);
  // Track whether we still need the initial loading spinner without putting
  // `sessions.length` in useCallback deps (which would reset the poll
  // interval each time the data changes).
  const hasLoadedOnceRef = useRef(false);

  const loadSessions = useCallback(async () => {
    const requestId = ++latestSessionsRequestRef.current;
    // Skip the global loading spinner on poll-driven refetches so the UI
    // doesn't flicker every 5 seconds — only the very first load shows it.
    if (!hasLoadedOnceRef.current) setLoading(true);
    setError(null);

    try {
      const data = await api.getSandboxes();
      if (requestId !== latestSessionsRequestRef.current) return;
      setSessions((data as SandboxSession[]) || []);
      hasLoadedOnceRef.current = true;
    } catch (err) {
      if (requestId !== latestSessionsRequestRef.current) return;
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      if (requestId === latestSessionsRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  // Auto-refresh every 5 seconds
  useEffect(() => {
    const interval = setInterval(loadSessions, 5000);
    return () => clearInterval(interval);
  }, [loadSessions]);

  const activeSessions = sessions.filter(
    (s) => s.status === "RUNNING" || s.status === "PAUSED" || s.status === "READY"
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
          <h1 className="font-display-lg text-display-lg text-on-surface mb-2">Live Timeline</h1>
          <p className="text-body-md text-on-surface-variant">
            Theo dõi mô phỏng đang chạy theo thời gian thực
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => router.push("/digital-twin/compare")}>
            <span className="material-symbols-outlined text-[18px]">compare</span>
            Compare
          </Button>
          <Button variant="primary" onClick={() => router.push("/auto-scheduling")}>
            <span className="material-symbols-outlined text-[18px]">add</span>
            Tạo mô phỏng
          </Button>
        </div>
      </div>

      {/* Active Sessions */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">
          Phiên đang hoạt động ({activeSessions.length})
        </h2>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1, 2].map((i) => (
              <Skeleton key={i} className="h-48 rounded-xl" />
            ))}
          </div>
        ) : activeSessions.length === 0 ? (
          <div className="p-12 bg-surface-container-lowest border border-outline-variant rounded-xl text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant">live_tv</span>
            <h3 className="mt-4 font-title-lg text-title-lg text-on-surface">Không có phiên đang chạy</h3>
            <p className="mt-2 text-body-md text-on-surface-variant">
              Tạo một mô phỏng mới để bắt đầu theo dõi
            </p>
            <Button variant="primary" className="mt-6" onClick={() => router.push("/auto-scheduling")}>
              Tạo mô phỏng mới
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {activeSessions.map((session) => {
              const status = statusConfig[session.status];
              const isRunning = session.status === "RUNNING";

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

                  {/* Live indicator */}
                  {isRunning && (
                    <div className="flex items-center gap-2 mb-4">
                      <div className="w-2 h-2 rounded-full bg-secondary animate-pulse" />
                      <span className="text-label-sm text-secondary">Live</span>
                    </div>
                  )}

                  {/* Quick metrics */}
                  <div className="grid grid-cols-2 gap-3 mb-4">
                    <div className="bg-surface-container-low rounded-lg p-2">
                      <div className="text-label-xs text-on-surface-variant">Iterations</div>
                      <div className="text-title-lg text-on-surface font-bold">
                        {session.iterations ?? 0}
                      </div>
                    </div>
                    <div className="bg-surface-container-low rounded-lg p-2">
                      <div className="text-label-xs text-on-surface-variant">Coverage</div>
                      <div className="text-title-lg text-on-surface font-bold">
                        {session.coverageRate?.toFixed(1) ?? "—"}%
                      </div>
                    </div>
                  </div>

                  {/* Watch button */}
                  <Button
                    variant={isRunning ? "primary" : "secondary"}
                    className="w-full"
                    onClick={() => router.push(`/digital-twin/live/${session.sessionKey}`)}
                  >
                    <span className="material-symbols-outlined text-[18px]">{isRunning ? "visibility" : "replay"}</span>
                    {isRunning ? "Theo dõi" : "Xem lại"}
                  </Button>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* Recent Sessions */}
      <section>
        <h2 className="font-headline-md text-headline-md text-on-surface mb-4">
          Các phiên gần đây
        </h2>

        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant">
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant uppercase font-semibold">
                  Tên
                </th>
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant uppercase font-semibold">
                  Trạng thái
                </th>
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant uppercase font-semibold">
                  Coverage
                </th>
                <th className="py-3 px-4 text-left text-label-sm text-on-surface-variant uppercase font-semibold">
                  Ngày tạo
                </th>
                <th className="py-3 px-4 text-right text-label-sm text-on-surface-variant uppercase font-semibold">
                  Thao tác
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {sessions.slice(0, 10).map((session) => {
                const status = statusConfig[session.status];
                return (
                  <tr key={session.sessionKey} className="hover:bg-surface-container-low transition-colors">
                    <td className="py-3 px-4">
                      <span className="text-body-md text-on-surface">{session.name}</span>
                    </td>
                    <td className="py-3 px-4">
                      <Badge tone={status.tone}>{status.label}</Badge>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-body-md text-on-surface">
                        {session.coverageRate?.toFixed(1) ?? "—"}%
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-body-md text-on-surface-variant">
                        {new Date(session.createdAt).toLocaleString("vi-VN")}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      {session.status === "COMPLETED" && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => router.push(`/digital-twin/compare/${session.sessionKey}`)}
                        >
                          <span className="material-symbols-outlined text-[16px]">analytics</span>
                          Kết quả
                        </Button>
                      )}
                      {["RUNNING", "PAUSED", "READY"].includes(session.status) && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => router.push(`/digital-twin/live/${session.sessionKey}`)}
                        >
                          <span className="material-symbols-outlined text-[16px]">visibility</span>
                          Xem
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
              {sessions.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-on-surface-variant">
                    Chưa có phiên mô phỏng nào
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
