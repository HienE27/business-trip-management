import type { ConflictCheckResponse, ConflictDetail, CompensationDay, Schedule, SchedulePeriod, ShiftRequirement, Staff } from "@/types/api";
import { getRoleBadge, getRoleLabel } from "@/lib/roleLabels";
import { formatDate } from "@/lib/date";
import { SHIFT_COLORS, SHIFT_TYPE_LABELS, WEEKDAYS } from "./constants";
import type { CalendarAnnotation, OperationalKpi, ScheduleTab, WorkloadRow } from "./types";

const AVATAR_COLORS = [
  "bg-primary-fixed text-on-primary-fixed-variant",
  "bg-secondary-container text-on-secondary-container",
  "bg-tertiary-fixed text-on-tertiary-fixed-variant",
  "bg-surface-container-high text-on-surface",
  "bg-primary/10 text-primary",
  "bg-secondary/10 text-secondary",
];

export { formatDate };

export function formatDateRange(period: SchedulePeriod | null) {
  if (!period) return "Chưa chọn kỳ lịch";
  return `${formatDate(period.startDate)} – ${formatDate(period.endDate)}`;
}

export function getShiftTypeLabel(id: string) {
  return SHIFT_TYPE_LABELS[id as ScheduleTab] ?? id;
}

export function getStatusBadgeClass(status: SchedulePeriod["status"] | undefined) {
  if (status === "PUBLISHED") return "bg-secondary-container text-on-secondary-container border border-secondary/20";
  if (status === "ARCHIVED") return "bg-surface-container-highest text-outline border border-outline-variant";
  return "bg-primary-fixed text-primary border border-primary/20";
}

export function getInitialCalendar(period: SchedulePeriod | null) {
  if (!period?.startDate) {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() };
  }
  const start = new Date(period.startDate);
  return { year: start.getFullYear(), month: start.getMonth() };
}

function getWeekday(dateStr: string): string {
  const d = new Date(`${dateStr}T00:00:00`);
  return WEEKDAYS[d.getDay()] ?? "";
}

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

function getAvatarColor(id: number): string {
  return AVATAR_COLORS[id % AVATAR_COLORS.length];
}

export function buildShiftDetailViewModel(schedule: Schedule) {
  const shiftTypeId = schedule.shiftType.id as ScheduleTab;
  const shiftTypeName = schedule.shiftType.name ?? getShiftTypeLabel(shiftTypeId);
  const shiftColor = SHIFT_COLORS[shiftTypeId]?.bg ?? "bg-surface-container-low";
  const weekday = getWeekday(schedule.workDate);
  const dateFormatted = schedule.workDate ? schedule.workDate.split("-").reverse().join("/") : "";
  const shiftTime = schedule.shiftType.startTime && schedule.shiftType.endTime
    ? `${schedule.shiftType.startTime} - ${schedule.shiftType.endTime}`
    : "";
  const statusMap: Record<string, "approved" | "pending" | "draft"> = {
    PUBLISHED: "approved",
    DRAFT: "draft",
    ARCHIVED: "draft",
  };
  const status = schedule.period?.status ? (statusMap[schedule.period.status] ?? "pending") : "pending";
  const compDateFormatted = schedule.compensationDate ? schedule.compensationDate.split("-").reverse().join("/") : null;
  const staffInitials = getInitials(schedule.staff.fullName);
  const avatarColor = getAvatarColor(schedule.staff.id);

  return {
    id: String(schedule.id),
    code: `${shiftTypeId}-${schedule.id}`,
    department: schedule.staff.specialtyName ?? "Chưa phân khoa",
    departmentFull: schedule.staff.specialtyName ?? "Chưa xác định",
    date: dateFormatted,
    weekday,
    shiftType: shiftTypeName,
    shiftTime,
    status,
    compensationDate: compDateFormatted,
    periodName: schedule.period?.periodName,
    periodRange: schedule.period
      ? `${schedule.period.startDate.split("-").reverse().join("/")} - ${schedule.period.endDate.split("-").reverse().join("/")}`
      : undefined,
    specialtyName: schedule.staff.specialtyName ?? null,
    roles: schedule.staff.roles ?? [],
    notes: schedule.notes ?? null,
    conflictReasons: schedule.conflictReasons ?? [],
    staff: [
      {
        id: String(schedule.staff.id),
        name: schedule.staff.fullName,
        initials: staffInitials,
        role: getRoleLabel(schedule.staff.roles ?? []),
        roleBadge: getRoleBadge(schedule.staff.roles ?? []),
        department: schedule.staff.specialtyName ?? "Chưa phân khoa",
        departmentFull: schedule.staff.specialtyName ?? "Chưa xác định",
        position: shiftTypeName,
        note: schedule.notes ?? undefined,
        avatarColor,
      },
    ],
    shiftColor,
    shiftTypeId,
  };
}

export function buildCalendarAnnotations(compensationDays: CompensationDay[], conflicts: ConflictDetail[]): CalendarAnnotation[] {
  // Group compensation days by date, list all staff on that date
  const compByDate = new Map<string, CompensationDay[]>();
  for (const cd of compensationDays) {
    // Use substring instead of split for better performance
    const key = cd.compensationDate.substring(0, 10);
    if (!compByDate.has(key)) compByDate.set(key, []);
    compByDate.get(key)!.push(cd);
  }
  const compAnnotations = Array.from(compByDate.entries()).map(([date, days]) => {
    const staffNames = days.map((d) => d.staffName);
    const label = staffNames.length === 1
      ? `Nghỉ bù · ${staffNames[0]}`
      : `Nghỉ bù · ${staffNames[0]}${staffNames.length > 1 ? ` (+${staffNames.length - 1})` : ""}`;
    const description = staffNames.length === 1
      ? `Ngày nghỉ bù của ${staffNames[0]} — không thể xếp lịch`
      : `Ngày nghỉ bù của ${staffNames.join(", ")} — không thể xếp lịch`;
    return { date, label, tone: "compLeave" as const, description, isCompensation: true, locked: true };
  });

  // Group conflicts by date
  const conflictByDate = new Map<string, ConflictDetail[]>();
  for (const conflict of conflicts) {
    // Use substring instead of split for better performance
    const key = conflict.workDate.substring(0, 10);
    if (!conflictByDate.has(key)) conflictByDate.set(key, []);
    conflictByDate.get(key)!.push(conflict);
  }
  const conflictAnnotations = Array.from(conflictByDate.entries()).map(([date, items]) => {
    const staffNames = items.map((c) => c.staffName);
    const label = staffNames.length === 1
      ? `Xung đột · ${staffNames[0]}`
      : `Xung đột · ${staffNames[0]}${staffNames.length > 1 ? ` (+${staffNames.length - 1})` : ""}`;
    return { date, label, tone: "warning" as const, description: items.flatMap((c) => c.conflictReasons).join(" • ") };
  });

  return [...compAnnotations, ...conflictAnnotations];
}

export function buildCoverageMap(
  requirements: ShiftRequirement[],
  filter?: { shiftTypeId?: ScheduleTab }
) {
  const map: Record<string, { required: number; assigned: number }> = {};
  const includeAll = !filter?.shiftTypeId || filter.shiftTypeId === "ALL";
  for (const req of requirements) {
    if (!includeAll && req.shiftType.id !== filter?.shiftTypeId) continue;
    // Use substring instead of split for better performance
    const key = req.workDate.substring(0, 10);
    const prev = map[key] ?? { required: 0, assigned: 0 };
    map[key] = {
      required: prev.required + req.requiredStaffCount,
      assigned: prev.assigned + req.assignedStaffCount,
    };
  }
  return map;
}

export function buildWorkloadSnapshot(schedules: Schedule[], limit = 5): WorkloadRow[] {
  const totals = new Map<number, WorkloadRow>();
  for (const schedule of schedules) {
    const current = totals.get(schedule.staff.id) ?? {
      staffId: schedule.staff.id,
      staffName: schedule.staff.fullName,
      shifts: 0,
    };
    current.shifts += 1;
    totals.set(schedule.staff.id, current);
  }
  return Array.from(totals.values()).sort((a, b) => b.shifts - a.shifts).slice(0, limit);
}

export function buildConflictKeys(conflicts: ConflictDetail[]) {
  return new Set(conflicts.map((conflict) => `${conflict.workDate.substring(0, 10)}-${conflict.shiftTypeId}`));
}

export function buildOperationalKpis(params: {
  schedules: Schedule[];
  requirements: ShiftRequirement[];
  conflictList: { shiftTypeId: string }[];
  activeStaff: Staff[];
  pendingLeaveRequests?: number;
}): OperationalKpi[] {
  const { schedules, requirements, conflictList, activeStaff, pendingLeaveRequests = 0 } = params;

  // Single-pass aggregation for better performance
  let required = 0;
  let assigned = 0;
  const understaffedDaysSet = new Set<string>();
  const l01ByStaff = new Map<number, number>();

  for (const req of requirements) {
    required += req.requiredStaffCount;
    assigned += req.assignedStaffCount;
    if (req.assignedStaffCount < req.requiredStaffCount) {
      // Use substring instead of split for better performance
      understaffedDaysSet.add(req.workDate.substring(0, 10));
    }
  }

  for (const schedule of schedules) {
    if (schedule.shiftType.id === "L01") {
      l01ByStaff.set(schedule.staff.id, (l01ByStaff.get(schedule.staff.id) ?? 0) + 1);
    }
  }

  const coverage = required > 0 ? Math.round((assigned / required) * 100) : schedules.length > 0 ? 100 : 0;
  const understaffedDays = understaffedDaysSet.size;
  const fatigueRisk = Array.from(l01ByStaff.values()).filter((count) => count >= 4).length;
  const openConflicts = conflictList.length > 0
    ? conflictList.length
    : schedules.filter((schedule) => schedule.hasConflict).length;

  return [
    {
      label: "Tỷ lệ phủ",
      value: `${coverage}%`,
      helper: required > 0 ? `${assigned}/${required} nhu cầu đã phủ` : "Chưa có yêu cầu nhân sự",
      tone: coverage >= 95 ? "success" : coverage >= 80 ? "warning" : "danger",
      trend: coverage >= 95 ? "Đạt ngưỡng vận hành" : "Cần rà soát coverage",
      icon: "donut_large",
    },
    {
      label: "Ngày thiếu nhân sự",
      value: understaffedDays,
      helper: understaffedDays > 0 ? "Ngày thiếu nhân sự so với yêu cầu" : "Không có ngày thiếu nhân sự",
      tone: understaffedDays > 0 ? "warning" : "success",
      trend: understaffedDays > 0 ? "Cần bổ sung" : "Ổn định",
      icon: "group_remove",
    },
    {
      label: "Nguy cơ quá tải",
      value: fatigueRisk,
      helper: activeStaff.length > 0 ? "Nhân sự có từ 4 ca L01 trong kỳ" : "Chưa có dữ liệu nhân sự",
      tone: fatigueRisk > 0 ? "danger" : "success",
      trend: fatigueRisk > 0 ? "Nguy cơ quá tải" : "Trong ngưỡng",
      icon: "battery_alert",
    },
    {
      label: "Ảnh hưởng nghỉ phép",
      value: pendingLeaveRequests,
      helper: pendingLeaveRequests > 0 ? "Yêu cầu nghỉ phép đang chờ duyệt" : "Không có yêu cầu nghỉ phép",
      tone: pendingLeaveRequests > 0 ? "warning" : "success",
      trend: pendingLeaveRequests > 0 ? "Cần duyệt/điều phối" : "Không ảnh hưởng",
      icon: "event_busy",
    },
    {
      label: "Xung đột mở",
      value: openConflicts,
      helper: openConflicts > 0 ? "Chặn publish kỳ lịch" : "Không có xung đột mở",
      tone: openConflicts > 0 ? "danger" : "success",
      trend: openConflicts > 0 ? "Cần xử lý" : "Sẵn sàng review",
      icon: "warning",
    },
  ];
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
