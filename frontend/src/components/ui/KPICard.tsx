export type KPITone = "success" | "warning" | "error" | "info" | "neutral";

export type KPICardProps = {
  label: string;
  value: string | number;
  icon: string;
  tone?: KPITone;
  helper?: string;
  className?: string;
};

const toneConfig: Record<NonNullable<KPICardProps["tone"]>, {
  iconBg: string;
  iconText: string;
  valueColor: string;
}> = {
  success: { iconBg: "bg-emerald-100", iconText: "text-emerald-800", valueColor: "text-on-surface" },
  warning: { iconBg: "bg-amber-100", iconText: "text-amber-800", valueColor: "text-on-surface" },
  error: { iconBg: "bg-red-100", iconText: "text-red-800", valueColor: "text-red-800 font-bold" },
  info: { iconBg: "bg-blue-100", iconText: "text-blue-800", valueColor: "text-on-surface" },
  neutral: { iconBg: "bg-surface-container-high", iconText: "text-on-surface-variant", valueColor: "text-on-surface" },
};

export function KPICard({
  label,
  value,
  icon,
  tone = "neutral",
  helper,
  className = "",
}: KPICardProps) {
  const config = toneConfig[tone];

  return (
    <div
      className={`flex flex-col justify-between rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm hover:bg-surface-container-low transition-colors duration-200 min-w-0 overflow-hidden ${className}`}
    >
      <div className="flex items-start justify-between gap-2 min-w-0">
        <span className="text-label-sm text-on-surface-variant leading-tight break-words min-w-0 flex-1">
          {label}
        </span>
        <span
          className={`material-symbols-outlined shrink-0 rounded-md p-1.5 text-[18px] ${config.iconBg} ${config.iconText}`}
          style={{ fontVariationSettings: "'FILL' 0" }}
        >
          {icon}
        </span>
      </div>
      <div className="mt-3 min-w-0">
        <div className="flex items-baseline gap-2 min-w-0">
          <span className={`font-bold text-[28px] leading-none tabular-nums truncate ${config.valueColor}`}>
            {value}
          </span>
        </div>
        {helper && (
          <span className="mt-1 block text-label-xs text-on-surface-variant leading-snug break-words">
            {helper}
          </span>
        )}
      </div>
    </div>
  );
}
