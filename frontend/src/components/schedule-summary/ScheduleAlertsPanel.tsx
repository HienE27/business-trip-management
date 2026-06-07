import type { ScheduleAlert } from "@/data/schedule-summary";

type ScheduleAlertsPanelProps = {
  alerts: ScheduleAlert[];
  className?: string;
};

function getAlertStyle(severity: ScheduleAlert["severity"]) {
  if (severity === "error") {
    return {
      container: "border-error/20 bg-error-container/30 hover:bg-error-container/50",
      icon: "text-error",
      bg: "bg-error-container",
    };
  }
  return {
    container: "border-tertiary/20 bg-tertiary-container/20 hover:bg-tertiary-container/40",
    icon: "text-tertiary",
    bg: "bg-tertiary-container",
  };
}

const ALERT_ICONS: Record<string, string> = {
  conflict: "sync_problem",
  restViolation: "timer_off",
};

export function ScheduleAlertsPanel({ alerts, className = "" }: ScheduleAlertsPanelProps) {
  return (
    <div
      className={`rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] flex flex-col overflow-hidden ${className}`}
    >
      {/* Header */}
      <div className="p-4 border-b border-outline-variant/30 flex items-center justify-between bg-error/5">
        <h3 className="font-title-lg text-on-surface font-semibold flex items-center gap-2">
          <span className="material-symbols-outlined text-error">warning</span>
          Cảnh báo trực tiếp
        </h3>
        {/* Pulsing dot */}
        <span className="relative flex h-3 w-3">
          <span className="animate-ping absolute inline-flex h-full w-full bg-error opacity-75 rounded-lg" />
          <span className="relative inline-flex h-3 w-3 bg-error rounded-lg" />
        </span>
      </div>

      {/* Alerts List */}
      <div className="p-4 flex flex-col gap-3">
        {alerts.map((alert) => {
          const style = getAlertStyle(alert.severity);
          return (
            <article
              key={alert.id}
              className={`p-3 rounded-lg border ${style.container} hover:shadow-sm transition-colors cursor-pointer group`}
            >
              <div className="flex flex-col">
                <span className={`font-label-md font-bold flex items-center gap-1 ${style.icon}`}>
                  <span className="material-symbols-outlined text-[16px]">
                    {ALERT_ICONS[alert.type]}
                  </span>
                  {alert.title}
                </span>
                <span className="font-body-sm text-on-surface mt-1">{alert.detail}</span>
              </div>
              <div className="mt-2 text-xs text-on-surface-variant flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <span className="material-symbols-outlined text-[14px]">edit</span>
                Nhấn để giải quyết
              </div>
            </article>
          );
        })}

        <button className="w-full py-2 mt-2 font-label-md text-primary hover:bg-primary/5 transition-colors border border-transparent hover:border-primary/20 rounded-lg">
          Xem tất cả cảnh báo (4)
        </button>
      </div>
    </div>
  );
}
