"use client";

import { useEffect, useRef } from "react";
import type { TimelineEvent } from "@/types/api";

interface TimelineFeedProps {
  events: TimelineEvent[];
}

/**
 * Timeline event feed showing move-by-move history.
 * Auto-scrolls to bottom as new events arrive.
 */
export function TimelineFeed({ events }: TimelineFeedProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isAutoScrollRef = useRef(true);

  // Auto-scroll to bottom when new events arrive
  useEffect(() => {
    if (containerRef.current && isAutoScrollRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [events.length]);

  // Format timestamp
  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  };

  // Get event icon
  const getEventIcon = (event: TimelineEvent) => {
    switch (event.eventType) {
      case "MOVE_ACCEPTED":
        return "check_circle";
      case "MOVE_REJECTED":
        return "cancel";
      case "SCORE_IMPROVED":
        return "trending_up";
      case "BEST_UPDATED":
        return "star";
      case "TABU_HIT":
        return "block";
      case "DIVERSIFIED":
        return "shuffle";
      case "COMPLETED":
        return "done_all";
      case "FAILED":
        return "error";
      default:
        return "radio_button_checked";
    }
  };

  // Get event color
  const getEventColor = (event: TimelineEvent) => {
    switch (event.eventType) {
      case "MOVE_ACCEPTED":
        return "text-secondary bg-secondary-container";
      case "MOVE_REJECTED":
        return "text-error bg-error-container";
      case "SCORE_IMPROVED":
        return "text-primary bg-primary-fixed";
      case "BEST_UPDATED":
        return "text-secondary bg-secondary";
      case "TABU_HIT":
        return "text-tertiary bg-tertiary-fixed";
      case "DIVERSIFIED":
        return "text-on-surface-variant bg-surface-container-high";
      case "COMPLETED":
        return "text-secondary bg-secondary";
      case "FAILED":
        return "text-error bg-error";
      case "STARTED":
        return "text-primary bg-primary";
      case "PAUSED":
      case "RESUMED":
        return "text-tertiary bg-tertiary-fixed";
      default:
        return "text-on-surface-variant bg-surface-container-low";
    }
  };

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-title-lg text-title-lg text-on-surface">Timeline</h3>
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-2 text-label-sm text-on-surface-variant cursor-pointer">
            <input
              type="checkbox"
              checked={isAutoScrollRef.current}
              onChange={(e) => (isAutoScrollRef.current = e.target.checked)}
              className="w-4 h-4 rounded border-outline"
            />
            Auto-scroll
          </label>
        </div>
      </div>

      <div
        ref={containerRef}
        className="h-80 overflow-y-auto space-y-2 pr-2"
        style={{ scrollbarWidth: "thin" }}
      >
        {events.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant animate-pulse">
              pending
            </span>
            <p className="mt-2 text-label-md text-on-surface-variant">Đang chờ sự kiện...</p>
          </div>
        ) : (
          events.map((event, idx) => (
            <div
              key={idx}
              className={`flex items-start gap-3 p-3 rounded-lg transition-colors ${getEventColor(event)}`}
            >
              {/* Icon */}
              <span
                className="material-symbols-outlined text-[20px] shrink-0 mt-0.5"
                style={{ fontVariationSettings: "'FILL' 0" }}
              >
                {getEventIcon(event)}
              </span>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-label-md font-semibold">
                    #{event.iteration}
                  </span>
                  <span className="text-label-sm opacity-80">
                    {formatTime(event.timestamp)}
                  </span>
                  {event.moveType && (
                    <span className="px-1.5 py-0.5 bg-white/20 rounded text-label-xs font-medium">
                      {event.moveType}
                    </span>
                  )}
                </div>

                {/* Event details */}
                <div className="mt-1 text-label-sm opacity-90">
                  {event.eventType === "MOVE_ACCEPTED" && (
                    <span>
                      Chấp nhận
                      {event.staffName && ` - ${event.staffName}`}
                      {event.scoreDelta && (
                        <span className="ml-1 text-secondary">
                          ({event.scoreDelta > 0 ? "+" : ""}{event.scoreDelta.toFixed(1)})
                        </span>
                      )}
                    </span>
                  )}
                  {event.eventType === "MOVE_REJECTED" && (
                    <span>
                      Từ chối
                      {event.rejectionReason && ` - ${event.rejectionReason}`}
                    </span>
                  )}
                  {event.eventType === "SCORE_IMPROVED" && (
                    <span>
                      Score cải thiện → {(event.score ?? 0).toFixed(1)}
                    </span>
                  )}
                  {event.eventType === "BEST_UPDATED" && (
                    <span className="text-secondary">
                      Best score mới: {(event.score ?? 0).toFixed(1)}
                    </span>
                  )}
                  {event.eventType === "TABU_HIT" && (
                    <span>Tabu hit</span>
                  )}
                  {event.eventType === "DIVERSIFIED" && (
                    <span>Đa dạng hóa</span>
                  )}
                  {event.eventType === "STARTED" && (
                    <span>Bắt đầu mô phỏng</span>
                  )}
                  {event.eventType === "COMPLETED" && (
                    <span className="text-secondary font-semibold">
                      Hoàn thành - Score: {(event.score ?? 0).toFixed(1)}
                    </span>
                  )}
                  {event.eventType === "FAILED" && (
                    <span className="text-error">
                      Thất bại
                    </span>
                  )}
                  {!["MOVE_ACCEPTED", "MOVE_REJECTED", "SCORE_IMPROVED", "BEST_UPDATED", "TABU_HIT", "DIVERSIFIED", "STARTED", "COMPLETED", "FAILED"].includes(event.eventType) && (
                    <span>{event.eventType}</span>
                  )}
                </div>

                {/* Metrics */}
                {((event.coverage ?? 0) > 0 || (event.fairnessCv ?? 0) > 0) && (
                  <div className="mt-1 flex items-center gap-3 text-label-xs opacity-70">
                    {(event.coverage ?? 0) > 0 && (
                      <span>Coverage: {(event.coverage ?? 0).toFixed(1)}%</span>
                    )}
                    {(event.fairnessCv ?? 0) > 0 && (
                      <span>CV: {(event.fairnessCv ?? 0).toFixed(3)}</span>
                    )}
                    {(event.hardViolations ?? 0) > 0 && (
                      <span className="text-error">Violations: {event.hardViolations}</span>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
