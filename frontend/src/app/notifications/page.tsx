"use client";

import { useCallback, useEffect, useState } from "react";
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
    if (diffMins < 1) return "Vừa xong";
    if (diffMins < 60) return `${diffMins} phút trước`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} giờ trước`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays} ngày trước`;
    return date.toLocaleDateString("vi-VN");
  } catch {
    return dateStr;
  }
}

function getNotificationIcon(title: string) {
  const lower = title.toLowerCase();
  if (lower.includes("xung đột") || lower.includes("conflict") || lower.includes("cảnh báo"))
    return { icon: "warning", wrapClass: "bg-error-container/40 text-error border border-error/20" };
  if (lower.includes("đổi trực") || lower.includes("swap") || lower.includes("đổi ca"))
    return { icon: "swap_horiz", wrapClass: "bg-primary-fixed text-primary border border-primary/20" };
  if (lower.includes("công bố") || lower.includes("published") || lower.includes("lich"))
    return { icon: "event_available", wrapClass: "bg-secondary-container text-secondary border border-secondary/20" };
  if (lower.includes("tự động") || lower.includes("auto"))
    return { icon: "auto_mode", wrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant/30" };
  return { icon: "notifications", wrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant" };
}

function getBadge(title: string) {
  const lower = title.toLowerCase();
  if (lower.includes("24/24")) return { badge: "24/24", badgeClass: "bg-primary-fixed text-primary font-bold" };
  if (lower.includes("dịch vụ")) return { badge: "DV", badgeClass: "bg-secondary-container text-secondary font-bold" };
  if (lower.includes("chuyên gia")) return { badge: "CG", badgeClass: "bg-tertiary-fixed text-on-tertiary font-bold" };
  return null;
}

export default function NotificationsPage() {
  const { user } = useAuth();
  const userId = user?.userId ?? null;
  const { refreshCount } = useNotifications();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<NotifTab>("all");
  const [message, setMessage] = useState("");
  const [markingAll, setMarkingAll] = useState(false);

  const fetchNotifications = useCallback(async () => {
    if (!userId) {
      setNotifications([]);
      await refreshCount(0);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setMessage("");
      const res = await api.get<Notification[]>(`/notifications/staff/${userId}`);
      setNotifications(res ?? []);
      await refreshCount((res ?? []).filter((n) => !n.isRead).length);
    } catch (err) {
      setNotifications([]);
      await refreshCount(0);
      setMessage(getErrorMessage(err, "Không thể tải thông báo."));
    } finally {
      setLoading(false);
    }
  }, [refreshCount, userId]);

  useEffect(() => {
    void fetchNotifications();
  }, [fetchNotifications]);

  const filtered = notifications.filter((n) => {
    if (activeTab === "all") return true;
    if (activeTab === "unread") return !n.isRead;
    if (activeTab === "conflict") return n.title.toLowerCase().includes("xung") || n.title.toLowerCase().includes("conflict");
    if (activeTab === "exchange") return n.title.toLowerCase().includes("đổi") || n.title.toLowerCase().includes("swap");
    if (activeTab === "published") return n.title.toLowerCase().includes("công bố") || n.title.toLowerCase().includes("lich");
    if (activeTab === "system") return n.title.toLowerCase().includes("tự động") || n.title.toLowerCase().includes("auto");
    return true;
  });

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  async function handleMarkAsRead(id: number) {
    try {
      await api.put(`/notifications/${id}/read`, {});
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
      );
      await refreshCount(Math.max(0, unreadCount - 1));
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi đánh dấu đã đọc."));
    }
  }

  async function handleMarkAllAsRead() {
    if (!userId) return;
    try {
      setMarkingAll(true);
      await api.put(`/notifications/staff/${userId}/read-all`, {});
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      await refreshCount(0);
      setMessage("Đã đánh dấu tất cả là đã đọc.");
      setTimeout(() => setMessage(""), 3000);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi đánh dấu tất cả."));
    } finally {
      setMarkingAll(false);
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("Xóa thông báo này?")) return;
    try {
      await api.delete(`/notifications/${id}`);
      const deleted = notifications.find((n) => n.id === id);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      if (deleted && !deleted.isRead) {
        await refreshCount(Math.max(0, unreadCount - 1));
      }
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi xóa thông báo."));
    }
  }

  return (
    <DashboardShell
      activeSection="notifications"
      description="Quản lý và theo dõi các luồng thông tin hệ thống, cảnh báo xếp lịch."
      title="Trung tâm Thông báo"
    >
      <div className="flex flex-col gap-6 pb-8">
        {/* Header bar */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-3">
            {unreadCount > 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-error px-3 py-1 text-[12px] font-bold text-on-error">
                <span className="h-2 w-2 rounded-full bg-white animate-pulse" />
                {unreadCount} chưa đọc
              </span>
            )}
            {unreadCount === 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary-container px-3 py-1 text-[12px] font-bold text-secondary">
                <span className="material-symbols-outlined text-[14px]">check</span>
                Tất cả đã đọc
              </span>
            )}
          </div>
          {unreadCount > 0 && (
            <button
              className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-[13px] font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low disabled:opacity-50"
              disabled={markingAll}
              onClick={handleMarkAllAsRead}
              type="button"
            >
              {markingAll ? (
                <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              ) : (
                <span className="material-symbols-outlined text-[18px]">done_all</span>
              )}
              Đánh dấu tất cả đã đọc
            </button>
          )}
        </div>

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
          <div className="rounded-lg border border-primary/20 bg-primary-container/30 px-4 py-3 text-sm text-on-surface">
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
            <button
              className="mt-4 inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-[13px] font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low"
              onClick={() => void fetchNotifications()}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">sync</span>
              Tải lại
            </button>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {filtered.map((notif) => {
              const { icon, wrapClass } = getNotificationIcon(notif.title);
              const badge = getBadge(notif.title);
              return (
                <div
                  className={`group relative flex gap-4 rounded-lg border bg-surface-container-lowest p-5 transition-all hover:bg-surface-container-low ${
                    !notif.isRead ? "border-primary/20 ring-1 ring-primary/10" : "border-outline-variant"
                  }`}
                  key={notif.id}
                >
                  {!notif.isRead && (
                    <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary rounded-l-lg" />
                  )}

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
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-[11px] font-semibold text-on-surface-variant">
                          {formatTime(notif.createdAt)}
                        </span>
                        {!notif.isRead && (
                          <span className="h-2.5 w-2.5 rounded-full bg-primary shadow-sm" />
                        )}
                      </div>
                    </div>

                    <p className="mb-3 font-body-md text-on-surface leading-relaxed">
                      {notif.message}
                    </p>

                    {/* Actions */}
                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      {!notif.isRead && (
                        <button
                          className="flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-[12px] font-medium text-on-surface transition-colors hover:bg-surface-container"
                          onClick={() => handleMarkAsRead(notif.id)}
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[14px]">check</span>
                          Đánh dấu đã đọc
                        </button>
                      )}
                      <button
                        className="flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-[12px] font-medium text-error transition-colors hover:bg-error-container"
                        onClick={() => handleDelete(notif.id)}
                        type="button"
                      >
                        <span className="material-symbols-outlined text-[14px]">delete</span>
                        Xóa
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Refresh button */}
        <div className="flex justify-center">
          <button
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-6 py-2.5 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 shadow-sm"
            onClick={() => void fetchNotifications()}
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
