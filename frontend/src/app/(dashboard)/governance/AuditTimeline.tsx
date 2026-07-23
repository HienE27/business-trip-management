"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Skeleton } from "@/components/ui/Skeleton";
import { Badge } from "@/components/ui/Badge";
import type { AuditEvent, AuditTimelineEvent } from "@/types/api";

interface AuditTimelineProps {
  limit?: number;
  showSearch?: boolean;
}

/**
 * Audit timeline component showing recent audit events.
 */
export function AuditTimeline({ limit = 10, showSearch = false }: AuditTimelineProps) {
  const [events, setEvents] = useState<AuditTimelineEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Discard stale in-flight loads so a slower earlier fetch for one filter
  // combination can't clobber a faster newer one.
  const latestEventsRequestRef = useRef(0);

  // Search
  const [entityType, setEntityType] = useState("");
  const [action, setAction] = useState("");

  const loadEvents = useCallback(async () => {
    const requestId = ++latestEventsRequestRef.current;
    setLoading(true);
    setError(null);

    try {
      // For now, we'll use search API with pagination
      const result = await api.searchAudit({
        entityType: entityType || undefined,
        action: action || undefined,
        page: 0,
        size: limit,
      });

      // Drop the response if the user has since changed filters.
      if (requestId !== latestEventsRequestRef.current) return;

      // Convert to timeline format
      const timelineEvents: AuditTimelineEvent[] = result.events.map((e: AuditEvent, idx: number) => ({
        id: idx,
        timestamp: e.timestamp ?? e.performedAt,
        userName: e.userName ?? e.performedBy,
        userRole: e.userRole ?? "N/A",
        action: e.action ?? e.eventType,
        entityType: e.entityType,
        entityId: e.entityId ?? 0,
        description: formatAction(e),
        previousValue: e.previousValue,
        newValue: e.newValue,
        reason: e.reason,
      }));

      setEvents(timelineEvents);
    } catch (err) {
      if (requestId !== latestEventsRequestRef.current) return;
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      if (requestId === latestEventsRequestRef.current) setLoading(false);
    }
  }, [limit, entityType, action]);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  const formatAction = (e: AuditEvent) => {
    const actionLabels: Record<string, string> = {
      CONFIG_CREATE: "tạo cấu hình",
      CONFIG_UPDATE: "cập nhật cấu hình",
      CONFIG_DELETE: "xóa cấu hình",
      CONFIG_APPLY: "áp dụng cấu hình",
      CONFIG_ROLLBACK: "rollback cấu hình",
      PROFILE_CREATE: "tạo profile",
      PROFILE_APPLY: "áp dụng profile",
      SCHEDULE_GENERATE: "sinh lịch",
      SCHEDULE_PUBLISH: "công bố lịch",
      USER_LOGIN: "đăng nhập",
      USER_LOGOUT: "đăng xuất",
      SANDBOX_CREATE: "tạo sandbox",
      SANDBOX_RUN: "chạy sandbox",
      WHATIF_CREATE_SCENARIO: "tạo scenario",
      WHATIF_RUN_SCENARIO: "chạy scenario",
      APPROVAL_SUBMIT: "gửi phê duyệt",
      APPROVAL_APPROVE: "duyệt",
      APPROVAL_REJECT: "từ chối",
    };

    return actionLabels[e.action as keyof typeof actionLabels] || (e.action ?? "unknown").toLowerCase().replace(/_/g, " ");
  };

  const getActionColor = (action: string) => {
    if (action.includes("CREATE")) return "success";
    if (action.includes("DELETE") || action.includes("REJECT")) return "error";
    if (action.includes("UPDATE") || action.includes("ROLLBACK")) return "warning";
    if (action.includes("APPLY") || action.includes("APPROVE")) return "info";
    return "info";
  };

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return "Vừa xong";
    if (diffMins < 60) return `${diffMins} phút trước`;
    if (diffHours < 24) return `${diffHours} giờ trước`;
    if (diffDays < 7) return `${diffDays} ngày trước`;
    return date.toLocaleDateString("vi-VN");
  };

  if (loading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3, 4, 5].map((i) => (
          <Skeleton key={i} className="h-16 rounded-lg" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 bg-error-container rounded-lg">
        <p className="text-error text-label-md">{error}</p>
      </div>
    );
  }

  if (events.length === 0) {
    return (
      <div className="p-8 text-center">
        <span className="material-symbols-outlined text-[48px] text-on-surface-variant">inbox</span>
        <p className="mt-2 text-label-md text-on-surface-variant">Chưa có audit event nào</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Search filters */}
      {showSearch && (
        <div className="flex items-center gap-4 p-4 bg-surface-container-low rounded-lg">
          <div className="flex-1">
            <label className="text-label-sm text-on-surface-variant mb-1 block">Entity Type</label>
            <select
              value={entityType}
              onChange={(e) => setEntityType(e.target.value)}
              className="w-full px-3 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg text-body-sm text-on-surface"
            >
              <option value="">Tất cả</option>
              <option value="Config">Config</option>
              <option value="Profile">Profile</option>
              <option value="Schedule">Schedule</option>
              <option value="User">User</option>
              <option value="Sandbox">Sandbox</option>
            </select>
          </div>
          <div className="flex-1">
            <label className="text-label-sm text-on-surface-variant mb-1 block">Action</label>
            <select
              value={action}
              onChange={(e) => setAction(e.target.value)}
              className="w-full px-3 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg text-body-sm text-on-surface"
            >
              <option value="">Tất cả</option>
              <option value="CREATE">Create</option>
              <option value="UPDATE">Update</option>
              <option value="DELETE">Delete</option>
              <option value="APPLY">Apply</option>
              <option value="ROLLBACK">Rollback</option>
            </select>
          </div>
          <div className="flex items-end">
            <button
              onClick={loadEvents}
              className="px-4 py-2 bg-primary text-white rounded-lg text-label-md hover:bg-primary/90 transition-colors"
            >
              Tìm kiếm
            </button>
          </div>
        </div>
      )}

      {/* Timeline */}
      <div className="space-y-2">
        {events.map((event, idx) => (
          <div
            key={idx}
            className="flex items-start gap-4 p-4 bg-surface-container-low rounded-lg hover:bg-surface-container-lowest transition-colors"
          >
            {/* Timeline line */}
            <div className="relative">
              <div className="w-3 h-3 rounded-full bg-primary mt-1.5" />
              {idx < events.length - 1 && (
                <div className="absolute top-4 left-1/2 -translate-x-1/2 w-0.5 h-full bg-outline-variant" />
              )}
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-medium text-body-md text-on-surface">
                  {event.userName || "System"}
                </span>
                <span className="text-body-sm text-on-surface-variant">
                  {event.description}
                </span>
                <Badge tone={getActionColor(event.action)}>
                  {event.action}
                </Badge>
              </div>

              <div className="flex items-center gap-4 mt-1 text-label-sm text-on-surface-variant">
                <span>{event.entityType}</span>
                {event.entityId && <span>#{event.entityId}</span>}
                <span className="ml-auto">{formatTime(event.timestamp)}</span>
              </div>

              {/* Reason if exists */}
              {event.reason && (
                <div className="mt-2 p-2 bg-surface-container-lowest rounded text-label-sm text-on-surface-variant">
                  <span className="font-medium">Lý do: </span>
                  {event.reason}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
