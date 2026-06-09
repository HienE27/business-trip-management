"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAuth } from "@/components/auth/AuthProvider";
import type { Notification } from "@/types/api";

export type NotificationItem = {
  id: string;
  icon: string;
  iconColor: string;
  title: string;
  detail: string;
  time: string;
  unread?: boolean;
};

type NotificationContextType = {
  unreadCount: number;
  notifications: NotificationItem[];
  loading: boolean;
  error: string;
  markAllRead: () => Promise<void>;
  markRead: (id: string) => Promise<void>;
  refreshCount: (count?: number) => Promise<void>;
};

const NotificationContext = createContext<NotificationContextType | null>(null);

function formatRelativeTime(dateStr: string) {
  try {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 60) return `${Math.max(diffMins, 0)} phút trước`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} giờ trước`;
    return date.toLocaleDateString("vi-VN");
  } catch {
    return dateStr;
  }
}

function toNotificationItem(notification: Notification): NotificationItem {
  const lower = notification.title.toLowerCase();

  if (lower.includes("xung đột") || lower.includes("conflict")) {
    return {
      id: String(notification.id),
      icon: "warning",
      iconColor: "bg-error/10 text-error",
      title: notification.title,
      detail: notification.message,
      time: formatRelativeTime(notification.createdAt),
      unread: !notification.isRead,
    };
  }

  if (lower.includes("đổi trực") || lower.includes("swap")) {
    return {
      id: String(notification.id),
      icon: "swap_horiz",
      iconColor: "bg-primary/10 text-primary",
      title: notification.title,
      detail: notification.message,
      time: formatRelativeTime(notification.createdAt),
      unread: !notification.isRead,
    };
  }

  if (lower.includes("nghỉ phép")) {
    return {
      id: String(notification.id),
      icon: "event_busy",
      iconColor: "bg-tertiary/10 text-tertiary",
      title: notification.title,
      detail: notification.message,
      time: formatRelativeTime(notification.createdAt),
      unread: !notification.isRead,
    };
  }

  if (lower.includes("công bố") || lower.includes("published")) {
    return {
      id: String(notification.id),
      icon: "event_available",
      iconColor: "bg-secondary/10 text-secondary",
      title: notification.title,
      detail: notification.message,
      time: formatRelativeTime(notification.createdAt),
      unread: !notification.isRead,
    };
  }

  return {
    id: String(notification.id),
    icon: "notifications",
    iconColor: "bg-surface-container-high text-on-surface-variant",
    title: notification.title,
    detail: notification.message,
    time: formatRelativeTime(notification.createdAt),
    unread: !notification.isRead,
  };
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const userId = user?.userId;
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadNotifications = useCallback(async () => {
    if (!userId) {
      setNotifications([]);
      return;
    }

    try {
      setLoading(true);
      setError("");
      const res = await api.get<Notification[]>(`/notifications/staff/${userId}`);
      setNotifications(res ?? []);
    } catch (err) {
      setNotifications([]);
      setError(getErrorMessage(err, "Không thể tải thông báo."));
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications]);

  const refreshCount = useCallback(async (count?: number) => {
    if (typeof count === "number") {
      setNotifications((prev) => {
        const unreadIndexes = prev
          .map((item, index) => (!item.isRead ? index : -1))
          .filter((index) => index >= 0);

        return prev.map((item, index) => ({
          ...item,
          isRead: !unreadIndexes.slice(0, count).includes(index),
        }));
      });
      return;
    }

    await loadNotifications();
  }, [loadNotifications]);

  const markRead = useCallback(async (id: string) => {
    await api.put(`/notifications/${id}/read`, {});
    setNotifications((prev) => prev.map((item) => item.id === Number(id) ? { ...item, isRead: true } : item));
  }, []);

  const markAllRead = useCallback(async () => {
    if (!userId) return;
    await api.put(`/notifications/staff/${userId}/read-all`, {});
    setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })));
  }, [userId]);

  const notificationItems = useMemo(
    () => notifications.map(toNotificationItem),
    [notifications]
  );

  const unreadCount = useMemo(
    () => notifications.filter((item) => !item.isRead).length,
    [notifications]
  );

  return (
    <NotificationContext.Provider
      value={{
        unreadCount,
        notifications: notificationItems,
        loading,
        error,
        markAllRead,
        markRead,
        refreshCount,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) {
    throw new Error("useNotifications must be inside NotificationProvider");
  }
  return ctx;
}
