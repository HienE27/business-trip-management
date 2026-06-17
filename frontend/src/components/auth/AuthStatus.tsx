"use client";

import Link from "next/link";
import { useAuth } from "./AuthProvider";

export function AuthStatus() {
  const { isAuthenticated, logout, user } = useAuth();

  if (!isAuthenticated) {
    return (
      <Link
        className="grid h-9 place-items-center rounded-lg bg-primary px-3 text-label-md font-medium text-on-primary shadow-sm max-sm:flex-1"
        href="/login"
      >
        Đăng nhập
      </Link>
    );
  }

  return (
    <div className="flex items-center gap-2 max-sm:w-full">
      <div className="min-w-0 rounded-lg border border-outline-variant bg-surface-container-low px-3 py-1.5 text-label-sm max-sm:flex-1">
        <p className="truncate font-semibold text-on-surface">{user?.username}</p>
        <p className="truncate text-on-surface-variant">{user?.roles.join(", ") || "Đã xác thực"}</p>
      </div>
      <button
        className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md font-medium text-on-surface shadow-sm hover:bg-surface-container-low transition-colors"
        onClick={logout}
        type="button"
      >
        Đăng xuất
      </button>
    </div>
  );
}
