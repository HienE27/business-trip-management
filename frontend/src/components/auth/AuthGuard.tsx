"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "./AuthProvider";

const PUBLIC_PATHS = ["/login"];

function AuthLoadingScreen({ message }: { message: string }) {
  return (
    <div className="grid min-h-screen place-items-center bg-surface">
      <div className="flex flex-col items-center gap-3">
        <svg className="size-8 animate-spin text-blue-800" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
          <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
        </svg>
        <p suppressHydrationWarning className="text-body-sm text-on-surface-variant">{message}</p>
      </div>
    </div>
  );
}

export function AuthGuard({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setMounted(true), 0);
    return () => clearTimeout(timer);
  }, []);

  const isPublicPage = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

  useEffect(() => {
    if (!mounted || isLoading) {
      return;
    }

    if (!isAuthenticated && !isPublicPage) {
      router.replace("/login");
      return;
    }

    if (isAuthenticated && isPublicPage) {
      router.replace("/");
      return;
    }
  }, [isAuthenticated, isLoading, isPublicPage, mounted, router]);

  if (!mounted || isLoading) {
    if (isPublicPage) {
      return <>{children}</>;
    }
    return <AuthLoadingScreen message="Đang kiểm tra xác thực…" />;
  }

  if (isPublicPage) {
    return isAuthenticated ? <AuthLoadingScreen message="Đang chuyển hướng…" /> : <>{children}</>;
  }

  if (!isAuthenticated) {
    return <AuthLoadingScreen message="Đang chuyển về trang đăng nhập…" />;
  }

  return <>{children}</>;
}
