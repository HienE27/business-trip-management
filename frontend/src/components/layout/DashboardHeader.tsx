"use client";

import Link from "next/link";
import { UserMenu } from "@/components/layout/HeaderWidgets";
import { useNotifications } from "@/components/ui/NotificationContext";
import { NotificationCenter } from "@/components/ui/NotificationCenter";

type DashboardHeaderProps = {
  title: string;
  description: string;
  onMenuToggle?: () => void;
};

export function DashboardHeader({ title, description, onMenuToggle }: DashboardHeaderProps) {
  const { notifications, markAllRead } = useNotifications();

  return (
    <header className="sticky top-0 z-40 h-16 border-b border-outline-variant bg-surface-bright shadow-sm flex items-center justify-between px-6 shrink-0">
      {/* Left */}
      <div className="flex-1 flex items-center gap-3 min-w-0">
        {onMenuToggle && (
          <button
            aria-label="Menu"
            onClick={onMenuToggle}
            className="p-2 text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary md:hidden"
          >
            <span className="material-symbols-outlined text-[20px]">menu</span>
          </button>
        )}
        <div className="relative w-40 md:w-64 lg:w-96">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]">
            search
          </span>
          <input
            className="w-full pl-10 pr-4 py-2 bg-surface-container-low border border-outline-variant rounded-lg text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors placeholder:text-on-surface-variant"
            placeholder="Tìm kiếm lịch, nhân sự..."
            type="search"
            aria-label="Tìm kiếm"
            name="global-search"
            autoComplete="off"
          />
        </div>
      </div>

      {/* Right */}
      <div className="flex shrink-0 items-center gap-2">
        {/* Notification Center */}
        <NotificationCenter notifications={notifications} maxCount={5} onMarkAllRead={markAllRead} />

        {/* Settings */}
        <Link
          aria-label="Cài đặt"
          className="p-2 text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          href="/settings"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-[20px]">
            settings
          </span>
        </Link>

        {/* User menu */}
        <UserMenu />
      </div>
    </header>
  );
}
