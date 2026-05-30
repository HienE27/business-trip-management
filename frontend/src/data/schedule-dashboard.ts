import type {
  ConflictItem,
  Metric,
  NavigationItem,
  ScheduleModule,
  StaffLoad,
  StaffScheduleRow,
  WorkflowStep,
} from "@/types/schedule";

const baseNavigationItems: NavigationItem[] = [
  { label: "Tổng quan", code: "M06", href: "/" },
  { label: "Nhân sự", code: "M01", href: "/staff" },
  { label: "Trực 24/24", code: "M02", href: "/duty-24" },
  { label: "Đổi trực", code: "M02-F04", href: "/swap-requests" },
  { label: "Thông tầm", code: "M03", href: "/all-day" },
  { label: "PK dịch vụ", code: "M04", href: "/service-clinic" },
  { label: "PK chuyên gia", code: "M05", href: "/expert-clinic" },
  { label: "Xung đột", code: "M06-F03", href: "/conflict-check" },
  { label: "Báo cáo", code: "M06-F04", href: "/reports" },
  { label: "Nhật ký", code: "M06-F05", href: "/audit-log" },
  { label: "Phân quyền", code: "M01-F05", href: "/roles" },
  { label: "Tự động xếp", code: "M07", href: "/auto-scheduling" },
];

export function getNavigationItems(activeCode: string): NavigationItem[] {
  return baseNavigationItems.map((item) => ({
    ...item,
    active: item.code === activeCode,
  }));
}

export const metrics: Metric[] = [
  { label: "Nhân sự hoạt động", value: "20", helper: "3 vai trò hệ thống" },
  { label: "Ngày đã phân công", value: "86%", helper: "Tháng 05/2026" },
  { label: "Xung đột cần xử lý", value: "04", helper: "Chặn lưu lịch tháng", tone: "warning" },
  { label: "Ngày nghỉ bù", value: "18", helper: "Tự tính sau trực 24/24", tone: "compLeave" },
];

export const scheduleModules: ScheduleModule[] = [
  {
    code: "M02",
    title: "Lịch trực 24/24",
    description: "Ca 7h30 ngày N đến 7h30 ngày N+1, tự tính nghỉ bù.",
    priority: "Cao",
    progress: 92,
  },
  {
    code: "M03",
    title: "Lịch thông tầm",
    description: "Chọn ngày làm liên tục, kiểm tra trùng trực 24/24.",
    priority: "Cao",
    progress: 78,
  },
  {
    code: "M04",
    title: "Phòng khám dịch vụ",
    description: "Gán nhân sự phụ trách ca khám dịch vụ theo ngày.",
    priority: "Cao",
    progress: 64,
  },
  {
    code: "M05",
    title: "Phòng khám chuyên gia",
    description: "Lọc chuyên khoa, tránh trùng lịch phòng khám dịch vụ.",
    priority: "Cao",
    progress: 58,
  },
];

export const staffColumns = ["Minh Anh", "Duc Huy", "Bao Chau", "Quoc Viet", "Lan Phuong"];

export const scheduleRows: StaffScheduleRow[] = [
  {
    day: "27",
    weekday: "Thứ 2",
    assignments: {
      "Minh Anh": { label: "24/24", tone: "duty24" },
      "Duc Huy": { label: "Thông tầm", tone: "allDay" },
      "Bao Chau": { label: "PK dịch vụ", tone: "serviceClinic" },
      "Quoc Viet": { label: "Trống", tone: "neutral" },
      "Lan Phuong": { label: "PK chuyên gia", tone: "expertClinic" },
    },
  },
  {
    day: "28",
    weekday: "Thứ 3",
    assignments: {
      "Minh Anh": { label: "Nghỉ bù", tone: "compLeave", locked: true },
      "Duc Huy": { label: "24/24", tone: "duty24" },
      "Bao Chau": { label: "Trống", tone: "neutral" },
      "Quoc Viet": { label: "PK dịch vụ", tone: "serviceClinic" },
      "Lan Phuong": { label: "Thông tầm", tone: "allDay" },
    },
  },
  {
    day: "29",
    weekday: "Thứ 4",
    assignments: {
      "Minh Anh": { label: "PK dịch vụ", tone: "serviceClinic" },
      "Duc Huy": { label: "Nghỉ bù", tone: "compLeave", locked: true },
      "Bao Chau": { label: "24/24", tone: "duty24" },
      "Quoc Viet": { label: "Thông tầm", tone: "allDay" },
      "Lan Phuong": { label: "Trống", tone: "neutral" },
    },
  },
  {
    day: "30",
    weekday: "Thứ 5",
    assignments: {
      "Minh Anh": { label: "Thông tầm", tone: "allDay" },
      "Duc Huy": { label: "PK chuyên gia", tone: "expertClinic" },
      "Bao Chau": { label: "Nghỉ bù", tone: "compLeave", locked: true },
      "Quoc Viet": { label: "24/24", tone: "duty24" },
      "Lan Phuong": { label: "PK dịch vụ", tone: "serviceClinic" },
    },
  },
  {
    day: "31",
    weekday: "Thứ 6",
    assignments: {
      "Minh Anh": { label: "Cảnh báo", tone: "warning" },
      "Duc Huy": { label: "Trống", tone: "neutral" },
      "Bao Chau": { label: "PK chuyên gia", tone: "expertClinic" },
      "Quoc Viet": { label: "Nghỉ bù", tone: "compLeave", locked: true },
      "Lan Phuong": { label: "24/24", tone: "duty24" },
    },
  },
];

export const conflicts: ConflictItem[] = [
  {
    type: "Trực 24/24 trùng thông tầm",
    staff: "Nguyen Minh Anh",
    date: "31/05/2026",
    severity: "Chặn lưu",
  },
  {
    type: "Lịch xếp vào ngày nghỉ bù",
    staff: "Tran Duc Huy",
    date: "28/05/2026",
    severity: "Chặn lưu",
  },
  {
    type: "Dịch vụ trùng chuyên gia",
    staff: "Le Bao Chau",
    date: "29/05/2026",
    severity: "Cảnh báo",
  },
];

export const workflowSteps: WorkflowStep[] = [
  { step: "B1", title: "Chọn tháng và ngoại lệ", status: "Done" },
  { step: "B2", title: "Chạy Round Robin", status: "Active" },
  { step: "B3", title: "Quét ràng buộc", status: "Pending" },
  { step: "B4", title: "Xem trước và áp dụng", status: "Pending" },
];

export const staffLoads: StaffLoad[] = [
  { name: "Minh Anh", duty24: 4, allDay: 3, clinics: 5 },
  { name: "Duc Huy", duty24: 4, allDay: 4, clinics: 4 },
  { name: "Bao Chau", duty24: 3, allDay: 3, clinics: 6 },
  { name: "Quoc Viet", duty24: 4, allDay: 2, clinics: 5 },
  { name: "Lan Phuong", duty24: 3, allDay: 4, clinics: 5 },
];
