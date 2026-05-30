type StatusBadgeProps = {
  children: string;
  tone?: "success" | "warning" | "danger" | "neutral" | "info";
};

const styles: Record<NonNullable<StatusBadgeProps["tone"]>, string> = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  warning: "border-amber-200 bg-amber-50 text-amber-700",
  danger: "border-rose-200 bg-rose-50 text-rose-700",
  neutral: "border-[#dfe4ea] bg-[#f8fafc] text-[#667085]",
  info: "border-blue-200 bg-blue-50 text-blue-700",
};

export function StatusBadge({ children, tone = "neutral" }: StatusBadgeProps) {
  return (
    <span className={`inline-flex h-7 items-center rounded-lg border px-2 text-xs font-medium ${styles[tone]}`}>
      {children}
    </span>
  );
}
