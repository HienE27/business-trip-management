import type {
  AllocationStat,
  ConflictItem,
  Metric,
  NavigationItem,
  ScheduleModule,
  StaffLoad,
  StaffScheduleRow,
  SwapRequest,
  WorkflowStep,
} from "@/types/schedule";

const baseNavigationItems: NavigationItem[] = [
  { label: "Tổng quan", code: "DASHBOARD", href: "/dashboard", icon: "dashboard" },
  { label: "Nhân sự", code: "M01", href: "/staff", icon: "groups" },
  { label: "Trực 24/24", code: "M02", href: "/duty-24", icon: "emergency" },
  { label: "Thông tầm", code: "M03", href: "/all-day", icon: "schedule" },
  { label: "PK dịch vụ", code: "M04", href: "/service-clinic", icon: "medical_services" },
  { label: "PK chuyên gia", code: "M05", href: "/expert-clinic", icon: "stethoscope" },
  { label: "Tổng hợp lịch", code: "M03-SUMMARY", href: "/schedule-summary", icon: "calendar_view_month" },
  { label: "Đổi trực", code: "M02-SWAP", href: "/swap-requests", icon: "swap_horiz" },
  { label: "Kiểm tra lỗi", code: "M06-CONFLICT", href: "/conflict-check", icon: "warning" },
  { label: "Báo cáo", code: "M06-REPORTS", href: "/reports", icon: "query_stats" },
  { label: "Thông báo", code: "M06-NOTIFICATIONS", href: "/notifications", icon: "notifications" },
  { label: "Nhật ký", code: "M06-AUDIT", href: "/audit-history", icon: "history" },
  { label: "Tự động xếp", code: "M07", href: "/auto-scheduling", icon: "auto_mode" },
];

export function getNavigationItems(activeCode: string): NavigationItem[] {
  return baseNavigationItems.map((item) => ({
    ...item,
    active: item.code === activeCode,
  }));
}

export const metrics: Metric[] = [
  {
    label: "Tổng nhân sự",
    value: "20",
    tone: "neutral",
    icon: "group",
  },
  {
    label: "Trực 24/24",
    value: "45",
    tone: "duty24",
    icon: "emergency",
  },
  {
    label: "Thông tầm",
    value: "30",
    tone: "allDay",
    icon: "schedule",
  },
  {
    label: "Lịch dịch vụ",
    value: "15",
    tone: "serviceClinic",
    icon: "medical_services",
  },
  {
    label: "Lịch chuyên gia",
    value: "12",
    tone: "expertClinic",
    icon: "vaccines",
  },
  {
    label: "Xung đột",
    value: "3",
    helper: "warning",
    tone: "warning",
    icon: "warning",
  },
];

export const dashboardCalendar = {
  month: "Tháng 10, 2023",
  prevDays: [25, 26, 27, 28, 29, 30],
  cells: [
    {
      day: 1,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: false,
      items: [
        { label: "BS. An (24/24)", tone: "duty24" as const },
        { label: "BS. Bình (TT)", tone: "allDay" as const },
      ],
    },
    {
      day: 2,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: false,
      items: [{ label: "BS. Cường (DV)", tone: "serviceClinic" as const }],
    },
    {
      day: 3,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: false,
      items: [{ label: "GS. Dũng (CG)", tone: "expertClinic" as const }],
    },
    {
      day: 4,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: false,
      hasConflict: true,
      items: [
        { label: "BS. An (24/24)", tone: "duty24" as const },
        { label: "BS. An (TT)", tone: "allDay" as const },
      ],
    },
    {
      day: 5,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: false,
      items: [],
    },
    {
      day: 6,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: true,
      items: [{ label: "BS. Hoa (DV)", tone: "serviceClinic" as const }],
    },
    {
      day: 7,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: true,
      isLocked: true,
      lockedLabel: "Khóa / Nghỉ",
      items: [],
    },
    {
      day: 8,
      isCurrentMonth: true,
      isToday: false,
      isWeekend: false,
      items: [],
    },
  ],
} as const;

export const swapRequests: SwapRequest[] = [
  {
    id: "SR-001",
    requester: "BS. An",
    requesterInitials: "A",
    requesterAvatar: "",
    target: "BS. Cường",
    shiftType: "Ca 24/24 ngày 10/10",
    date: "10/10/2023",
    type: "exchange",
    status: "pending",
  },
  {
    id: "SR-002",
    requester: "BS. Hoa",
    requesterInitials: "H",
    requesterAvatar: "",
    shiftType: "Lý do sức khỏe - Ca DV 12/10",
    date: "12/10/2023",
    type: "leave",
    status: "pending",
  },
];

export const allocationStats: AllocationStat[] = [
  { department: "Nội khoa", percentage: 85, color: "primary" },
  { department: "Ngoại khoa", percentage: 60, color: "secondary" },
  { department: "Cấp cứu", percentage: 95, color: "error" },
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
    id: "CF-001",
    type: "Truc 24/24 trung thong tam",
    staffName: "Nguyen Minh Anh",
    date: "31/05/2026",
    severity: "Chặn lưu",
    detail: "Nhan su co lich thong tam cung ngay voi ca truc 24/24 duoc de xuat.",
  },
  {
    id: "CF-002",
    type: "Xep lich vao ngay nghi bu",
    staffName: "Tran Duc Huy",
    date: "28/05/2026",
    severity: "Chặn lưu",
    detail: "Ngay nghi bu sau truc dem dang bi su dung lai cho phong kham dich vu.",
  },
  {
    id: "CF-003",
    type: "Dich vu trung chuyen gia",
    staffName: "Le Bao Chau",
    date: "29/05/2026",
    severity: "Cảnh báo",
    detail: "Nhan su dang duoc de xuat cho ca lich kham dich vu va kham chuyen gia cung ngay.",
  },
];

export const workflowSteps: WorkflowStep[] = [
  { id: "WS-001", step: "B1", title: "Chon thang va ngoai le", status: "completed" },
  { id: "WS-002", step: "B2", title: "Chay Round Robin", status: "active", description: "Phan bo deu ca truc theo thuat toan vong tron." },
  { id: "WS-003", step: "B3", title: "Quet rang buoc", status: "pending", description: "Kiem tra xung dot, nghi bu va ngoai le." },
  { id: "WS-004", step: "B4", title: "Xem truoc va ap dung", status: "pending", description: "Xem truoc ket qua va xac nhan luu." },
];

export const staffLoads: StaffLoad[] = [
  { name: "Minh Anh", duty24: 4, allDay: 3, clinics: 5 },
  { name: "Duc Huy", duty24: 4, allDay: 4, clinics: 4 },
  { name: "Bao Chau", duty24: 3, allDay: 3, clinics: 6 },
  { name: "Quoc Viet", duty24: 4, allDay: 2, clinics: 5 },
  { name: "Lan Phuong", duty24: 3, allDay: 4, clinics: 5 },
];
