"use client";

import { useState } from "react";
import Link from "next/link";
import type { NavigationItem } from "@/types/schedule";
import { useAuth } from "@/components/auth/AuthProvider";

type AppSidebarProps = {
  items: NavigationItem[];
};

export function AppSidebar({ items }: AppSidebarProps) {
  const { user, logout } = useAuth();
  const [showConfirm, setShowConfirm] = useState(false);

  return (
    <aside className="border-r border-slate-200 bg-[#111418] text-white max-lg:hidden flex flex-col h-screen sticky top-0 justify-between z-30">
      <div>
        <div className="flex h-16 items-center gap-3 border-b border-white/10 px-5">
          <div className="grid size-9 place-items-center rounded-md bg-white text-sm font-bold text-slate-950">
            MS
          </div>
          <div>
            <p className="text-sm font-semibold">MedSchedule Pro</p>
            <p className="text-xs text-white/50">Clinical operations system</p>
          </div>
        </div>
        <nav className="space-y-1 px-3 py-4 text-sm">
          {items.map((item) => (
            <Link
              className={`flex h-10 items-center justify-between rounded-md px-3 ${
                item.active
                  ? "bg-white text-slate-950"
                  : "text-white/68 hover:bg-white/8 hover:text-white"
              }`}
              href={item.href}
              key={item.code}
            >
              <span>{item.label}</span>
              <span className={item.active ? "text-slate-500" : "text-white/35"}>
                {item.code}
              </span>
            </Link>
          ))}
        </nav>
      </div>

      {/* User profile card & Logout button at the bottom */}
      <div className="border-t border-white/10 p-4 space-y-3">
        {user && (
          <div className="flex items-center gap-3 px-1">
            <div className="size-8 rounded-full bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-semibold text-sm">
              {user.username.substring(0, 2).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-white truncate">{user.username}</p>
              <p className="text-[10px] text-white/50 truncate">
                {user.roles?.map(r => r.replace("ROLE_", "")).join(", ") || "STAFF"}
              </p>
            </div>
          </div>
        )}
        <button
          onClick={() => setShowConfirm(true)}
          className="flex w-full h-10 items-center justify-center gap-2 rounded-md border border-red-500/30 bg-red-950/15 hover:bg-red-950/30 hover:border-red-500/50 text-red-400 transition-all text-sm font-medium shadow-sm"
        >
          <svg className="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          <span>Đăng xuất</span>
        </button>
      </div>

      {/* Modern, professional confirmation dialog overlay */}
      {showConfirm && (
        <div className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm z-[100] flex items-center justify-center p-4">
          <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl max-w-sm w-full p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150 text-center">
            {/* Warning Icon */}
            <div className="mx-auto w-12 h-12 bg-red-50 dark:bg-red-950/20 text-red-500 dark:text-red-400 rounded-full flex items-center justify-center">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </div>
            
            {/* Confirmation details */}
            <div className="space-y-1.5">
              <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                Xác nhận đăng xuất
              </h3>
              <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed">
                Bạn có chắc chắn muốn đăng xuất khỏi hệ thống MedSchedule Pro?
              </p>
            </div>

            {/* Buttons */}
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowConfirm(false)}
                className="flex-1 h-10 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 font-medium text-sm hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
              >
                Hủy
              </button>
              <button
                onClick={() => {
                  setShowConfirm(false);
                  logout();
                }}
                className="flex-1 h-10 rounded-lg bg-red-600 hover:bg-red-700 text-white font-medium text-sm transition-colors shadow-sm"
              >
                Đăng xuất
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}

