import type { ConflictDetail, CompensationDay, Schedule, SchedulePeriod, Staff } from "@/types/api";
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

function getInitials(name: string | null | undefined): string {
  if (!name) return "";
  return String(name)
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
  const dateFormatted = schedule.workDate ? String(schedule.workDate).split("-").reverse().join("/") : "";
  const shiftTime = schedule.shiftType.startTime && schedule.shiftType.endTime
    ? `${schedule.shiftType.startTime} - ${schedule.shiftType.endTime}`
    : "";
  const statusMap: Record<string, "approved" | "pending" | "draft"> = {
    PUBLISHED: "approved",
    DRAFT: "draft",
    ARCHIVED: "draft",
  };
  const status = schedule.period?.status ? (statusMap[schedule.period.status] ?? "pending") : "pending";
  const compDateFormatted = schedule.compensationDate ? String(schedule.compensationDate).split("-").reverse().join("/") : null;
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
      ? `${String(schedule.period.startDate).split("-").reverse().join("/")} - ${String(schedule.period.endDate).split("-").reverse().join("/")}`
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
  const compByDate = new Map<string, CompensationDay[]>();
  for (const cd of compensationDays) {
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

  const conflictByDate = new Map<string, ConflictDetail[]>();
  for (const conflict of conflicts) {
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
  schedules: Schedule[],
  filter?: { shiftTypeId?: ScheduleTab }
) {
  const map: Record<string, { assigned: number }> = {};
  const includeAll = !filter?.shiftTypeId || filter.shiftTypeId === "ALL";
  for (const schedule of schedules) {
    if (!schedule.shiftType || !schedule.workDate) continue;
    if (!includeAll && schedule.shiftType.id !== filter?.shiftTypeId) continue;
    const key = String(schedule.workDate).substring(0, 10);
    const prev = map[key] ?? { assigned: 0 };
    map[key] = { assigned: prev.assigned + 1 };
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
  conflictList: { shiftTypeId: string }[];
  activeStaff: Staff[];
  pendingLeaveRequests?: number;
}): OperationalKpi[] {
  const { schedules, conflictList, activeStaff, pendingLeaveRequests = 0 } = params;

  const l01ByStaff = new Map<number, number>();
  const totalShifts = schedules.length;

  for (const schedule of schedules) {
    if (schedule.shiftType.id === "L01") {
      l01ByStaff.set(schedule.staff.id, (l01ByStaff.get(schedule.staff.id) ?? 0) + 1);
    }
  }

  const staffById = new Map(activeStaff.map((s) => [s.id, s]));
  const fatigueRisk = Array.from(l01ByStaff.entries()).filter(([staffId, count]) => {
    const staff = staffById.get(staffId);
    const threshold = staff?.maxShiftsPerMonth && staff.maxShiftsPerMonth > 0 ? staff.maxShiftsPerMonth : 4;
    return count >= threshold;
  }).length;
  const openConflicts = conflictList.length > 0
    ? conflictList.length
    : schedules.filter((schedule) => schedule.hasConflict).length;

  return [
    {
      label: "Tổng ca trực",
      value: totalShifts,
      helper: schedules.length > 0 ? `${schedules.length} ca đã xếp` : "Chưa có lịch",
      tone: totalShifts > 0 ? "success" : "warning",
      trend: totalShifts > 0 ? "Đã xếp lịch" : "Chưa xếp lịch",
      icon: "calendar_month",
    },
    {
      label: "Nguy cơ quá tải",
      value: fatigueRisk,
      helper: activeStaff.length > 0 ? "Nhân sự đạt ngưỡng L01 tối đa cá nhân" : "Chưa có dữ liệu nhân sự",
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
