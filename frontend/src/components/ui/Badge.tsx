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
  success: "bg-emerald-100 text-emerald-800 border border-emerald-300",
  warning: "bg-amber-100 text-amber-800 border border-amber-300",
  error: "bg-red-100 text-red-800 border border-red-300",
  danger: "bg-red-100 text-red-800 border border-red-300",
  info: "bg-blue-100 text-blue-800 border border-blue-300",
  neutral: "bg-surface-container-high text-on-surface-variant border border-outline/10",
};

const dotColors: Record<NonNullable<BadgeProps["tone"]>, string> = {
  success: "bg-secondary",
  warning: "bg-tertiary",
  error: "bg-error animate-pulse",
  danger: "bg-error animate-pulse",
  info: "bg-blue-100",
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
