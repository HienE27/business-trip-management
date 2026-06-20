import { GuardedScheduleByTypePage } from "@/components/monthly-schedule/GuardedScheduleByTypePage";
import type { ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";

const config: ScheduleTypeConfig = {
  activeSection: "duty-24",
  shiftTypeId: "L01",
  title: "Lịch trực 24/24",
  description:
    "Xếp lịch trực 24/24 theo tháng. Hệ thống tự động tính ngày nghỉ bù theo quy định (T2-T5 nghỉ bù hôm sau, T6/T7 nghỉ bù T3 tuần sau, CN nghỉ bù T2).",
  emptyMessage: "Chọn một kỳ lịch để xem lịch trực 24/24.",
  emptyIcon: "emergency",
  ctaIcon: "add",
  ctaLabel: "Thêm ca trực",
  totalShiftLabel: "Tổng ca trực 24/24",
  totalShiftAccent: "bg-shift-24/30 text-on-shift-24",
  staffAccent: "bg-shift-all-day/20 text-on-shift-all-day",
  fetchErrorMessage: "Không thể tải lịch trực 24/24.",
  compDescription: "Ngày nghỉ bù sau ca trực 24/24 — không thể xếp lịch khác",
};

export default function Duty24Page() {
  return <GuardedScheduleByTypePage config={config} />;
}