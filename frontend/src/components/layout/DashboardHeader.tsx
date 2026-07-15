"use client";

import Link from "next/link";
import { UserMenu } from "@/components/layout/HeaderWidgets";
import { useNotifications } from "@/components/ui/NotificationContext";
import { NotificationCenter } from "@/components/ui/NotificationCenter";

export function DashboardHeader(props: {
  title: string;
  description: string;
  onMenuToggle?: () => void;
  mobileOpen?: boolean;
}) {
  const { notifications, markAllRead } = useNotifications();

  return (
    <header className="sticky top-0 z-40 h-16 border-b border-outline-variant bg-surface-container-low shadow-sm flex items-center justify-between px-4 md:px-6 shrink-0 gap-4">
      {/* Left */}
      <div className="flex-1 flex items-center gap-2 md:gap-3 min-w-0">
        {props.onMenuToggle && (
        <button
          aria-expanded={props.mobileOpen}
          aria-controls="app-sidebar"
          aria-label="Mở menu điều hướng"
          onClick={props.onMenuToggle}
          className="p-3 text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary lg:hidden shrink-0"
        >
            <span className="material-symbols-outlined text-[20px]">menu</span>
          </button>
        )}
        <div className="flex-1" />
      </div>

      {/* Right */}
      <div className="flex shrink-0 items-center gap-1">
        <NotificationCenter
          notifications={notifications}
          maxCount={5}
          onMarkAllRead={markAllRead}
        />
        <Link
          aria-label="Cài đặt"
          className="p-3 text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          href="/settings"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-[20px]">
            settings
          </span>
        </Link>
        <UserMenu />
      </div>
    </header>
  );
}
