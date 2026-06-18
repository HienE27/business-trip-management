import {
  ScheduleByTypePage,
  type ScheduleTypeConfig,
} from "@/components/monthly-schedule/ScheduleByTypePage";

const config: ScheduleTypeConfig = {
  activeSection: "expert-clinic",
  shiftTypeId: "L04",
  title: "Phòng khám chuyên gia",
  description: "Xem và quản lý lịch phòng khám chuyên gia theo chuyên khoa.",
  emptyMessage: "Chọn một kỳ lịch để xem lịch phòng khám chuyên gia.",
  emptyIcon: "stethoscope",
  ctaIcon: "add",
  ctaLabel: "Thêm ca chuyên gia",
  totalShiftLabel: "Tổng ca PK Chuyên gia",
  totalShiftAccent: "bg-shift-expert/10 text-on-shift-expert",
  staffAccent: "bg-shift-all-day/10 text-on-shift-all-day",
  fetchErrorMessage: "Không thể tải lịch phòng khám chuyên gia.",
  compDescription: "",
  expertClinicMode: true,
};

export default function ExpertClinicPage() {
  return <ScheduleByTypePage config={config} />;
}
