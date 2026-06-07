"use client";

import {
  createContext,
  useCallback,
  useContext,
  useState,
  useEffect,
  type ReactNode,
} from "react";

type NotificationContextType = {
  unreadCount: number;
  markAllRead: () => void;
  markRead: (id: string) => void;
  refreshCount: (count: number) => void;
};

const NotificationContext = createContext<NotificationContextType | null>(null);

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    const saved = localStorage.getItem("notif.unreadCount");
    if (saved) {
      const n = parseInt(saved, 10);
      if (!isNaN(n)) setUnreadCount(n);
    }
  }, []);

  const refreshCount = useCallback((count: number) => {
    setUnreadCount(count);
    localStorage.setItem("notif.unreadCount", String(count));
  }, []);

  const markAllRead = useCallback(() => {
    setUnreadCount(0);
    localStorage.setItem("notif.unreadCount", "0");
  }, []);

  const markRead = useCallback((_id: string) => {
    setUnreadCount((prev) => {
      const next = Math.max(0, prev - 1);
      localStorage.setItem("notif.unreadCount", String(next));
      return next;
    });
  }, []);

  return (
    <NotificationContext.Provider value={{ unreadCount, markAllRead, markRead, refreshCount }}>
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
