"use client";

import Link from "next/link";
import { useAuth } from "./AuthProvider";

export function AuthStatus() {
  const { isAuthenticated, logout, user } = useAuth();

  if (!isAuthenticated) {
    return (
      <Link
        className="grid h-9 place-items-center rounded-md bg-slate-950 px-3 text-sm font-medium text-white shadow-sm max-sm:flex-1"
        href="/login"
      >
        Đăng nhập
      </Link>
    );
  }

  return (
    <div className="flex items-center gap-2 max-sm:w-full">
      <div className="min-w-0 rounded-md border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs max-sm:flex-1">
        <p className="truncate font-semibold text-slate-800">{user?.username}</p>
        <p className="truncate text-slate-500">{user?.roles.join(", ") || "Đã xác thực"}</p>
      </div>
      <button
        className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm"
        onClick={logout}
        type="button"
      >
        Đăng xuất
      </button>
    </div>
  );
}
