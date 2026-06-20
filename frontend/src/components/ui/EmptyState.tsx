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
  /** Compact variant for nested contexts (modals, side panels, list slots) */
  size?: "default" | "compact";
};

export function EmptyState({
  icon = "inbox",
  title,
  description,
  action,
  className = "",
  headingLevel: Heading = "h2",
  size = "default",
}: EmptyStateProps) {
  const isCompact = size === "compact";

  return (
    <div
      className={`flex flex-col items-center justify-center text-center empty-state-fade-in ${
        isCompact ? "py-6" : "py-16"
      } ${className}`}
      role="status"
      aria-live="polite"
    >
      {/* Icon container */}
      <div
        className={`flex items-center justify-center rounded-2xl bg-surface-container-low text-on-surface-variant shadow-sm ${
          isCompact ? "mb-2 h-10 w-10" : "mb-5 h-16 w-16"
        }`}
        aria-hidden="true"
      >
        <span className={`material-symbols-outlined ${isCompact ? "text-[22px]" : "text-[36px]"}`}>
          {icon}
        </span>
      </div>

      {/* Title — semantic heading */}
      <Heading
        className={`text-on-surface font-semibold ${
          isCompact ? "text-label-md" : "text-title-lg"
        }`}
      >
        {title}
      </Heading>

      {/* Description */}
      {description ? (
        <p
          className={`text-on-surface-variant leading-relaxed ${
            isCompact ? "mt-1 max-w-xs text-label-sm" : "mt-2 max-w-xs text-body-sm"
          }`}
        >
          {description}
        </p>
      ) : null}

      {/* Action slot — ensure focusable element gets visible focus ring */}
      {action ? (
        <div className={isCompact ? "mt-3" : "mt-6"}>{action}</div>
      ) : null}
    </div>
  );
}
