type StatusBadgeProps = {
  children: string;
  tone?: "success" | "warning" | "danger" | "neutral" | "info";
  showDot?: boolean;
};

const styles: Record<NonNullable<StatusBadgeProps["tone"]>, string> = {
  success: "bg-secondary-fixed text-on-secondary-fixed border border-on-secondary-fixed/10",
  warning: "bg-tertiary-fixed text-on-tertiary-fixed border border-on-tertiary-fixed/10",
  danger: "bg-error-container text-on-error-container border border-error/20",
  neutral: "bg-surface-container-high text-on-surface-variant border border-outline/10",
  info: "bg-primary-fixed text-on-primary-fixed-variant border border-on-primary-fixed-variant/10",
};

const dotColors: Record<NonNullable<StatusBadgeProps["tone"]>, string> = {
  success: "bg-secondary",
  warning: "bg-tertiary",
  danger: "bg-error",
  neutral: "bg-outline",
  info: "bg-primary",
};

export function StatusBadge({ children, tone = "neutral", showDot = false }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-label-sm ${styles[tone]}`}
    >
      {showDot && (
        <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${dotColors[tone]}`} />
      )}
      {children}
    </span>
  );
}
