"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

interface BackButtonProps {
  href?: string;
  label?: string;
  variant?: "icon" | "full";
  className?: string;
}

/**
 * Back button - quay về trang trước hoặc href cụ thể.
 * Ưu tiên dùng `href` (static) hoặc `router.back()` (dynamic).
 */
export function BackButton({ href, label = "Quay lại", variant = "icon", className = "" }: BackButtonProps) {
  const router = useRouter();

  if (href) {
    return (
      <Link
        href={href}
        className={`inline-flex items-center gap-2 h-9 px-4 rounded-lg text-label-md font-medium
          text-on-surface-variant hover:text-primary hover:bg-primary-fixed/30 transition-colors cursor-pointer ${className}`}
      >
        <span className="material-symbols-outlined text-[18px]">arrow_back</span>
        {variant === "full" && <span>{label}</span>}
      </Link>
    );
  }

  return (
    <button
      type="button"
      onClick={() => router.back()}
      className={`inline-flex items-center gap-2 h-9 px-4 rounded-lg text-label-md font-medium
        text-on-surface-variant hover:text-primary hover:bg-primary-fixed/30 transition-colors cursor-pointer ${className}`}
    >
      <span className="material-symbols-outlined text-[18px]">arrow_back</span>
      {variant === "full" && <span>{label}</span>}
    </button>
  );
}
