"use client";

import Link from "next/link";
import { useAuth } from "./AuthProvider";

export function AuthStatus() {
  const { isAuthenticated, logout, user } = useAuth();

  if (!isAuthenticated) {
    return (
      <Link
        className="grid h-9 place-items-center rounded-lg bg-[#111418] px-3 text-sm font-medium text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)] max-sm:flex-1"
        href="/login"
      >
        Đăng nhập
      </Link>
    );
  }

  return (
    <div className="flex items-center gap-2 max-sm:w-full">
      <div className="min-w-0 rounded-lg border border-[#dfe4ea] bg-[#f8fafc] px-3 py-1.5 text-xs max-sm:flex-1">
        <p className="truncate font-semibold leading-4 text-[#111418]">{user?.username}</p>
        <p className="truncate leading-4 text-[#667085]">{user?.roles.join(", ") || "Đã xác thực"}</p>
      </div>
      <button
        className="h-9 rounded-lg border border-[#dfe4ea] bg-white px-3 text-sm font-medium text-[#364152] shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
        onClick={logout}
        type="button"
      >
        Đăng xuất
      </button>
    </div>
  );
}
