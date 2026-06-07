import { shiftLegends } from "@/data/schedule-summary";

const LEGEND_STYLES: Record<string, { bg: string; border: string }> = {
  duty24: { bg: "bg-blue-100", border: "border-blue-600" },
  allDay: { bg: "bg-green-100", border: "border-green-600" },
  serviceClinic: { bg: "bg-orange-100", border: "border-orange-600" },
  expertClinic: { bg: "bg-purple-100", border: "border-purple-600" },
};

export function ScheduleLegend({ className = "" }: { className?: string }) {
  return (
    <div
      className={`rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] p-4 ${className}`}
    >
      <h3 className="font-title-lg text-on-surface font-semibold mb-4 border-b border-outline-variant/30 pb-2">
        Shift Legends
      </h3>
      <ul className="flex flex-col gap-3">
        {shiftLegends.map((item) => {
          const style = LEGEND_STYLES[item.color] || LEGEND_STYLES.duty24;
          return (
            <li key={item.color} className="flex items-center gap-3">
              <div
                className={`w-4 h-4 rounded border-l-2 shrink-0 ${style.bg} ${style.border}`}
              />
              <span className="font-body-sm text-body-sm text-on-surface">{item.label}</span>
              <span className="ml-auto font-label-sm text-label-sm text-on-surface-variant bg-surface-container-high px-2 py-0.5 rounded">
                {item.color === "duty24" ? "Blue" : item.color === "allDay" ? "Green" : item.color === "serviceClinic" ? "Orange" : "Purple"}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
