import type { ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";

export type ScheduleRouteKey =
  | "duty-24"
  | "all-day"
  | "service-clinic"
  | "expert-clinic";

export const SCHEDULE_TYPE_CONFIG_MAP: Record<ScheduleRouteKey, ScheduleTypeConfig> = {
  "duty-24": {
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
    compDescription:
      "Ngày nghỉ bù sau ca trực 24/24 — không thể xếp lịch khác",
  },
  "all-day": {
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
  },
  "service-clinic": {
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
  },
  "expert-clinic": {
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
  },
};
