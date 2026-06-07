"use client";

import Link from "next/link";
import { useAuth } from "@/components/auth/AuthProvider";
import { useNotifications } from "@/components/ui/NotificationContext";

type DashboardHeaderProps = {
  title: string;
  description: string;
  primaryAction?: string;
  secondaryAction?: string;
};

export function DashboardHeader({
  title,
  description,
  primaryAction,
  secondaryAction,
}: DashboardHeaderProps) {
  const { user, logout } = useAuth();
  const { unreadCount } = useNotifications();
  const displayName = user?.username || "Nguoi dung";

  return (
    <header className="sticky top-0 z-40 border-b border-outline-variant bg-surface-bright shadow-sm h-[60px] flex items-center justify-between px-6 shrink-0">
      {/* Left: Page title */}
      <div className="flex-1 min-w-0 mr-6">
        <h1 className="font-headline-md text-on-surface truncate">
          {title}
        </h1>
        {description && (
          <p className="font-label-sm text-on-surface-variant uppercase tracking-wider truncate">
            {description}
          </p>
        )}
      </div>

      {/* Right: Actions + User */}
      <div className="flex shrink-0 items-center gap-1">
        {/* Notification Bell */}
        <Link
          aria-label={`Thong bao${unreadCount > 0 ? ` (${unreadCount} chua doc)` : ""}`}
          className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container-high focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
          href="/notifications"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-[22px]">
            notifications
          </span>
          {unreadCount > 0 && (
            <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-error border border-surface-bright" />
          )}
        </Link>

        {/* Divider */}
        <div className="h-6 w-px bg-outline-variant mx-1" />

        {/* User Menu */}
        <div className="group relative">
          <button
            aria-expanded="false"
            aria-haspopup="menu"
            aria-label={`Tai khoan: ${displayName}`}
            className="flex items-center gap-2 rounded-full px-1 py-1 transition-colors hover:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 cursor-pointer"
            type="button"
          >
            <div className="h-8 w-8 shrink-0 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-sm overflow-hidden border border-outline-variant/30">
              {user?.avatar ? (
                <img alt={displayName} className="w-full h-full object-cover" src={user.avatar} />
              ) : (
                displayName.charAt(0).toUpperCase()
              )}
            </div>
            <div className="hidden lg:flex flex-col items-start">
              <span className="font-label-md text-on-surface leading-none">{displayName}</span>
              <span className="font-label-sm text-on-surface-variant">{user?.roles?.[0] || "STAFF"}</span>
            </div>
            <span aria-hidden="true" className="material-symbols-outlined text-[20px] text-on-surface-variant hidden lg:block">
              expand_more
            </span>
          </button>

          <div
            aria-orientation="vertical"
            role="menu"
            className="pointer-events-none absolute right-0 top-full z-50 mt-2 min-w-[200px] rounded-xl border border-outline-variant bg-surface-container-lowest opacity-0 shadow-lg transition-all group-focus-within:pointer-events-auto group-focus-within:opacity-100 group-hover:pointer-events-auto group-hover:opacity-100"
            tabIndex={-1}
          >
            <div className="border-b border-outline-variant px-4 py-3">
              <p className="font-label-md text-on-surface">{displayName}</p>
              <p className="font-label-sm text-on-surface-variant uppercase tracking-wider mt-0.5">{user?.roles?.[0] || "STAFF"}</p>
            </div>
            <div className="py-1">
              <Link
                className="flex items-center gap-3 px-4 py-2.5 font-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none"
                href="/profile"
                role="menuitem"
              >
                <span aria-hidden="true" className="material-symbols-outlined text-[18px]">person</span>
                Ho so ca nhan
              </Link>
              <Link
                className="flex items-center gap-3 px-4 py-2.5 font-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none"
                href="/settings"
                role="menuitem"
              >
                <span aria-hidden="true" className="material-symbols-outlined text-[18px]">settings</span>
                Cai dat
              </Link>
            </div>
            <div className="border-t border-outline-variant py-1">
              <button
                className="flex w-full items-center gap-3 px-4 py-2.5 font-label-md text-error transition-colors hover:bg-error-container/30 focus-visible:outline-none"
                onClick={logout}
                role="menuitem"
                type="button"
              >
                <span aria-hidden="true" className="material-symbols-outlined text-[18px]">logout</span>
                Dang xuat
              </button>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
