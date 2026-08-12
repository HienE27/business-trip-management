export type StatCardAccent = "primary" | "secondary" | "tertiary" | "expert";

export type StatCardProps = {
  label: string;
  value: string | number;
  accent?: StatCardAccent;
  className?: string;
};

const accentConfig: Record<NonNullable<StatCardProps["accent"]>, {
  iconBg: string;
  iconText: string;
}> = {
  primary: { iconBg: "bg-blue-100", iconText: "text-blue-800" },
  secondary: { iconBg: "bg-emerald-100", iconText: "text-emerald-800" },
  tertiary: { iconBg: "bg-amber-100", iconText: "text-amber-800" },
  expert: { iconBg: "bg-expert-container", iconText: "text-on-expert-container" },
};

export function StatCard({
  label,
  value,
  accent = "primary",
  className = "",
}: StatCardProps) {
  const config = accentConfig[accent];

  return (
    <div
      className={`flex flex-col gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest p-3 shadow-sm hover:bg-surface-container-low transition-colors duration-200 ${className}`}
    >
      <span className="text-label-sm text-on-surface-variant">{label}</span>
      <span className="flex items-center justify-between">
        <span className="font-headline-lg text-headline-lg text-on-surface">{value}</span>
        <span
          className={`material-symbols-outlined rounded-md p-1.5 text-[20px] ${config.iconBg} ${config.iconText}`}
          style={{ fontVariationSettings: "'FILL' 0" }}
        >
          event_available
        </span>
      </span>
    </div>
  );
}
