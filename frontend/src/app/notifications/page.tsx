"use client";

import { useEffect, useRef, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { useNotifications } from "@/components/ui/NotificationContext";

const TABS = [
  { label: "Tat ca", active: true },
  { label: "Chua doc", badge: "2" },
  { label: "Canh bao xung dot", dotClass: "bg-error-container border border-error/20" },
  { label: "Yeu cau doi truc", dotClass: "bg-secondary-container border border-secondary/20" },
  { label: "Lich da cong bo" },
  { label: "He thong", dotClass: "bg-primary-fixed border border-primary/20" },
];

const LATEST_NOTIFICATIONS = [
  {
    title: "Xung dot ca truc: Khoa Cap cuu - Khoa Noi",
    badge: "24/24",
    badgeClass: "bg-primary-fixed text-on-primary-fixed-variant font-bold",
    time: "10 phut truoc",
    timeClass: "text-error font-semibold",
    description: "He thong phat hien BS. Nguyen Van A duoc phan cong ca truc tai Khoa Cap cuu (08:00 - 16:00, 24/10) trung lap voi lich phau thuat tai Khoa Noi.",
    icon: "warning",
    iconWrapClass: "bg-error-container/40 text-error border border-error-container",
    unread: true,
    cta: ["Giai quyet ngay", "Xem chi tiet"] as string[],
  },
  {
    title: "Yeu cau doi truc: DD. Tran Thi B",
    badge: "Full-time",
    badgeClass: "bg-secondary-container text-secondary font-bold",
    time: "1 gio truoc",
    timeClass: "text-primary font-semibold",
    description: "DD. Tran Thi B (Khoa Nhi) yeu cau doi ca truc dem (25/10) voi DD. Le Van C. Ly do: Viec gia dinh dot xuat.",
    icon: "swap_horiz",
    iconWrapClass: "bg-primary-container text-on-primary-container border border-primary",
    unread: true,
    cta: ["Phe duyet", "Tu choi"] as string[],
  },
  {
    title: "Da cong bo Lich truc tuan 42 (Khoa Ngoai)",
    badge: "Service",
    badgeClass: "bg-secondary-container text-secondary font-bold",
    time: "3 gio truoc",
    timeClass: "text-on-surface-variant font-semibold",
    description: "Lich truc tuan 42 (tu 23/10 den 29/10) cua Khoa Ngoai da duoc Truong khoa phe duyet va cong bo thanh cong. 45 nhan su da nhan duoc thong bao.",
    icon: "event_available",
    iconWrapClass: "bg-secondary-container text-secondary border border-secondary-container",
    unread: false,
    cta: ["Xem lich da cong bo"] as string[],
  },
  {
    title: "Hoan tat tu dong xep lich du thao thang 11",
    badge: "Auto",
    badgeClass: "bg-primary-container text-on-primary-container font-bold",
    time: "23/10, 18:30",
    timeClass: "text-on-surface-variant font-semibold",
    description: "Module tu dong da tao xong ban nhap lich truc thang 11/2023 cho toan vien voi ty le toi uu 92%. Vui long kiem tra va dieu chinh thu cong truoc khi gui phe duyet.",
    icon: "auto_mode",
    iconWrapClass: "bg-surface-container-high text-on-surface-variant border border-outline-variant/30",
    unread: false,
    cta: ["Den man hinh tinh chinh"] as string[],
  },
];

type NotifType = (typeof LATEST_NOTIFICATIONS)[number];

function NotificationCard({ notification }: { notification: NotifType }) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!isMenuOpen) return;

    function handlePointerDown(e: MouseEvent) {
      if (!menuRef.current?.contains(e.target as Node)) {
        setIsMenuOpen(false);
      }
    }

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setIsMenuOpen(false);
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isMenuOpen]);

  const unreadFill = notification.unread ? "fill" : "";

  return (
    <div
      className={`group relative flex gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest p-5 transition-all hover:bg-surface-container-low hover:shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] ${
        notification.unread ? "ring-1 ring-primary/10" : ""
      }`}
    >
      {notification.unread && (
        <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary" />
      )}

      <div
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg shadow-sm ${notification.iconWrapClass}`}
      >
        <span aria-hidden="true" className={`material-symbols-outlined text-[20px] ${unreadFill}`}>
          {notification.icon}
        </span>
      </div>

      <div className="min-w-0 flex-1 pr-6">
        <div className="mb-1.5 flex items-start justify-between gap-2">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <h3
              className={`truncate font-title-lg ${
                notification.unread ? "text-on-surface font-semibold" : "text-on-surface font-medium"
              }`}
            >
              {notification.title}
            </h3>
            {notification.badge && (
              <span className={`shrink-0 rounded px-2 py-0.5 text-[10px] uppercase tracking-wider ${notification.badgeClass}`}>
                {notification.badge}
              </span>
            )}
          </div>
          <span className={`shrink-0 text-[11px] font-semibold ${notification.timeClass ?? "text-on-surface-variant"}`}>
            {notification.time}
          </span>
        </div>

        <p className="mb-4 font-body-md text-on-surface leading-relaxed">
          {notification.description}
        </p>

        {notification.cta && notification.cta.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {notification.cta.map((label, idx) => (
              <button
                className={`rounded-lg px-4 py-2 text-label-md shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 ${
                  idx === 0
                    ? "bg-primary text-on-primary hover:opacity-90"
                    : "border border-outline-variant bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container-low"
                }`}
                key={label}
                type="button"
              >
                {label}
              </button>
            ))}
          </div>
        )}
      </div>

      {notification.unread && (
        <div className="absolute right-5 top-5 h-2.5 w-2.5 rounded-full bg-primary shadow-sm" />
      )}
    </div>
  );
}

export default function NotificationsPage() {
  const { refreshCount } = useNotifications();

  useEffect(() => {
    refreshCount(LATEST_NOTIFICATIONS.filter((n) => n.unread).length);
  }, [refreshCount]);

  return (
    <DashboardShell
      activeCode="M06-NOTIFICATIONS"
      description="Quan ly va theo doi cac luong thong tin he thong, canh bao xep lich."
      title="Trung tam Thong bao"
    >
      <div className="flex flex-col gap-6 pb-8">
        {/* Filter tabs */}
        <div className="flex gap-2 overflow-x-auto pb-1 hide-scrollbar">
          {TABS.map((tab) => (
            <button
              className={`flex items-center gap-2 whitespace-nowrap rounded-lg px-4 py-2 text-label-md shadow-sm transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 ${
                tab.active
                  ? "bg-primary text-on-primary font-semibold"
                  : "border border-outline-variant/50 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container-low"
              }`}
              key={tab.label}
              type="button"
            >
              {tab.dotClass ? (
                <span className={`h-2 w-2 rounded-full ${tab.dotClass}`} />
              ) : null}
              {tab.label}
              {tab.badge ? (
                <span className="rounded-full bg-error px-1.5 py-0.5 text-[10px] font-bold text-on-error leading-tight">
                  {tab.badge}
                </span>
              ) : null}
            </button>
          ))}
        </div>

        <div className="flex flex-col gap-8">
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-4">
              <span className="text-label-sm text-on-surface-variant uppercase tracking-widest">
                Hom nay
              </span>
              <div className="h-px flex-1 bg-outline-variant/50" />
            </div>
            <div className="flex flex-col gap-3">
              {LATEST_NOTIFICATIONS.map((n) => (
                <NotificationCard key={n.title} notification={n} />
              ))}
            </div>
          </div>

      {/* Load more */}
      <div className="flex justify-center">
        <button
          className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-6 py-2.5 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 shadow-sm"
          type="button"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-[18px]">sync</span>
          Tai them thong bao
        </button>
      </div>
        </div>
      </div>
    </DashboardShell>
  );
}
