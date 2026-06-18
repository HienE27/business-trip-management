import { ScheduleByTypePage, type ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";

const config: ScheduleTypeConfig = {
  activeSection: "all-day",
  shiftTypeId: "L02",
  title: "Lịch thông tầm",
  description:
    "Xếp lịch thông tầm theo tháng. Ràng buộc: không trùng lịch trực 24/24 cùng ngày và không xếp vào ngày nghỉ bù.",
  emptyMessage: "Chọn một kỳ lịch để xem lịch thông tầm.",
  emptyIcon: "schedule",
  ctaIcon: "add",
  ctaLabel: "Thêm ca thông tầm",
  totalShiftLabel: "Tổng ca thông tầm",
  totalShiftAccent: "bg-shift-all-day/30 text-on-shift-all-day",
  staffAccent: "bg-shift-24/20 text-on-shift-24",
  fetchErrorMessage: "Không thể tải lịch thông tầm.",
  compDescription: "Ngày nghỉ bù — không thể xếp lịch thông tầm cho nhân sự này",
};

export default function AllDayPage() {
  return <ScheduleByTypePage config={config} />;
}
