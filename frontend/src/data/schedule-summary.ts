export type ScheduleShift = {
  label: string;
  tone: string;
  staff: string;
};

export type WeekDay = {
  dayName: string;
  dayNumber: number;
  isToday?: boolean;
  isWeekend?: boolean;
  shifts: ScheduleShift[];
};

export type ScheduleAlert = {
  id: string;
  type: "conflict" | "restViolation";
  severity: "error" | "warning";
  title: string;
  detail: string;
};

export const weekDays: WeekDay[] = [
  {
    dayName: "T2",
    dayNumber: 12,
    isToday: false,
    isWeekend: false,
    shifts: [
      { label: "BS. Nguyễn Văn A (24h)", tone: "duty24", staff: "Nguyễn Văn A" },
      { label: "BS. Trần Thị B (TT)", tone: "allDay", staff: "Trần Thị B" },
      { label: "ĐD. Lê Văn C (Off)", tone: "off", staff: "Lê Văn C" },
    ],
  },
  {
    dayName: "T3",
    dayNumber: 13,
    isToday: false,
    isWeekend: false,
    shifts: [
      { label: "GS. Phạm D (KCG)", tone: "expertClinic", staff: "Phạm D" },
      { label: "BS. Hoàng E (KDV)", tone: "serviceClinic", staff: "Hoàng E" },
    ],
  },
  {
    dayName: "T4",
    dayNumber: 14,
    isToday: false,
    isWeekend: false,
    shifts: [
      { label: "BS. Đặng F (24h)", tone: "duty24", staff: "Đặng F" },
      { label: "ĐD. Ngô G (24h)", tone: "duty24", staff: "Ngô G" },
      { label: "BS. Vũ H (TT)", tone: "allDay", staff: "Vũ H" },
    ],
  },
  {
    dayName: "T5",
    dayNumber: 15,
    isToday: true,
    isWeekend: false,
    shifts: [
      { label: "BS. Hoàng E (KDV)", tone: "serviceClinic", staff: "Hoàng E" },
      { label: "GS. Phạm D (KCG)", tone: "expertClinic", staff: "Phạm D" },
      { label: "ĐD. Bùi K (TT)", tone: "allDay", staff: "Bùi K" },
      { label: "BS. Nguyễn Văn A (Off)", tone: "off", staff: "Nguyễn Văn A" },
    ],
  },
  {
    dayName: "T6",
    dayNumber: 16,
    isToday: false,
    isWeekend: false,
    shifts: [
      { label: "BS. Lê L (24h)", tone: "duty24", staff: "Lê L" },
      { label: "BS. Hoàng E (KDV)", tone: "serviceClinic", staff: "Hoàng E" },
    ],
  },
  {
    dayName: "T7",
    dayNumber: 17,
    isToday: false,
    isWeekend: true,
    shifts: [
      { label: "GS. Phạm D (KCG)", tone: "expertClinic", staff: "Phạm D" },
    ],
  },
  {
    dayName: "CN",
    dayNumber: 18,
    isToday: false,
    isWeekend: true,
    shifts: [
      { label: "BS. Nguyễn N (24h)", tone: "duty24", staff: "Nguyễn N" },
    ],
  },
];

// Second row of shifts (simulating multiple staff rows)
export const weekDaysRow2: WeekDay[] = [
  {
    dayName: "T2",
    dayNumber: 12,
    isWeekend: false,
    shifts: [
      { label: "GS. Phạm D (KCG)", tone: "expertClinic", staff: "Phạm D" },
    ],
  },
  {
    dayName: "T3",
    dayNumber: 13,
    isWeekend: false,
    shifts: [
      { label: "BS. Đặng Q (24h)", tone: "duty24", staff: "Đặng Q" },
      { label: "ĐD. Ngô R (TT)", tone: "allDay", staff: "Ngô R" },
    ],
  },
  {
    dayName: "T4",
    dayNumber: 14,
    isWeekend: false,
    shifts: [],
  },
  {
    dayName: "T5",
    dayNumber: 15,
    isWeekend: false,
    shifts: [
      { label: "BS. Hoàng E (KDV)", tone: "serviceClinic", staff: "Hoàng E" },
    ],
  },
  {
    dayName: "T6",
    dayNumber: 16,
    isWeekend: false,
    shifts: [
      { label: "BS. Trần T (TT)", tone: "allDay", staff: "Trần T" },
    ],
  },
  {
    dayName: "T7",
    dayNumber: 17,
    isWeekend: true,
    shifts: [],
  },
  {
    dayName: "CN",
    dayNumber: 18,
    isWeekend: true,
    shifts: [],
  },
];

export const scheduleAlerts: ScheduleAlert[] = [
  {
    id: "AL-001",
    type: "conflict",
    severity: "error",
    title: "Trùng ca trực",
    detail: "BS. Châu I được xếp Khám Dịch Vụ và Trực 24/24 cùng lúc.",
  },
  {
    id: "AL-002",
    type: "restViolation",
    severity: "warning",
    title: "Vi phạm thời gian nghỉ",
    detail: "ĐD. Ngô G không đủ 12h nghỉ giữa 2 ca trực liên tiếp.",
  },
];

export const shiftLegends = [
  { label: "24/24 Shift", color: "duty24", cssBg: "bg-blue-100", cssBorder: "border-blue-600" },
  { label: "Full-time", color: "allDay", cssBg: "bg-green-100", cssBorder: "border-green-600" },
  { label: "Service Care", color: "serviceClinic", cssBg: "bg-orange-100", cssBorder: "border-orange-600" },
  { label: "Specialist", color: "expertClinic", cssBg: "bg-purple-100", cssBorder: "border-purple-600" },
];
