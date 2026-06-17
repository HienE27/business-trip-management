export type BadgeTone = "success" | "warning" | "error" | "info" | "neutral" | "danger";

export type BadgeProps = {
  children: React.ReactNode;
  tone?: BadgeTone;
  dot?: boolean;
  /** Alias for dot — matches StatusBadge API */
  showDot?: boolean;
  size?: "sm" | "md";
  className?: string;
};

const toneClasses: Record<NonNullable<BadgeProps["tone"]>, string> = {
  success: "bg-secondary-container text-on-secondary-container border border-secondary/20",
  warning: "bg-tertiary-fixed text-on-tertiary-fixed-variant border border-tertiary/20",
  error: "bg-error-container text-on-error-container border border-error/20",
  danger: "bg-error-container text-on-error-container border border-error/20",
  info: "bg-primary-fixed text-on-primary-fixed-variant border border-primary/20",
  neutral: "bg-surface-container-high text-on-surface-variant border border-outline/10",
};

const dotColors: Record<NonNullable<BadgeProps["tone"]>, string> = {
  success: "bg-secondary",
  warning: "bg-tertiary",
  error: "bg-error",
  danger: "bg-error",
  info: "bg-primary",
  neutral: "bg-outline",
};

export function Badge({
  children,
  tone = "neutral",
  dot = false,
  showDot,
  size = "md",
  className = "",
}: BadgeProps) {
  const isDotted = dot || showDot;
  const role = (tone === "success" || tone === "warning" || tone === "error" || tone === "danger") ? "status" : undefined;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full font-semibold ${toneClasses[tone]} ${size === "sm" ? "px-2" : "px-2.5"} py-0.5 text-label-sm ${className}`}
      role={role}
    >
      {isDotted && (
        <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${dotColors[tone]}`} />
      )}
      {children}
    </span>
  );
}
