export type ShiftDetailStatus = "approved" | "pending" | "draft";

export type ShiftDetailStaff = {
  id: string;
  name: string;
  initials: string;
  role: string;
  roleBadge: string;
  department: string;
  departmentFull: string;
  position: string;
  note?: string;
  avatarColor: string;
};

export type ShiftDetailViewModel = {
  id: string;
  code: string;
  department: string;
  departmentFull: string;
  date: string;
  weekday: string;
  shiftType: string;
  shiftTime: string;
  status: ShiftDetailStatus;
  compensationDate?: string | null;
  periodName?: string;
  periodRange?: string;
  specialtyName?: string | null;
  roles: string[];
  notes?: string | null;
  conflictReasons: string[];
  staff: ShiftDetailStaff[];
};
