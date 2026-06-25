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
  success: { iconBg: "bg-secondary-container", iconText: "text-on-secondary-container", valueColor: "text-on-surface" },
  warning: { iconBg: "bg-tertiary-fixed", iconText: "text-on-tertiary-fixed-variant", valueColor: "text-on-surface" },
  error: { iconBg: "bg-error-container", iconText: "text-on-error-container", valueColor: "text-error font-bold" },
  info: { iconBg: "bg-primary-fixed", iconText: "text-on-primary-fixed-variant", valueColor: "text-on-surface" },
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
      className={`flex flex-col justify-between rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm hover:bg-surface-container-low transition-colors duration-200 ${className}`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="text-label-sm text-on-surface-variant leading-tight">
          {label}
        </span>
        <span
          className={`material-symbols-outlined shrink-0 rounded-md p-1.5 text-[18px] ${config.iconBg} ${config.iconText}`}
          style={{ fontVariationSettings: "'FILL' 0" }}
        >
          {icon}
        </span>
      </div>
      <div className="mt-3 flex items-baseline gap-2">
        <span className={`font-bold text-[28px] leading-none tabular-nums ${config.valueColor}`}>
          {value}
        </span>
        {helper && (
          <span className="text-label-xs text-on-surface-variant">{helper}</span>
        )}
      </div>
    </div>
  );
}
