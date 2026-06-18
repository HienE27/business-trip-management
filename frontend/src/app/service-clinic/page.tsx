import { ScheduleByTypePage, type ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";

const config: ScheduleTypeConfig = {
  activeSection: "service-clinic",
  shiftTypeId: "L03",
  title: "Lịch PK dịch vụ",
  description:
    "Xếp lịch phòng khám dịch vụ theo tháng. Ràng buộc: không trùng lịch phòng khám chuyên gia cùng ngày.",
  emptyMessage: "Chọn một kỳ lịch để xem lịch PK dịch vụ.",
  emptyIcon: "medical_services",
  ctaIcon: "add",
  ctaLabel: "Thêm ca PK dịch vụ",
  totalShiftLabel: "Tổng ca PK dịch vụ",
  totalShiftAccent: "bg-shift-clinic-service/30 text-on-shift-clinic-service",
  staffAccent: "bg-shift-24/20 text-on-shift-24",
  fetchErrorMessage: "Không thể tải lịch PK dịch vụ.",
  compDescription: "Ngày nghỉ bù — không thể xếp lịch phòng khám dịch vụ cho nhân sự này",
};

export default function ServiceClinicPage() {
  return <ScheduleByTypePage config={config} />;
}
