export type ScheduleTone =
  | "duty24"
  | "allDay"
  | "serviceClinic"
  | "expertClinic"
  | "compLeave"
  | "warning"
  | "neutral";

export type Priority = "Cao" | "Trung bình" | "Thấp" | "Cốt lõi";

export type AllocationStat = {
  department: string;
  percentage: number;
  color: string;
};

export type SwapRequest = {
  id: string;
  requester: string;
  requesterAvatar: string;
  requesterInitials: string;
  target?: string;
  shiftType: string;
  date: string;
  reason?: string;
  type: "exchange" | "leave";
  status: "pending" | "approved" | "rejected";
};

export type Metric = {
  label: string;
  value: string;
  helper?: string;
  tone?: ScheduleTone;
  icon?: string;
  trend?: string;
};

export type NavigationItem = {
  label: string;
  code: string;
  href: string;
  active?: boolean;
  icon?: string;
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
  id: string;
  type: string;
  staffName: string;
  date: string;
  severity: "Chan luu" | "Canh bao";
  detail?: string;
};

export type WorkflowStep = {
  id: string;
  step: string;
  title: string;
  description?: string;
  status: "completed" | "active" | "pending";
};

export type StaffLoad = {
  name: string;
  duty24: number;
  allDay: number;
  clinics: number;
};
