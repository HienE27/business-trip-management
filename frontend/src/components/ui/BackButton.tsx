"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { IconButton } from "@/components/ui";

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
        aria-label={variant === "full" ? label : undefined}
        className={`inline-flex items-center gap-2 h-9 px-4 rounded-lg text-label-md font-medium
          text-on-surface-variant hover:text-blue-800 hover:bg-blue-100 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${className}`}
      >
        <span className="material-symbols-outlined text-[18px]" aria-hidden="true">arrow_back</span>
        {variant === "full" && <span>{label}</span>}
      </Link>
    );
  }

  if (variant === "icon") {
    return (
      <IconButton
        label={label}
        variant="ghost"
        size="sm"
        onClick={() => router.back()}
        className={`text-on-surface-variant hover:text-blue-800 hover:bg-blue-100 ${className}`}
      >
        <span className="material-symbols-outlined text-[18px]" aria-hidden="true">arrow_back</span>
      </IconButton>
    );
  }

  return (
    <button
      type="button"
      onClick={() => router.back()}
      aria-label={label}
      className={`inline-flex items-center gap-2 h-9 px-4 rounded-lg text-label-md font-medium
        text-on-surface-variant hover:text-blue-800 hover:bg-blue-100 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${className}`}
    >
      <span className="material-symbols-outlined text-[18px]" aria-hidden="true">arrow_back</span>
      <span>{label}</span>
    </button>
  );
}