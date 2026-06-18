import type React from "react";

/* ── Empty State Component ──
 *
 * Design tokens: bg-surface-container-low, primary, on-surface-variant
 * Icon: Material Symbols Outlined
 * Animation: subtle scale-in on mount
 * Accessibility: role="status", aria-live="polite", heading level, action focus
 *
 * Usage:
 *   <EmptyState icon="inbox" title="Không có dữ liệu" description="..." />
 *   <EmptyState icon="search_off" title="Không tìm thấy" action={<Button />} />
 */

type EmptyStateProps = {
  icon?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
  /** Render title as h2 (default) or h3 for nested contexts */
  headingLevel?: "h2" | "h3" | "h4";
};

export function EmptyState({
  icon = "inbox",
  title,
  description,
  action,
  className = "",
  headingLevel: Heading = "h2",
}: EmptyStateProps) {
  return (
    <div
      className={`flex flex-col items-center justify-center py-16 text-center empty-state-fade-in ${className}`}
      role="status"
      aria-live="polite"
    >
      {/* Icon container */}
      <div
        className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-surface-container-low text-on-surface-variant shadow-sm"
        aria-hidden="true"
      >
        <span className="material-symbols-outlined text-[36px]">
          {icon}
        </span>
      </div>

      {/* Title — semantic heading */}
      <Heading className="text-title-lg text-on-surface font-semibold">
        {title}
      </Heading>

      {/* Description */}
      {description ? (
        <p className="mt-2 max-w-xs text-body-sm text-on-surface-variant leading-relaxed">
          {description}
        </p>
      ) : null}

      {/* Action slot — ensure focusable element gets visible focus ring */}
      {action ? (
        <div className="mt-6">{action}</div>
      ) : null}
    </div>
  );
}
