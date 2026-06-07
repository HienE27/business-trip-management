"use client";

import Link from "next/link";
import { useAuth } from "@/components/auth/AuthProvider";
import { useNotifications } from "@/components/ui/NotificationContext";

export function NotificationBell() {
  const { unreadCount } = useNotifications();

  return (
    <Link
      aria-label={`Thông báo${unreadCount > 0 ? ` (${unreadCount} chưa đọc)` : ""}`}
      className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-on-surface-variant transition-colors hover:bg-surface-container-high hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
      href="/notifications"
    >
      <span aria-hidden="true" className="material-symbols-outlined text-[22px]">
        notifications
      </span>
      {unreadCount > 0 && (
        <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-error px-1 text-[10px] font-bold text-white">
          {unreadCount > 99 ? "99+" : unreadCount}
        </span>
      )}
    </Link>
  );
}

export function UserMenu() {
  const { user, logout } = useAuth();
  const displayName = user?.username || "Người dùng";
  const displayRole = user?.roles?.[0] || "STAFF";

  return (
    <div className="group relative">
      <button
        aria-expanded="false"
        aria-haspopup="menu"
        aria-label={`Tài khoản: ${displayName}`}
        className="flex h-10 items-center gap-2.5 rounded-xl px-3 transition-colors hover:bg-surface-container-high focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
        type="button"
      >
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-[13px] font-bold text-on-primary shadow-sm">
          {displayName.charAt(0).toUpperCase()}
        </div>
        <div className="hidden min-w-0 flex-col items-start lg:flex">
          <span className="max-w-[120px] truncate text-[13px] font-semibold leading-4 text-on-surface">
            {displayName}
          </span>
          <span className="text-[11px] leading-4 text-on-surface-variant">
            {displayRole}
          </span>
        </div>
        <span
          aria-hidden="true"
          className="material-symbols-outlined hidden text-[18px] text-on-surface-variant lg:block"
        >
          expand_more
        </span>
      </button>

      <div
        aria-orientation="vertical"
        role="menu"
        className="pointer-events-none absolute right-0 top-full z-50 mt-2 min-w-[200px] rounded-xl border border-outline-variant bg-surface-container-lowest opacity-0 shadow-lg transition-all group-focus-within:pointer-events-auto group-focus-within:opacity-100 group-hover:pointer-events-auto group-hover:opacity-100"
        tabIndex={-1}
      >
        <div className="border-b border-outline-variant/60 px-4 py-3">
          <p className="text-sm font-semibold text-on-surface">{displayName}</p>
          <p className="text-xs text-on-surface-variant">{displayRole}</p>
        </div>
        <div className="py-1">
          <Link
            className="flex items-center gap-3 px-4 py-2.5 text-sm text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:bg-surface-container-low"
            href="/profile"
            role="menuitem"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">person</span>
            Hồ sơ cá nhân
          </Link>
          <Link
            className="flex items-center gap-3 px-4 py-2.5 text-sm text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:bg-surface-container-low"
            href="/settings"
            role="menuitem"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">settings</span>
            Cài đặt
          </Link>
        </div>
        <div className="border-t border-outline-variant/60 py-1">
          <button
            className="flex w-full items-center gap-3 px-4 py-2.5 text-sm text-error transition-colors hover:bg-error-container/30 focus-visible:outline-none focus-visible:bg-error-container/30"
            onClick={logout}
            role="menuitem"
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">logout</span>
            Đăng xuất
          </button>
        </div>
      </div>
    </div>
  );
}
