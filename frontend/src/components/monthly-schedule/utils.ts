import type { ConflictCheckResponse, ConflictDetail, CompensationDay, Schedule, SchedulePeriod, ShiftRequirement, Staff } from "@/types/api";
import { SHIFT_TYPE_COLORS, SHIFT_TYPE_LABELS, WEEKDAYS } from "./constants";
import type { CalendarAnnotation, OperationalKpi, ScheduleTab, WorkloadRow } from "./types";

const AVATAR_COLORS = [
  "bg-blue-100 text-blue-700",
  "bg-green-100 text-green-700",
  "bg-purple-100 text-purple-700",
  "bg-orange-100 text-orange-700",
  "bg-pink-100 text-pink-700",
  "bg-teal-100 text-teal-700",
];

export function formatDate(date?: string | null) {
  if (!date) return "";
  return new Date(date).toLocaleDateString("vi-VN");
}

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
  const shiftColor = SHIFT_TYPE_COLORS[shiftTypeId] ?? "bg-gray-500";
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
        role: schedule.staff.roles?.includes("ADMIN")
          ? "Quản trị viên"
          : schedule.staff.roles?.includes("MANAGER")
            ? "Quản lý"
            : "Nhân viên",
        roleBadge: schedule.staff.roles?.includes("ADMIN")
          ? "primary"
          : schedule.staff.roles?.includes("MANAGER")
            ? "secondary"
            : "neutral",
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
  const compAnnotations = compensationDays.map((cd) => ({
    date: cd.compensationDate.split("T")[0],
    label: `Nghỉ bù · ${cd.staffName}`,
    tone: "compLeave" as const,
    description: `Ngày nghỉ bù của ${cd.staffName} — không thể xếp lịch`,
  }));

  const conflictAnnotations = conflicts.map((conflict) => ({
    date: conflict.workDate.split("T")[0],
    label: `Xung đột · ${conflict.staffName}`,
    tone: "warning" as const,
    description: conflict.conflictReasons.join(" • "),
  }));

  return [...compAnnotations, ...conflictAnnotations];
}

export function buildCoverageMap(requirements: ShiftRequirement[]) {
  const map: Record<string, { required: number; assigned: number }> = {};
  for (const req of requirements) {
    const key = req.workDate.split("T")[0];
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
  return new Set(conflicts.map((conflict) => `${conflict.workDate.split("T")[0]}-${conflict.shiftTypeId}`));
}

export function buildOperationalKpis(params: {
  schedules: Schedule[];
  requirements: ShiftRequirement[];
  conflictData: ConflictCheckResponse | null;
  activeStaff: Staff[];
}): OperationalKpi[] {
  const { schedules, requirements, conflictData, activeStaff } = params;
  const required = requirements.reduce((sum, req) => sum + req.requiredStaffCount, 0);
  const assigned = requirements.reduce((sum, req) => sum + req.assignedStaffCount, 0);
  const coverage = required > 0 ? Math.round((assigned / required) * 100) : schedules.length > 0 ? 100 : 0;
  const understaffedDays = new Set(
    requirements
      .filter((req) => req.assignedStaffCount < req.requiredStaffCount)
      .map((req) => req.workDate.split("T")[0]),
  ).size;
  const l01ByStaff = new Map<number, number>();
  for (const schedule of schedules) {
    if (schedule.shiftType.id !== "L01") continue;
    l01ByStaff.set(schedule.staff.id, (l01ByStaff.get(schedule.staff.id) ?? 0) + 1);
  }
  const fatigueRisk = Array.from(l01ByStaff.values()).filter((count) => count >= 4).length;
  const openConflicts = conflictData?.totalConflicts ?? schedules.filter((schedule) => schedule.hasConflict).length;

  return [
    {
      label: "Coverage %",
      value: `${coverage}%`,
      helper: required > 0 ? `${assigned}/${required} nhu cầu đã phủ` : "Chưa có yêu cầu nhân sự",
      tone: coverage >= 95 ? "success" : coverage >= 80 ? "warning" : "danger",
      trend: coverage >= 95 ? "Đạt ngưỡng vận hành" : "Cần rà soát coverage",
      icon: "donut_large",
    },
    {
      label: "Understaffed Days",
      value: understaffedDays,
      helper: understaffedDays > 0 ? "Ngày thiếu nhân sự so với yêu cầu" : "Không có ngày thiếu nhân sự",
      tone: understaffedDays > 0 ? "warning" : "success",
      trend: understaffedDays > 0 ? "Cần bổ sung" : "Ổn định",
      icon: "group_remove",
    },
    {
      label: "Fatigue Risk",
      value: fatigueRisk,
      helper: activeStaff.length > 0 ? "Nhân sự có từ 4 ca L01 trong kỳ" : "Chưa có dữ liệu nhân sự",
      tone: fatigueRisk > 0 ? "danger" : "success",
      trend: fatigueRisk > 0 ? "Nguy cơ quá tải" : "Trong ngưỡng",
      icon: "battery_alert",
    },
    {
      label: "Pending Leave Impact",
      value: "N/A",
      helper: "Workspace hiện chưa nạp dữ liệu nghỉ phép chờ duyệt",
      tone: "neutral",
      trend: "Cần tích hợp nguồn leave request",
      icon: "event_busy",
    },
    {
      label: "Open Conflicts",
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
