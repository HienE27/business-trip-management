import type { ScheduleTone } from "@/types/schedule";

export const toneStyles: Record<ScheduleTone, string> = {
  duty24: "border-blue-200 bg-blue-50 text-blue-800",
  allDay: "border-emerald-200 bg-emerald-50 text-emerald-800",
  serviceClinic: "border-amber-200 bg-amber-50 text-amber-800",
  expertClinic: "border-violet-200 bg-violet-50 text-violet-800",
  compLeave: "border-[#dfe4ea] bg-[#edf1f5] text-[#667085]",
  warning: "border-rose-200 bg-rose-50 text-rose-700",
  neutral: "border-[#dfe4ea] bg-white text-[#98a2b3]",
};
