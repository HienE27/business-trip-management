"use client";

import { useState, useRef, useEffect, useMemo } from "react";
import Link from "next/link";
import { Button, IconButton } from "@/components/ui";
import type { NotificationItem } from "@/components/ui/NotificationContext";

type NotificationCenterProps = {
  notifications?: NotificationItem[];
  maxCount?: number;
  onMarkAllRead?: () => Promise<void> | void;
};

export function NotificationCenter({
  notifications = [],
  maxCount = 5,
  onMarkAllRead,
}: NotificationCenterProps) {
  const [open, setOpen] = useState(false);
  const [markingAllRead, setMarkingAllRead] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const unreadCount = useMemo(
    () => notifications.filter((n) => n.unread).length,
    [notifications]
  );

  const displayNotifications = useMemo(
    () => notifications.slice(0, maxCount),
    [notifications, maxCount]
  );

  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  async function handleMarkAllRead() {
    if (!onMarkAllRead) {
      setOpen(false);
      return;
    }

    try {
      setMarkingAllRead(true);
      await onMarkAllRead();
      setOpen(false);
    } finally {
      setMarkingAllRead(false);
    }
  }

  return (
    <div ref={ref} className="relative">
      <IconButton
        label={`Thông báo${unreadCount > 0 ? ` (${unreadCount} chưa đọc)` : ""}`}
        variant="ghost"
        size="md"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="relative text-on-surface-variant"
      >
        <span className="material-symbols-outlined text-[20px]" aria-hidden="true" style={{ fontVariationSettings: "'FILL' 1" }}>notifications</span>
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] px-1 items-center justify-center rounded-full bg-error text-[10px] leading-none font-semibold text-white ring-2 ring-surface">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </IconButton>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-80 overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-xl">
          <div className="flex items-center justify-between border-b border-outline-variant px-4 py-3">
            <h3 className="text-title-lg font-semibold text-on-surface">Thông báo</h3>
            {unreadCount > 0 && (
              <Button
                variant="ghost"
                size="sm"
                disabled={markingAllRead}
                loading={markingAllRead}
                onClick={() => void handleMarkAllRead()}
                className="text-blue-800 hover:underline hover:bg-transparent"
              >
                {markingAllRead ? "Đang cập nhật..." : "Đánh dấu đã đọc"}
              </Button>
            )}
          </div>

          <ul className="max-h-[360px] overflow-y-auto" aria-label="Danh sách thông báo">
            {displayNotifications.length === 0 ? (
              <li className="flex flex-col items-center justify-center gap-3 py-10 text-on-surface-variant">
                <span className="material-symbols-outlined text-[40px] opacity-40" aria-hidden="true">notifications_none</span>
                <p className="text-label-md">Không có thông báo nào</p>
              </li>
            ) : (
              displayNotifications.map((n) => (
                <li
                  key={n.id}
                  className={`flex cursor-pointer items-start gap-3 border-b border-outline-variant/50 px-4 py-3 transition-colors hover:bg-surface-container-low last:border-b-0 ${
                    n.unread ? "bg-blue-100/5" : ""
                  }`}
                >
                  <div className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${n.iconColor ?? "bg-surface-container-low"}`}>
                    <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{n.icon}</span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-label-md font-medium text-on-surface">{n.title}</p>
                    <p className="mt-0.5 line-clamp-2 text-label-sm leading-relaxed text-on-surface-variant">{n.detail}</p>
                    <p className="mt-1 text-label-sm text-on-surface-variant opacity-70">{n.time}</p>
                  </div>
                  {n.unread && <div className="mt-2 h-2 w-2 shrink-0 rounded-full bg-blue-100" />}
                </li>
              ))
            )}
          </ul>

          {notifications.length > 0 && (
            <Link
              href="/notifications"
              onClick={() => setOpen(false)}
              className="block border-t border-outline-variant px-4 py-3 text-center text-label-md font-medium text-blue-800 transition-colors hover:bg-blue-100/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300"
            >
              {notifications.length > maxCount ? `Xem tất cả (${notifications.length})` : "Xem tất cả thông báo"}
            </Link>
          )}
        </div>
      )}
    </div>
  );
}
