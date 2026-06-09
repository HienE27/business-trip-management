"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useNotifications } from "@/components/ui/NotificationContext";
import { useAuth } from "@/components/auth/AuthProvider";
import type { Notification } from "@/types/api";

type NotifTab = "all" | "unread" | "conflict" | "exchange" | "published" | "system";

const TABS: { label: string; value: NotifTab }[] = [
  { label: "Tất cả", value: "all" },
  { label: "Chưa đọc", value: "unread" },
  { label: "Cảnh báo xung đột", value: "conflict" },
  { label: "Yêu cầu đổi trực", value: "exchange" },
  { label: "Lịch đã công bố", value: "published" },
  { label: "Hệ thống", value: "system" },
];

function formatTime(dateStr: string) {
  try {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 60) return `${diffMins} phút trước`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} giờ trước`;
    return date.toLocaleDateString("vi-VN");
  } catch {
    return dateStr;
  }
}

function getNotificationIcon(title: string) {
  const lower = title.toLowerCase();
  if (lower.includes("xung đột") || lower.includes("conflict")) return { icon: "warning", wrapClass: "bg-error-container/40 text-error border border-error-container" };
  if (lower.includes("đổi trực") || lower.includes("swap")) return { icon: "swap_horiz", wrapClass: "bg-primary-container text-on-primary-container border border-primary" };
  if (lower.includes("công bố") || lower.includes("published")) return { icon: "event_available", wrapClass: "bg-secondary-container text-secondary border border-secondary-container" };
  if (lower.includes("tự động") || lower.includes("auto")) return { icon: "auto_mode", wrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant/30" };
  return { icon: "notifications", wrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant" };
}

function getNotificationBadge(title: string) {
  const lower = title.toLowerCase();
  if (lower.includes("24/24")) return { badge: "24/24", badgeClass: "bg-primary-fixed text-on-primary-fixed-variant font-bold" };
  if (lower.includes("dịch vụ")) return { badge: "DV", badgeClass: "bg-secondary-container text-secondary font-bold" };
  if (lower.includes("chuyên gia")) return { badge: "CG", badgeClass: "bg-tertiary-fixed text-on-tertiary font-bold" };
  return null;
}

export default function NotificationsPage() {
  const { user } = useAuth();
  const { refreshCount } = useNotifications();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<NotifTab>("all");
  const [message, setMessage] = useState("");

  const fetchNotifications = useCallback(async () => {
    if (!user?.userId) {
      setNotifications([]);
      await refreshCount(0);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setMessage("");
      const res = await api.get<Notification[]>(`/notifications/staff/${user.userId}`);
      setNotifications(res ?? []);
      await refreshCount((res ?? []).filter((n) => !n.isRead).length);
    } catch (err) {
      setNotifications([]);
      await refreshCount(0);
      setMessage(getErrorMessage(err, "Không thể tải thông báo."));
    } finally {
      setLoading(false);
    }
  }, [refreshCount, user?.userId]);

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  const filtered = notifications.filter((n) => {
    if (activeTab === "all") return true;
    if (activeTab === "unread") return !n.isRead;
    if (activeTab === "conflict") return n.title.toLowerCase().includes("xung") || n.title.toLowerCase().includes("conflict");
    if (activeTab === "exchange") return n.title.toLowerCase().includes("đổi") || n.title.toLowerCase().includes("swap");
    if (activeTab === "published") return n.title.toLowerCase().includes("công bố") || n.title.toLowerCase().includes("lich");
    if (activeTab === "system") return n.title.toLowerCase().includes("hệ thống") || n.title.toLowerCase().includes("auto");
    return true;
  });

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <DashboardShell
      activeCode="M06-NOTIFICATIONS"
      description="Quản lý và theo dõi các luồng thông tin hệ thống, cảnh báo xếp lịch."
      title="Trung tâm Thông báo"
    >
      <div className="flex flex-col gap-6 pb-8">
        {/* Filter tabs */}
        <div className="flex gap-2 overflow-x-auto pb-1 hide-scrollbar">
          {TABS.map((tab) => (
            <button
              className={`flex items-center gap-2 whitespace-nowrap rounded-lg px-4 py-2 text-label-md shadow-sm transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 ${
                activeTab === tab.value
                  ? "bg-primary text-on-primary font-semibold"
                  : "border border-outline-variant/50 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container-low"
              }`}
              key={tab.value}
              onClick={() => setActiveTab(tab.value)}
              type="button"
            >
              {tab.label}
              {tab.value === "unread" && unreadCount > 0 ? (
                <span className="rounded-full bg-error px-1.5 py-0.5 text-[10px] font-bold text-on-error leading-tight">
                  {unreadCount}
                </span>
              ) : null}
            </button>
          ))}
        </div>

        {message && (
          <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
            {message}
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16">
            <span className="material-symbols-outlined text-5xl text-outline">notifications_none</span>
            <p className="mt-4 text-on-surface-variant">Không có thông báo nào.</p>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {filtered.map((notif) => {
              const { icon, wrapClass } = getNotificationIcon(notif.title);
              const badge = getNotificationBadge(notif.title);
              return (
                <div
                  className={`group relative flex gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest p-5 transition-all hover:bg-surface-container-low hover:shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] ${
                    !notif.isRead ? "ring-1 ring-primary/10" : ""
                  }`}
                  key={notif.id}
                >
                  {!notif.isRead && <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary" />}

                  <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg shadow-sm ${wrapClass}`}>
                    <span className={`material-symbols-outlined text-[20px] ${!notif.isRead ? "fill" : ""}`}>
                      {icon}
                    </span>
                  </div>

                  <div className="min-w-0 flex-1 pr-6">
                    <div className="mb-1.5 flex items-start justify-between gap-2">
                      <div className="flex min-w-0 flex-wrap items-center gap-2">
                        <h3 className={`truncate font-title-lg ${!notif.isRead ? "text-on-surface font-semibold" : "text-on-surface font-medium"}`}>
                          {notif.title}
                        </h3>
                        {badge && (
                          <span className={`shrink-0 rounded px-2 py-0.5 text-[10px] uppercase tracking-wider ${badge.badgeClass}`}>
                            {badge.badge}
                          </span>
                        )}
                      </div>
                      <span className="shrink-0 text-[11px] font-semibold text-on-surface-variant">
                        {formatTime(notif.createdAt)}
                      </span>
                    </div>

                    <p className="mb-4 font-body-md text-on-surface leading-relaxed">
                      {notif.message}
                    </p>
                  </div>

                  {!notif.isRead && (
                    <div className="absolute right-5 top-5 h-2.5 w-2.5 rounded-full bg-primary shadow-sm" />
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Load more */}
        <div className="flex justify-center">
          <button
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-6 py-2.5 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 shadow-sm"
            onClick={fetchNotifications}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">sync</span>
            Tải lại thông báo
          </button>
        </div>
      </div>
    </DashboardShell>
  );
}
