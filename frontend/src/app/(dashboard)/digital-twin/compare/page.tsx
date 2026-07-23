"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type { SandboxSession, SandboxStatus, SimulationMode } from "@/types/api";

/**
 * v11.1.2 Compare Dashboard - List Page
 *
 * Shows all sandbox sessions with options to:
 * - View comparison results
 * - Start new simulation
 * - Delete old sessions
 */
export default function CompareDashboardPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<SandboxSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | SandboxStatus>("all");

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

  const handleDelete = async (sessionKey: string) => {
    if (!confirm("Bạn có chắc muốn xóa phiên mô phỏng này?")) return;

    try {
      await api.deleteSandbox(sessionKey);
      loadSessions();
    } catch (err) {
      alert("Lỗi: " + getErrorMessage(err, "Có lỗi xảy ra"));
    }
  };

  const filteredSessions = filter === "all"
    ? sessions
    : sessions.filter((s) => s.status === filter);

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

  const modeLabels: Record<SimulationMode, string> = {
    STEPPING: "Stepping",
    FULL: "Full",
    FAST_FORWARD: "Fast Forward",
    SINGLE_RUN: "Đơn lẻ",
    COMPARE: "So sánh",
    SENSITIVITY: "Độ nhạy",
    WHAT_IF: "What-if",
  };

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-surface mb-2">Compare Dashboard</h1>
          <p className="text-body-md text-on-surface-variant">
            So sánh kết quả mô phỏng với lịch hiện tại
          </p>
        </div>

        <Button variant="primary" onClick={() => router.push("/auto-scheduling")}>
          <span className="material-symbols-outlined text-[18px]">add</span>
          Tạo mô phỏng mới
        </Button>
      </div>

      {/* Filters */}
      <div className="flex gap-2 flex-wrap">
        <Button
          variant={filter === "all" ? "primary" : "ghost"}
          size="sm"
          onClick={() => setFilter("all")}
        >
          Tất cả ({sessions.length})
        </Button>
        <Button
          variant={filter === "COMPLETED" ? "primary" : "ghost"}
          size="sm"
          onClick={() => setFilter("COMPLETED")}
        >
          Hoàn thành ({sessions.filter((s) => s.status === "COMPLETED").length})
        </Button>
        <Button
          variant={filter === "RUNNING" ? "primary" : "ghost"}
          size="sm"
          onClick={() => setFilter("RUNNING")}
        >
          Đang chạy ({sessions.filter((s) => s.status === "RUNNING").length})
        </Button>
        <Button
          variant={filter === "READY" ? "primary" : "ghost"}
          size="sm"
          onClick={() => setFilter("READY")}
        >
          Sẵn sàng ({sessions.filter((s) => s.status === "READY").length})
        </Button>
      </div>

      {/* Sessions Grid */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-64 rounded-xl" />
          ))}
        </div>
      ) : error ? (
        <div className="p-6 bg-error-container rounded-lg text-center">
          <p className="text-on-error-container">Lỗi: {error}</p>
          <Button variant="secondary" className="mt-4" onClick={loadSessions}>
            Thử lại
          </Button>
        </div>
      ) : filteredSessions.length === 0 ? (
        <div className="p-12 bg-surface-container-lowest border border-outline-variant rounded-xl text-center">
          <span className="material-symbols-outlined text-[48px] text-on-surface-variant">analytics</span>
          <h3 className="mt-4 font-title-lg text-title-lg text-on-surface">Chưa có phiên mô phỏng</h3>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Tạo một phiên mô phỏng mới để bắt đầu so sánh
          </p>
          <Button variant="primary" className="mt-6" onClick={() => router.push("/auto-scheduling")}>
            Tạo mô phỏng mới
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredSessions.map((session) => {
            const status = statusConfig[session.status];
            const coverageDelta = (session.coverageRate ?? 0) - (session.initialScore ?? 0);

            return (
              <div
                key={session.sessionKey}
                className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 hover:bg-surface-container-low transition-colors group"
              >
                {/* Header */}
                <div className="flex items-start justify-between gap-2 mb-4">
                  <div className="flex-1 min-w-0">
                    <h3 className="font-title-lg text-title-lg text-on-surface truncate">{session.name}</h3>
                    <p className="text-label-sm text-on-surface-variant">
                      {new Date(session.createdAt).toLocaleDateString("vi-VN")}
                    </p>
                  </div>
                  <Badge tone={status.tone}>{status.label}</Badge>
                </div>

                {/* Metrics */}
                <div className="grid grid-cols-2 gap-3 mb-4">
                  <div className="bg-surface-container-low rounded-lg p-3">
                    <div className="text-label-sm text-on-surface-variant">Coverage</div>
                    <div className="text-title-lg text-on-surface font-bold">
                      {session.coverageRate?.toFixed(1) ?? "—"}%
                    </div>
                  </div>
                  <div className="bg-surface-container-low rounded-lg p-3">
                    <div className="text-label-sm text-on-surface-variant">Fairness</div>
                    <div className="text-title-lg text-on-surface font-bold">
                      {session.fairnessCv?.toFixed(3) ?? "—"}
                    </div>
                  </div>
                </div>

                {/* Delta */}
                {session.status === "COMPLETED" && (
                  <div className="flex items-center gap-2 text-label-sm mb-4">
                    <span className={coverageDelta >= 0 ? "text-secondary" : "text-error"}>
                      {coverageDelta >= 0 ? "+" : ""}{coverageDelta.toFixed(1)}%
                    </span>
                    <span className="text-on-surface-variant">vs ban đầu</span>
                  </div>
                )}

                {/* Footer */}
                <div className="flex gap-2 pt-4 border-t border-outline-variant">
                  {session.status === "COMPLETED" && (
                    <Button
                      variant="primary"
                      size="sm"
                      className="flex-1"
                      onClick={() => router.push(`/digital-twin/compare/${session.sessionKey}`)}
                    >
                      <span className="material-symbols-outlined text-[16px]">compare</span>
                      Xem kết quả
                    </Button>
                  )}
                  {["RUNNING", "PAUSED"].includes(session.status) && (
                    <Button
                      variant="secondary"
                      size="sm"
                      className="flex-1"
                      onClick={() => router.push(`/digital-twin/live/${session.sessionKey}`)}
                    >
                      <span className="material-symbols-outlined text-[16px]">visibility</span>
                      Theo dõi
                    </Button>
                  )}
                  {session.status === "READY" && (
                    <Button
                      variant="secondary"
                      size="sm"
                      className="flex-1"
                      onClick={() => api.startSandboxSimulation(session.sessionKey).then(loadSessions)}
                    >
                      <span className="material-symbols-outlined text-[16px]">play_arrow</span>
                      Chạy
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleDelete(session.sessionKey)}
                  >
                    <span className="material-symbols-outlined text-[16px]">delete</span>
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
