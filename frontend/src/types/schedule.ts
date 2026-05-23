export type ScheduleTone =
  | "duty24"
  | "allDay"
  | "serviceClinic"
  | "expertClinic"
  | "compLeave"
  | "warning"
  | "neutral";

export type Priority = "Cao" | "Trung bình" | "Thấp" | "Cốt lõi";

export type Metric = {
  label: string;
  value: string;
  helper: string;
  tone?: ScheduleTone;
};

export type NavigationItem = {
  label: string;
  code: string;
  href: string;
  active?: boolean;
};

export type ScheduleModule = {
  code: string;
  title: string;
  description: string;
  priority: Priority;
  progress: number;
};

export type StaffScheduleRow = {
  day: string;
  weekday: string;
  assignments: Record<string, CalendarAssignment>;
};

export type CalendarAssignment = {
  label: string;
  tone: ScheduleTone;
  locked?: boolean;
};

export type ConflictItem = {
  type: string;
  staff: string;
  date: string;
  severity: "Chặn lưu" | "Cảnh báo";
};

export type WorkflowStep = {
  step: string;
  title: string;
  status: "Done" | "Active" | "Pending";
};

export type StaffLoad = {
  name: string;
  duty24: number;
  allDay: number;
  clinics: number;
};
