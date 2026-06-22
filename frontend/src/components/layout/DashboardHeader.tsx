"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { UserMenu } from "@/components/layout/HeaderWidgets";
import { useNotifications } from "@/components/ui/NotificationContext";
import { NotificationCenter } from "@/components/ui/NotificationCenter";

function GlobalSearch() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [query, setQuery] = useState(() => searchParams.get("q") ?? "");
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleSearch = useCallback((value: string) => {
    setQuery(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      const params = new URLSearchParams(searchParams.toString());
      if (value.trim()) {
        params.set("q", value.trim());
      } else {
        params.delete("q");
      }
      router.push(`?${params.toString()}`, { scroll: false });
    }, 400);
  }, [router, searchParams]);

  useEffect(() => {
    const q = searchParams.get("q");
    setQuery(q ?? "");
  }, [searchParams]);

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  return (
    <div className="relative w-full max-w-64">
      <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]">
        search
      </span>
      <input
        className="w-full h-10 pl-10 pr-4 bg-surface-container-low border border-outline-variant rounded-lg text-label-md text-on-surface focus:bg-surface-container-lowest focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all placeholder:text-on-surface-variant"
        placeholder="Tìm kiếm lịch, nhân sự..."
        type="search"
        aria-label="Tìm kiếm toàn cục"
        name="global-search"
        autoComplete="off"
        value={query}
        onChange={(e) => handleSearch(e.target.value)}
      />
    </div>
  );
}

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
          className="p-3 text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary md:hidden shrink-0"
        >
            <span className="material-symbols-outlined text-[20px]">menu</span>
          </button>
        )}
        <Suspense fallback={
          <div className="relative w-full max-w-64">
            <div className="h-10 bg-surface-container-low border border-outline-variant rounded-lg animate-pulse" />
          </div>
        }>
          <GlobalSearch />
        </Suspense>
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
