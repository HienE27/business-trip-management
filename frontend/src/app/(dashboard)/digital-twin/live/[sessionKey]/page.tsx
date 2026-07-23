"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { BackButton } from "@/components/ui/BackButton";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type {
  SandboxSession,
  SandboxStatus,
  TimelineEvent,
  TimelineEventType,
} from "@/types/api";
import { LiveScoreChart } from "./LiveScoreChart";
import { TimelineFeed } from "./TimelineFeed";
import { LiveMetrics } from "./LiveMetrics";
import { PlaybackControls } from "./PlaybackControls";

/**
 * v11.1.5 Live Timeline
 *
 * Real-time visualization of sandbox simulation:
 * - Live score chart
 * - Timeline event feed
 * - Live metrics
 * - Playback controls (for replay)
 */
export default function LiveTimelinePage() {
  const params = useParams();
  const router = useRouter();
  const sessionKey = params.sessionKey as string;

  const [session, setSession] = useState<SandboxSession | null>(null);
  const [events, setEvents] = useState<TimelineEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  const eventSourceRef = useRef<EventSource | null>(null);
  const eventsRef = useRef<TimelineEvent[]>([]);

  // Load initial session data
  const loadSession = useCallback(async () => {
    try {
      const data = await api.getSandboxByKey(sessionKey);
      setSession(data as SandboxSession | null);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [sessionKey]);

  useEffect(() => {
    loadSession();
  }, [loadSession]);

  // Connect to SSE stream
  useEffect(() => {
    if (!sessionKey) return;

    const eventSource = new EventSource(`/api/v1/sandbox/${sessionKey}/timeline/live`);
    eventSourceRef.current = eventSource;

    eventSource.onopen = () => {
      setIsConnected(true);
    };

    eventSource.onerror = () => {
      setIsConnected(false);
    };

    eventSource.addEventListener("timeline", (e) => {
      try {
        const event = JSON.parse(e.data) as TimelineEvent;
        eventsRef.current = [...eventsRef.current, event];
        setEvents([...eventsRef.current]);
      } catch (err) {
        console.error("Failed to parse timeline event:", err);
      }
    });

    return () => {
      eventSource.close();
      setIsConnected(false);
    };
  }, [sessionKey]);

  // Control actions
  const handlePause = async () => {
    try {
      await api.pauseSandboxSimulation(sessionKey);
      setSession((s) => s ? { ...s, status: "PAUSED" as SandboxStatus } : null);
    } catch (err) {
      alert("Lỗi: " + getErrorMessage(err, "Có lỗi xảy ra"));
    }
  };

  const handleResume = async () => {
    try {
      await api.resumeSandboxSimulation(sessionKey);
      setSession((s) => s ? { ...s, status: "RUNNING" as SandboxStatus } : null);
    } catch (err) {
      alert("Lỗi: " + getErrorMessage(err, "Có lỗi xảy ra"));
    }
  };

  const handleCancel = async () => {
    if (!confirm("Bạn có chắc muốn dừng mô phỏng?")) return;

    try {
      await api.cancelSandboxSimulation(sessionKey);
      router.push("/digital-twin/compare");
    } catch (err) {
      alert("Lỗi: " + getErrorMessage(err, "Có lỗi xảy ra"));
    }
  };

  // Get latest metrics from events
  const latestEvent = events[events.length - 1];
  const currentMetrics = latestEvent || session ? {
    iteration: latestEvent?.iteration ?? session?.iterations ?? 0,
    score: latestEvent?.score ?? session?.bestScore ?? 0,
    coverage: latestEvent?.coverage ?? session?.coverageRate ?? 0,
    fairnessCv: latestEvent?.fairnessCv ?? session?.fairnessCv ?? 0,
    violations: latestEvent?.hardViolations ?? session?.violations ?? 0,
  } : null;

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

  if (loading) {
    return (
      <div className="p-margin-desktop space-y-6">
        <Skeleton className="h-12 w-64" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-4">
            <Skeleton className="h-64 rounded-xl" />
            <Skeleton className="h-96 rounded-xl" />
          </div>
          <Skeleton className="h-96 rounded-xl" />
        </div>
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

  const status = statusConfig[session.status];

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <BackButton href="/digital-twin/compare" />
            <h1 className="font-display-lg text-display-lg text-on-surface">{session.name}</h1>
            <Badge tone={status.tone}>{status.label}</Badge>
            <div className={`w-2 h-2 rounded-full ${isConnected ? "bg-secondary animate-pulse" : "bg-error"}`} />
          </div>
          <p className="text-body-sm text-on-surface-variant">
            Kỳ #{session.sourcePeriodId} • {new Date(session.createdAt).toLocaleString("vi-VN")}
          </p>
        </div>

        <PlaybackControls
          status={session.status}
          onPause={handlePause}
          onResume={handleResume}
          onCancel={handleCancel}
          onViewResults={() => router.push(`/digital-twin/compare/${sessionKey}`)}
        />
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Chart + Feed */}
        <div className="lg:col-span-2 space-y-6">
          {/* Live Score Chart */}
          <LiveScoreChart events={events} />

          {/* Timeline Feed */}
          <TimelineFeed events={events} />
        </div>

        {/* Right Column: Live Metrics */}
        <div className="space-y-6">
          <LiveMetrics
            iteration={currentMetrics?.iteration ?? 0}
            score={currentMetrics?.score ?? 0}
            coverage={currentMetrics?.coverage ?? 0}
            fairnessCv={currentMetrics?.fairnessCv ?? 0}
            violations={currentMetrics?.violations ?? 0}
            isRunning={session.status === "RUNNING"}
          />

          {/* Recent Events */}
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4">
            <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Sự kiện gần đây</h3>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {events.slice(-10).reverse().map((event, idx) => (
                <div key={idx} className="flex items-start gap-2 text-label-sm">
                  <span className="text-on-surface-variant shrink-0">#{event.iteration}</span>
                  <EventTypeBadge type={event.eventType} />
                  {event.accepted !== undefined && (
                    <span className={event.accepted ? "text-secondary" : "text-error"}>
                      {event.accepted ? "✓" : "✗"}
                    </span>
                  )}
                </div>
              ))}
              {events.length === 0 && (
                <p className="text-label-sm text-on-surface-variant text-center py-4">
                  Chưa có sự kiện
                </p>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function EventTypeBadge({ type }: { type: TimelineEventType }) {
  const config: Record<TimelineEventType, { label: string; color: string }> = {
    STARTED: { label: "Bắt đầu", color: "bg-primary-fixed text-primary" },
    ITERATION_START: { label: "Iteration Start", color: "bg-surface-variant text-on-surface" },
    ITERATION_END: { label: "Iteration End", color: "bg-surface-variant text-on-surface" },
    MOVE_PROPOSED: { label: "Move Proposed", color: "bg-surface-variant text-on-surface" },
    MOVE_EVALUATING: { label: "Đánh giá", color: "bg-surface-variant text-on-surface" },
    MOVE_ACCEPTED: { label: "Chấp nhận", color: "bg-secondary-container text-on-secondary-container" },
    MOVE_REJECTED: { label: "Từ chối", color: "bg-error-container text-on-error-container" },
    SCORE_IMPROVED: { label: "Cải thiện", color: "bg-primary-fixed text-primary" },
    NO_IMPROVEMENT: { label: "No Improvement", color: "bg-surface-variant text-on-surface" },
    TABU_HIT: { label: "Tabu", color: "bg-tertiary-fixed text-tertiary" },
    DIVERSIFIED: { label: "Đa dạng", color: "bg-surface-variant text-on-surface" },
    BEST_UPDATED: { label: "Best mới", color: "bg-secondary text-white" },
    SNAPSHOT: { label: "Snapshot", color: "bg-surface-variant text-on-surface" },
    EARLY_STOP: { label: "Early Stop", color: "bg-tertiary-fixed text-tertiary" },
    PAUSED: { label: "Tạm dừng", color: "bg-tertiary-fixed text-tertiary" },
    RESUMED: { label: "Tiếp tục", color: "bg-secondary-container text-on-secondary-container" },
    COMPLETED: { label: "Hoàn thành", color: "bg-secondary text-white" },
    FAILED: { label: "Thất bại", color: "bg-error text-white" },
  };

  const c = config[type] || { label: type, color: "bg-surface-variant text-on-surface" };

  return (
    <span className={`px-1.5 py-0.5 rounded text-label-xs font-medium ${c.color}`}>
      {c.label}
    </span>
  );
}
