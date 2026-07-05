"use client";

import { useState, type ReactNode } from "react";

/**
 * Collapsible section card với header clickable để expand/collapse.
 * Dùng cho các section phụ không quan trọng bậc nhất (vd: Ngoại lệ nhân sự,
 * Bộ lọc nâng cao, Debug info…). State collapse KHÔNG persist qua reload.
 *
 * Pattern: Material surface, header có thể click toàn bộ, button "thu gọn/mở rộng"
 * ở góc phải để click chính xác hơn (a11y).
 */
type CollapsibleCardProps = {
  title: string;
  subtitle?: string;
  icon?: string;
  iconBgClass?: string;
  iconTextClass?: string;
  defaultExpanded?: boolean;
  /** Compact badge hiển thị trên header (vd: "21 NS · 3 loại trừ") */
  summary?: ReactNode;
  children: ReactNode;
};

export function CollapsibleCard({
  title,
  subtitle,
  icon,
  iconBgClass = "bg-primary-fixed",
  iconTextClass = "text-primary",
  defaultExpanded = false,
  summary,
  children,
}: CollapsibleCardProps) {
  const [isOpen, setIsOpen] = useState(defaultExpanded);
  const toggle = () => setIsOpen((v) => !v);

  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={isOpen}
        aria-controls="collapsible-card-body"
        className={`w-full flex items-center gap-3 px-4 py-3 bg-surface-container-low border-b border-outline-variant cursor-pointer select-none transition-colors text-left ${
          isOpen ? "border-outline-variant" : "border-transparent"
        } hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30`}
      >
        {icon && (
          <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${iconBgClass}`}>
            <span className={`material-symbols-outlined text-[18px] ${iconTextClass}`} aria-hidden="true">
              {icon}
            </span>
          </div>
        )}
        <div className="min-w-0 flex-1">
          <p className="text-title-sm font-semibold text-on-surface truncate">{title}</p>
          {subtitle && (
            <p className="text-label-xs text-on-surface-variant truncate">{subtitle}</p>
          )}
        </div>
        {summary && <div className="shrink-0">{summary}</div>}
        <span
          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-on-surface-variant transition-transform duration-200 ${isOpen ? "rotate-180" : "rotate-0"}`}
          aria-hidden="true"
        >
          <span className="material-symbols-outlined text-[18px]">expand_more</span>
        </span>
      </button>
      {isOpen && (
        <div id="collapsible-card-body" className="animate-fade-in">
          {children}
        </div>
      )}
    </div>
  );
}