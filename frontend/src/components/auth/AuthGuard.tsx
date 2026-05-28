"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "./AuthProvider";

const PUBLIC_PATHS = ["/login"];

export function AuthGuard({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const isPublicPage = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

  useEffect(() => {
    if (!isAuthenticated && !isPublicPage) {
      router.replace("/login");
    }
  }, [isAuthenticated, isPublicPage, router]);

  // On public pages (e.g. /login) always render
  if (isPublicPage) {
    return <>{children}</>;
  }

  // On protected pages, show nothing while redirecting
  if (!isAuthenticated) {
    return (
      <div className="grid min-h-screen place-items-center bg-slate-900">
        <div className="flex flex-col items-center gap-3">
          <svg className="size-8 animate-spin text-indigo-400" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
            <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
          </svg>
          <p className="text-sm text-white/50">Đang kiểm tra xác thực…</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
