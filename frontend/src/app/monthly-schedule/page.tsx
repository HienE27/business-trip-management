"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { WorkflowShell } from "@/components/layout/WorkflowShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { SkeletonCalendar, SkeletonKPI, SkeletonTable } from "@/components/ui/Skeleton";
import { ConflictInspector } from "@/components/schedule-summary/ConflictInspector";
import { ConflictResolutionModal } from "@/components/ui/ConflictResolutionModal";
import { CoverageInspector } from "@/components/schedule-summary/CoverageInspector";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { ShiftDetailInfo } from "@/components/shift-detail/ShiftDetailInfo";
import { ShiftDetailTable } from "@/components/shift-detail/ShiftDetailTable";
import { useRole, canManage } from "@/hooks/useRole";
import { useScheduleWorkspace } from "@/hooks/useScheduleWorkspace";
import { useAutoSchedule } from "@/hooks/useAutoSchedule";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  ConflictDetail,
  Schedule,
  SchedulePeriod,
  Staff,
} from "@/types/api";
import type { ConflictItem } from "@/types/schedule";

type ScheduleTab = "L01" | "L02" | "L03" | "L04";
type ViewMode = "calendar" | "table";
type LegacyPanel = "overview" | "auto-schedule" | "conflicts" | "summary";

type WorkflowStep = {
  id: LegacyPanel | "review" | "publish" | "notify";
  title: string;
  description: string;
};

const TAB_OPTIONS: { id: ScheduleTab; label: string; shortLabel: string; description: string }[] = [
  { id: "L01", label: "Trực 24/24", shortLabel: "24/24", description: "Ca trực xuyên ngày, có nghỉ bù và ràng buộc fatigue cao nhất." },
  { id: "L02", label: "Thông tầm", shortLabel: "TT", description: "Ca ngày liên tục, không nghỉ trưa và không được trùng với trực 24/24." },
  { id: "L03", label: "PK dịch vụ", shortLabel: "PKDV", description: "Lịch phòng khám dịch vụ theo ngày, ưu tiên theo năng lực chuyên môn." },
  { id: "L04", label: "PK chuyên gia", shortLabel: "PKCG", description: "Lịch phòng khám chuyên gia theo chuyên khoa, không được trùng với PKDV." },
];

const WORKFLOW_STEPS: WorkflowStep[] = [
  { id: "auto-schedule", title: "Auto schedule", description: "Tạo phương án phân công ban đầu cho kỳ lịch." },
  { id: "conflicts", title: "Conflict check", description: "Quét xung đột và đánh dấu lịch cần xử lý trước khi công bố." },
  { id: "review", title: "Review", description: "Đối chiếu tải công việc, ngày nghỉ bù và mức độ phủ lịch." },
  { id: "publish", title: "Publish", description: "Khóa bản nháp, công bố kỳ lịch hợp lệ và chuyển trạng thái vận hành." },
  { id: "notify", title: "Notify", description: "Gửi thông báo cho nhân sự và đẩy dữ liệu sang các màn báo cáo." },
];

function formatDateRange(period: SchedulePeriod | null) {
  if (!period) return "Chưa chọn kỳ lịch";
  const start = new Date(period.startDate).toLocaleDateString("vi-VN");
  const end = new Date(period.endDate).toLocaleDateString("vi-VN");
  return `${start} – ${end}`;
}

function getLegacyTab(searchParams: URLSearchParams): ScheduleTab {
  const tab = searchParams.get("tab")?.toUpperCase();
  if (tab === "L01" || tab === "L02" || tab === "L03" || tab === "L04") {
    return tab;
  }
  return "L01";
}

function getLegacyPanel(searchParams: URLSearchParams): LegacyPanel {
  const panel = searchParams.get("panel");
  if (panel === "auto-schedule" || panel === "conflicts" || panel === "summary") {
    return panel;
  }
  return "overview";
}

function getViewMode(searchParams: URLSearchParams): ViewMode {
  return searchParams.get("view") === "table" ? "table" : "calendar";
}

function getShiftTypeLabel(id: ScheduleTab) {
  return TAB_OPTIONS.find((option) => option.id === id)?.label ?? id;
}

function getStatusBadge(status: SchedulePeriod["status"] | undefined) {
  if (status === "PUBLISHED") return "bg-secondary-container text-on-secondary-container border border-secondary/20";
  if (status === "ARCHIVED") return "bg-surface-container-highest text-outline border border-outline-variant";
  return "bg-primary-fixed text-primary border border-primary/20";
}

const WEEKDAYS = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];

function getWeekday(dateStr: string): string {
  const d = new Date(dateStr + "T00:00:00");
  return WEEKDAYS[d.getDay()] ?? "";
}

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

const AVATAR_COLORS = [
  "bg-blue-100 text-blue-700",
  "bg-green-100 text-green-700",
  "bg-purple-100 text-purple-700",
  "bg-orange-100 text-orange-700",
  "bg-pink-100 text-pink-700",
  "bg-teal-100 text-teal-700",
];

function getAvatarColor(id: number): string {
  return AVATAR_COLORS[id % AVATAR_COLORS.length];
}

const SHIFT_TYPE_COLORS: Record<string, string> = {
  L01: "bg-red-500",
  L02: "bg-blue-500",
  L03: "bg-green-500",
  L04: "bg-purple-500",
};

const SHIFT_TYPE_LABELS: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Lịch thông tầm",
  L03: "Phòng khám dịch vụ",
  L04: "Phòng khám chuyên gia",
};

function buildShiftDetailViewModel(schedule: Schedule) {
  const shiftTypeId = schedule.shiftType.id;
  const shiftTypeName = schedule.shiftType.name ?? SHIFT_TYPE_LABELS[shiftTypeId] ?? shiftTypeId;
  const shiftColor = SHIFT_TYPE_COLORS[shiftTypeId] ?? "bg-gray-500";
  const weekday = getWeekday(schedule.workDate);
  const dateFormatted = schedule.workDate ? schedule.workDate.split("-").reverse().join("/") : "";
  const shiftTime =
    schedule.shiftType.startTime && schedule.shiftType.endTime
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

type ShiftDetailViewModel = ReturnType<typeof buildShiftDetailViewModel>;

type QuickAddFormProps = {
  date: Date;
  periodId: number | null;
  defaultShiftTypeId: string;
  staffList: Staff[];
  onSuccess: () => void;
  onCancel: () => void;
};

function QuickAddForm({ date, periodId, defaultShiftTypeId, staffList, onSuccess, onCancel }: QuickAddFormProps) {
  const [shiftTypeId, setShiftTypeId] = useState(defaultShiftTypeId);
  const [staffId, setStaffId] = useState<number | "">("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dateStr = date.toISOString().slice(0, 10);
  const dateLabel = date.toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!periodId || staffId === "") return;
    setSubmitting(true);
    setError(null);
    try {
      await api.post("/schedules", {
        periodId,
        workDate: dateStr,
        staffId,
        shiftTypeId,
      });
      onSuccess();
    } catch (err) {
      setError(getErrorMessage(err, "Không thể tạo lịch. Vui lòng thử lại."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="text-label-md text-on-surface-variant">
        Ngày: <span className="font-semibold text-on-surface">{dateLabel}</span>
      </div>

      {error && (
        <div className="bg-error-container border border-error/20 rounded-lg px-4 py-2 text-label-sm text-on-error-container flex items-center gap-2">
          <span className="material-symbols-outlined text-[16px]">error</span>
          {error}
        </div>
      )}

      <div>
        <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="qa-shift-type">
          Loại lịch
        </label>
        <div className="relative">
          <select
            id="qa-shift-type"
            className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
            value={shiftTypeId}
            onChange={(e) => setShiftTypeId(e.target.value)}
            required
          >
            <option value="L01">Trực 24/24</option>
            <option value="L02">Thông tầm</option>
            <option value="L03">Phòng khám dịch vụ</option>
            <option value="L04">Phòng khám chuyên gia</option>
          </select>
          <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">expand_more</span>
        </div>
      </div>

      <div>
        <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="qa-staff">
          Nhân sự
        </label>
        <div className="relative">
          <select
            id="qa-staff"
            className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
            value={staffId}
            onChange={(e) => setStaffId(e.target.value ? Number(e.target.value) : "")}
            required
          >
            <option value="">Chọn nhân sự…</option>
            {staffList.map((s) => (
              <option key={s.id} value={s.id}>{s.fullName}</option>
            ))}
          </select>
          <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">expand_more</span>
        </div>
      </div>

      <ModalFooter>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          Hủy
        </button>
        <button
          type="submit"
          disabled={submitting || staffId === ""}
          className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md hover:bg-primary/90 transition-colors disabled:opacity-60 flex items-center gap-2"
        >
          {submitting ? (
            <><div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" aria-hidden="true" /><span>Đang tạo…</span></>
          ) : (
            <><span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span><span>Tạo lịch</span></>
          )}
        </button>
      </ModalFooter>
    </form>
  );
}

export default function MonthlySchedulePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const role = useRole();
  const selectedTab = getLegacyTab(searchParams);
  const selectedPanel = getLegacyPanel(searchParams);

  const [wsState, wsActions] = useScheduleWorkspace();
  const [asState, asActions] = useAutoSchedule();

  const {
    periods,
    selectedPeriodId,
    schedules,
    activeStaff,
    conflictData,
    compensationDays,
    requirements,
    specialties,
    loading,
    refreshing,
    message,
  } = wsState;

  const {
    previewResult,
    editedPreview,
    applying: applyingPreview,
    running: runningAutoSchedule,
    message: asMessage,
    algorithmType,
  } = asState;

  // Parse URL params first (before state that depends on them)
  const scheduleIdParam = searchParams.get("scheduleId");
  const parsedScheduleId = scheduleIdParam ? parseInt(scheduleIdParam, 10) : null;
  const staffIdParam = searchParams.get("staffId");
  const parsedStaffId = staffIdParam ? parseInt(staffIdParam, 10) : null;
  const specialtyIdParam = searchParams.get("specialtyId");
  const parsedSpecialtyId = specialtyIdParam ? parseInt(specialtyIdParam, 10) : null;

  const [selectedConflict, setSelectedConflict] = useState<ConflictDetail | null>(null);
  const [resolvingConflict, setResolvingConflict] = useState<ConflictItem | null>(null);
  const [focusDate, setFocusDate] = useState<string | null>(null);
  const [addModalDate, setAddModalDate] = useState<Date | null>(null);
  const [staffFilterId, setStaffFilterId] = useState<number | null>(parsedStaffId);
  const [specialtyFilterId, setSpecialtyFilterId] = useState<number | null>(parsedSpecialtyId);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [detailScheduleId, setDetailScheduleId] = useState<number | null>(null);
  const [detailSchedule, setDetailSchedule] = useState<Schedule | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const selectedPeriod = periods.find((period) => period.id === selectedPeriodId) ?? null;
  const resolvedViewMode = getViewMode(searchParams);

  const fetchScheduleDetail = useCallback(async (id: number) => {
    setDetailLoading(true);
    try {
      const res = await api.getScheduleById(id);
      setDetailSchedule(res.data);
    } catch {
      setDetailSchedule(null);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  // Sync scheduleId from URL param
  const scheduleIdRef = useRef<number | null>(null);
  useEffect(() => {
    if (parsedScheduleId !== null && parsedScheduleId !== scheduleIdRef.current) {
      scheduleIdRef.current = parsedScheduleId;
      setDetailScheduleId(parsedScheduleId);
      void fetchScheduleDetail(parsedScheduleId);
    }
  }, [parsedScheduleId, fetchScheduleDetail]);

  const initialCalendarYear = useMemo(() => {
    if (selectedPeriod?.startDate) {
      const d = new Date(selectedPeriod.startDate);
      return d.getFullYear();
    }
    return new Date().getFullYear();
  }, [selectedPeriod]);

  const initialCalendarMonth = useMemo(() => {
    if (selectedPeriod?.startDate) {
      const d = new Date(selectedPeriod.startDate);
      return d.getMonth();
    }
    return new Date().getMonth();
  }, [selectedPeriod]);

  const filteredSchedules = useMemo(
    () => schedules.filter((schedule) => schedule.shiftType.id === selectedTab),
    [schedules, selectedTab],
  );

  const conflictList = useMemo(
    () => (conflictData?.conflicts ?? []).filter((item) => item.shiftTypeId === selectedTab),
    [conflictData, selectedTab],
  );

  const calendarAnnotations = useMemo(() => {
    const compAnnotations = compensationDays.map((cd) => ({
      date: cd.compensationDate.split("T")[0],
      label: `Nghỉ bù · ${cd.staffName}`,
      tone: "compLeave" as const,
      description: `Ngày nghỉ bù của ${cd.staffName} — không thể xếp lịch`,
    }));

    const conflictAnnotations = conflictList.map((conflict) => ({
      date: conflict.workDate.split("T")[0],
      label: `Xung đột · ${conflict.staffName}`,
      tone: "warning" as const,
      description: conflict.conflictReasons.join(" • "),
    }));

    return [...compAnnotations, ...conflictAnnotations];
  }, [conflictList, compensationDays]);

  const computedCoverages = useMemo(() => {
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
  }, [requirements]);

  const kpis = useMemo(() => {
    const total = filteredSchedules.length;
    const uniqueStaff = new Set(filteredSchedules.map((schedule) => schedule.staff.id)).size;
    const conflicts = conflictList.length;
    const compensationDays = filteredSchedules.filter((schedule) => Boolean(schedule.compensationDate)).length;
    return [
      { label: "Tổng phân công", value: total, helper: getShiftTypeLabel(selectedTab) },
      { label: "Nhân sự tham gia", value: uniqueStaff, helper: "Trong kỳ đang chọn" },
      { label: "Ngày nghỉ bù", value: compensationDays, helper: selectedTab === "L01" ? "Đã khóa trên lịch" : "Theo dữ liệu liên quan" },
      { label: "Xung đột", value: conflicts, helper: conflicts > 0 ? "Cần xử lý trước publish" : "Không phát hiện" },
    ];
  }, [conflictList.length, filteredSchedules, selectedTab]);

  const workloadSnapshot = useMemo(() => {
    const totals = new Map<number, { staffName: string; shifts: number }>();
    for (const schedule of filteredSchedules) {
      const current = totals.get(schedule.staff.id) ?? { staffName: schedule.staff.fullName, shifts: 0 };
      current.shifts += 1;
      totals.set(schedule.staff.id, current);
    }
    return Array.from(totals.values()).sort((a, b) => b.shifts - a.shifts).slice(0, 5);
  }, [filteredSchedules]);

  const focusSchedules = useMemo(() => {
    if (!focusDate) return filteredSchedules.slice(0, 8);
    return filteredSchedules.filter((schedule) => schedule.workDate.startsWith(focusDate));
  }, [filteredSchedules, focusDate]);

  const setQueryState = useCallback(
    (next: { tab?: ScheduleTab; panel?: LegacyPanel; view?: ViewMode }) => {
      const params = new URLSearchParams(searchParams.toString());
      if (next.tab) params.set("tab", next.tab);
      if (next.panel) params.set("panel", next.panel);
      if (next.view) params.set("view", next.view);
      router.replace(`/monthly-schedule?${params.toString()}`);
    },
    [router, searchParams],
  );

  const handlePeriodChange = (periodId: number) => {
    void wsActions.setSelectedPeriodId(periodId);
  };

  const handlePreviewAutoSchedule = () => {
    if (!selectedPeriodId) return;
    void asActions.runPreview(selectedPeriodId);
    wsActions.clearMessage();
    setQueryState({ panel: "auto-schedule" });
  };

  const handleApplyPreview = async () => {
    await asActions.applyPreview(selectedPeriodId, editedPreview, () => {
      void wsActions.refreshWorkspace();
    });
  };

  const handleCheckConflicts = async () => {
    setCheckingConflicts(true);
    try {
      await wsActions.checkConflicts();
    } finally {
      setCheckingConflicts(false);
    }
    setQueryState({ panel: "conflicts" });
  };

  const handlePublish = async () => {
    setPublishing(true);
    try {
      await wsActions.publishPeriod();
    } finally {
      setPublishing(false);
    }
  };

  const handleSendNotifications = async () => {
    await wsActions.sendNotifications();
  };

  const handleNotifyWorkflowStep = () => {
    void handleSendNotifications();
  };

  if (loading) {
    return (
      <WorkflowShell
        section="monthly-schedule"
        title="Lập lịch tháng"
        description="Điều phối kỳ lịch theo workflow vận hành thay vì chỉnh sửa rời từng record."
      >
        <SkeletonKPI />
        <SkeletonCalendar />
        <SkeletonTable rows={5} cols={4} />
      </WorkflowShell>
    );
  }

  return (
    <WorkflowShell
      section="monthly-schedule"
      title="Lập lịch tháng"
      description="Điều phối kỳ lịch theo workflow: auto schedule, conflict check, review, publish và notify."
    >
      <section className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <SectionCard
          title="Kỳ lịch đang vận hành"
          description="Gom tất cả thao tác điều phối vào một màn trung tâm thay cho các route CRUD rời rạc trước đây."
          action={
            <div className="flex flex-wrap items-center gap-2">
              <select
                className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
                value={selectedPeriodId ?? ""}
                onChange={(event) => void handlePeriodChange(Number(event.target.value))}
              >
                {periods.map((period) => (
                  <option key={period.id} value={period.id}>{period.periodName}</option>
                ))}
              </select>
              <button
                type="button"
                onClick={() => void wsActions.refreshWorkspace()}
                className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low"
                aria-label="Làm mới dữ liệu kỳ lịch"
              >
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">sync</span>
                {refreshing ? "Đang tải..." : "Làm mới"}
              </button>
            </div>
          }
        >
          <div className="grid gap-4 p-5 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-3">
                <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-[12px] font-semibold ${getStatusBadge(selectedPeriod?.status)}`}>
                  <span className="material-symbols-outlined text-[14px]">event_available</span>
                  {selectedPeriod?.status ?? "DRAFT"}
                </span>
                <span className="text-body-sm text-on-surface-variant">{formatDateRange(selectedPeriod)}</span>
              </div>
              <div>
                <h2 className="text-headline-md text-on-surface">{selectedPeriod?.periodName ?? "Chưa có kỳ lịch"}</h2>
                <p className="mt-1 text-body-sm leading-6 text-on-surface-variant">
                  Tập trung lập lịch theo kỳ, thay vì tách thành nhiều menu như `Trực 24/24`, `Thông tầm`, `PK dịch vụ`, `PK chuyên gia`, `Conflict Check` hay `Auto Schedule`.
                </p>
              </div>
            </div>

            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-1">
              <button
                type="button"
                onClick={() => void handlePreviewAutoSchedule()}
                className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-60"
                disabled={runningAutoSchedule || !selectedPeriodId || selectedPeriod?.status !== "DRAFT"}
              >
                <span className="material-symbols-outlined text-[18px]">auto_mode</span>
                {runningAutoSchedule ? "Đang tạo preview" : "Auto Schedule"}
              </button>
              <button
                type="button"
                onClick={() => void handleCheckConflicts()}
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60"
                disabled={checkingConflicts || !selectedPeriodId || selectedPeriod?.status !== "DRAFT"}
              >
                <span className="material-symbols-outlined text-[18px]">warning</span>
                {checkingConflicts ? "Đang kiểm tra" : "Conflict Check"}
              </button>
              {canManage(role) && (
                <button
                  type="button"
                  onClick={() => void handlePublish()}
                  className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60"
                  disabled={publishing || !selectedPeriodId || selectedPeriod?.status !== "DRAFT"}
                >
                  <span className="material-symbols-outlined text-[18px]">publish</span>
                  {publishing ? "Đang publish" : "Publish"}
                </button>
              )}
              <button
                type="button"
                onClick={() => setQueryState({ panel: "summary" })}
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low"
              >
                <span className="material-symbols-outlined text-[18px]">assessment</span>
                Review & Report
              </button>
            </div>
          </div>
        </SectionCard>

        <SectionCard
          title="Workflow vận hành"
          description="Biến lập lịch tháng thành luồng điều phối có checkpoint rõ ràng."
        >
          <div className="space-y-3 p-5">
            {WORKFLOW_STEPS.map((step, index) => {
              const isActive = step.id === selectedPanel || (selectedPanel === "overview" && step.id === "review");
              const isCompleted = step.id === "auto-schedule"
                ? Boolean(previewResult)
                : step.id === "conflicts"
                  ? Boolean(conflictData)
                  : step.id === "publish"
                    ? selectedPeriod?.status === "PUBLISHED"
                    : false;

              return (
                <button
                  key={step.id}
                  type="button"
                  onClick={() => {
                    if (step.id === "notify") {
                      void handleNotifyWorkflowStep();
                    } else {
                      setQueryState({ panel: step.id as LegacyPanel });
                    }
                  }}
                  aria-current={isActive ? "step" : undefined}
                  className={`flex w-full items-start gap-3 rounded-lg border px-4 py-3 text-left transition-colors focus-visible:ring-2 focus-visible:ring-primary ${isActive ? "border-primary bg-primary-fixed/40" : "border-outline-variant bg-surface hover:bg-surface-container-low"}`}
                >
                  <div aria-label={`Bước ${index + 1} trong ${WORKFLOW_STEPS.length}`} className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[12px] font-bold ${isCompleted ? "bg-secondary-container text-on-secondary-container" : isActive ? "bg-primary text-on-primary" : "bg-surface-container-high text-on-surface-variant"}`}>
                    {isCompleted ? <span className="material-symbols-outlined text-[16px]">check</span> : index + 1}
                  </div>
                  <div>
                    <p className="text-label-md font-semibold text-on-surface">{step.title}</p>
                    <p className="mt-1 text-body-sm leading-5 text-on-surface-variant">{step.description}</p>
                  </div>
                </button>
              );
            })}
          </div>
        </SectionCard>
      </section>

      {message ? (
        <div className="rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-3 text-body-sm text-on-surface shadow-sm">
          {message}
        </div>
      ) : null}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {kpis.map((item) => (
          <article key={item.label} className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">{item.label}</p>
            <p className="mt-3 text-display-lg text-on-surface">{item.value}</p>
            <p className="mt-1 text-body-sm text-on-surface-variant">{item.helper}</p>
          </article>
        ))}
      </section>

      <SectionCard
        title="Tabs loại lịch"
        description="Bốn loại lịch nay chỉ còn là tabs bên trong module lập lịch tháng, không còn đứng riêng ở sidebar."
        action={
          <div className="flex items-center gap-1 rounded-lg bg-surface-container-low p-1">
              <button
                type="button"
                onClick={() => {
                  setQueryState({ view: "calendar" });
                }}
                aria-label="Chế độ xem lịch theo tháng"
                aria-pressed={resolvedViewMode === "calendar"}
                className={`rounded-md px-3 py-1.5 text-label-sm transition-colors focus-visible:ring-2 focus-visible:ring-primary ${resolvedViewMode === "calendar" ? "bg-surface-container-lowest text-primary shadow-sm" : "text-on-surface-variant"}`}
              >
                Calendar View
              </button>
              <button
                type="button"
                onClick={() => {
                  setQueryState({ view: "table" });
                }}
                aria-label="Chế độ xem bảng danh sách"
                aria-pressed={resolvedViewMode === "table"}
                className={`rounded-md px-3 py-1.5 text-label-sm transition-colors focus-visible:ring-2 focus-visible:ring-primary ${resolvedViewMode === "table" ? "bg-surface-container-lowest text-primary shadow-sm" : "text-on-surface-variant"}`}
              >
                Table View
              </button>
          </div>
        }
      >
        <div className="border-b border-outline-variant px-5 pt-4">
          <div className="flex flex-wrap gap-2">
            {TAB_OPTIONS.map((option) => {
              const isActive = selectedTab === option.id;
              return (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => setQueryState({ tab: option.id })}
                  aria-selected={isActive}
                  role="tab"
                  className={`inline-flex items-center gap-2 rounded-t-lg border border-b-0 px-4 py-2 text-label-md transition-colors focus-visible:ring-2 focus-visible:ring-primary ${isActive ? "border-primary bg-primary-fixed text-primary" : "border-outline-variant bg-surface text-on-surface-variant hover:bg-surface-container-low"}`}
                >
                  <span className="rounded-full bg-surface-container-low px-2 py-0.5 text-[11px] font-semibold">{option.shortLabel}</span>
                  {option.label}
                </button>
              );
            })}
          </div>
          <p className="pb-4 pt-3 text-body-sm text-on-surface-variant">
            {TAB_OPTIONS.find((option) => option.id === selectedTab)?.description}
          </p>
        </div>

        <div className="grid gap-4 p-5 md:grid-cols-1 xl:grid-cols-[minmax(0,1fr)_340px]">
          <div className="space-y-4">
            {filteredSchedules.length === 0 ? (
              <EmptyState
                icon="calendar_month"
                title="Chưa có phân công cho loại lịch này"
                description="Hãy chạy auto schedule, thêm lịch thủ công hoặc đổi sang kỳ có dữ liệu để tiếp tục audit workflow."
                action={
                  <button
                    type="button"
                    onClick={() => void handlePreviewAutoSchedule()}
                    className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90"
                  >
                    Chạy auto schedule preview
                  </button>
                }
              />
            ) : (
              <ScheduleCalendarWidget
                key={`${selectedTab}-${resolvedViewMode}`}
                schedules={filteredSchedules}
                calendarAnnotations={calendarAnnotations}
                coverages={computedCoverages}
                staffList={activeStaff}
                specialtyList={specialties}
                onRefresh={() => void wsActions.refreshWorkspace()}
                onDayClick={(date) => setFocusDate(date.toISOString().slice(0, 10))}
                onAddClick={(date) => setAddModalDate(date)}
                staffFilter={staffFilterId}
                specialtyFilter={specialtyFilterId}
                onStaffFilterChange={setStaffFilterId}
                onSpecialtyFilterChange={setSpecialtyFilterId}
                initialYear={initialCalendarYear}
                initialMonth={initialCalendarMonth}
                periodId={selectedPeriodId}
                onViewDetail={(schedule) => {
                  router.push(`/monthly-schedule?scheduleId=${schedule.id}`);
                }}
              />
            )}
          </div>

          <div className="space-y-4">
            <ConflictInspector
              conflicts={conflictList}
              emptyLabel="Không có xung đột cho loại lịch đang chọn."
              title="Conflict panel"
              description="Panel có thể thu gọn về mặt IA bằng query `panel=conflicts`, nhưng luôn giữ khả năng click để focus đúng ngày lịch."
              selectedConflict={selectedConflict}
              onSelect={(conflict) => {
                setSelectedConflict(conflict);
                setFocusDate(conflict.workDate.split("T")[0]);
                setQueryState({ panel: "conflicts" });
              }}
              onClose={() => setSelectedConflict(null)}
              onResolve={(conflict) => {
                setResolvingConflict({
                  id: String(conflict.scheduleId),
                  type: "SCHEDULE_CONFLICT",
                  staffName: conflict.staffName,
                  date: new Date(conflict.workDate).toLocaleDateString("vi-VN"),
                  severity: "Chặn lưu",
                  detail: `Xung đột: ${conflict.conflictReasons.join("; ")}`,
                  shiftType: conflict.shiftTypeName,
                  periodId: selectedPeriodId ?? undefined,
                  workDate: conflict.workDate,
                  shiftTypeId: conflict.shiftTypeId,
                  originalStaffId: conflict.scheduleId,
                });
              }}
            />

            <CoverageInspector
              coverageGaps={conflictData?.coverageGaps ?? []}
              hasCoverageGaps={conflictData?.hasCoverageGaps ?? false}
              totalCoverageGaps={conflictData?.totalCoverageGaps ?? 0}
            />

            <SectionCard
              title="Review snapshot"
              description={focusDate ? `Đang focus ${new Date(focusDate).toLocaleDateString("vi-VN")}` : "Danh sách nhanh để review lịch và phân công tải cao."}
            >
              <div className="divide-y divide-outline-variant">
                {focusSchedules.length === 0 ? (
                  <EmptyState
                    className="py-10"
                    icon="event_busy"
                    title="Không có lịch tại ngày đang focus"
                    description="Chọn một cảnh báo hoặc click ngày trên calendar để xem bản review liên quan."
                  />
                ) : (
                  focusSchedules.slice(0, 8).map((schedule) => (
                    <div key={schedule.id} className="flex items-start justify-between gap-3 px-4 py-3">
                      <div>
                        <p className="text-label-md font-semibold text-on-surface">{schedule.staff.fullName}</p>
                        <p className="mt-1 text-body-sm text-on-surface-variant">
                          {new Date(schedule.workDate).toLocaleDateString("vi-VN")} · {schedule.shiftType.name}
                        </p>
                      </div>
                      {schedule.hasConflict ? (
                        <span className="rounded-full bg-error-container px-3 py-1 text-[11px] font-semibold text-on-error-container">
                          Xung đột
                        </span>
                      ) : null}
                    </div>
                  ))
                )}
              </div>
            </SectionCard>
          </div>
        </div>
      </SectionCard>

      <section className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <SectionCard
          title="Auto schedule & coverage"
          description="Chỉnh sửa phương án trước khi áp dụng vào kỳ lịch."
          action={
            <button
              type="button"
              onClick={() => void handlePreviewAutoSchedule()}
              className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-60"
              disabled={runningAutoSchedule || !selectedPeriodId || selectedPeriod?.status !== "DRAFT"}
            >
              {runningAutoSchedule ? "Đang chạy preview" : "Làm mới preview"}
            </button>
          }
        >
          {previewResult ? (
            <div className="p-5 space-y-4">
              {/* KPI row */}
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant">Đã tạo</p>
                  <p className="mt-1 text-title-lg font-bold text-on-surface">{previewResult.totalSchedulesCreated}</p>
                </div>
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant">Coverage</p>
                  <p className="mt-1 text-title-lg font-bold text-on-surface">{previewResult.coverageRate}%</p>
                </div>
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant">Balance</p>
                  <p className="mt-1 text-title-lg font-bold text-on-surface">{previewResult.balanceScore}</p>
                </div>
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant">Xung đột</p>
                  <p className={`mt-1 text-title-lg font-bold ${previewResult.conflictCount > 0 ? "text-error" : "text-secondary"}`}>
                    {previewResult.conflictCount}
                  </p>
                </div>
              </div>

              {/* Editable preview table */}
              <div className="border border-outline-variant rounded-lg overflow-hidden">
                <div className="flex items-center justify-between px-4 py-3 bg-surface-container-low border-b border-outline-variant">
                  <p className="text-label-sm text-on-surface-variant">
                    Phương án — bấm vào nhân sự để thay đổi trước khi áp dụng
                  </p>
                  {editedPreview.length > 0 && (
                    <button
                      type="button"
                      onClick={() => { asActions.resetEdits(); }}
                      className="text-label-sm text-error hover:text-error/80 transition-colors"
                    >
                      Hủy thay đổi
                    </button>
                  )}
                </div>
                <div className="overflow-x-auto max-h-80">
                  <table className="w-full text-left">
                    <thead className="sticky top-0 bg-surface-container-low border-b border-outline-variant">
                      <tr>
                        <th className="px-3 py-2 text-label-xs text-on-surface-variant uppercase">Ngày</th>
                        <th className="px-3 py-2 text-label-xs text-on-surface-variant uppercase">Loại lịch</th>
                        <th className="px-3 py-2 text-label-xs text-on-surface-variant uppercase">Nhân sự</th>
                        <th className="px-3 py-2 text-label-xs text-on-surface-variant uppercase w-8">Tình trạng</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant">
                      {previewResult.schedules
                        .sort((a, b) => a.workDate.localeCompare(b.workDate))
                        .map((item) => {
                          const editIdx = editedPreview.findIndex(
                            (e) => e.workDate === item.workDate && e.shiftTypeId === item.shiftTypeId
                          );
                          const isEdited = editIdx >= 0;
                          const currentStaffId = isEdited ? editedPreview[editIdx].staffId : item.staffId;

                          return (
                            <tr key={`${item.workDate}-${item.shiftTypeId}-${item.staffId}`} className="hover:bg-surface-container-low transition-colors">
                              <td className="px-3 py-2 text-label-sm text-on-surface whitespace-nowrap">
                                {new Date(item.workDate).toLocaleDateString("vi-VN")}
                              </td>
                              <td className="px-3 py-2 text-label-sm text-on-surface">
                                <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-semibold ${
                                  item.shiftTypeId === "L01" ? "bg-red-50 text-red-700" :
                                  item.shiftTypeId === "L02" ? "bg-blue-50 text-blue-700" :
                                  item.shiftTypeId === "L03" ? "bg-green-50 text-green-700" :
                                  "bg-purple-50 text-purple-700"
                                }`}>
                                  {item.shiftTypeName}
                                </span>
                              </td>
                              <td className="px-3 py-2">
                                <div className="relative">
                                  <select
                                    value={currentStaffId}
                                    onChange={(e) => {
                                      asActions.editStaff(item.workDate, item.shiftTypeId, Number(e.target.value));
                                    }}
                                    className="h-7 pl-2 pr-7 text-label-sm bg-surface border border-transparent rounded appearance-none cursor-pointer hover:border-primary focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-colors w-full max-w-[180px]"
                                  >
                                    {activeStaff.map((staff) => (
                                      <option key={staff.id} value={staff.id}>
                                        {staff.fullName}
                                      </option>
                                    ))}
                                  </select>
                                  <span className="material-symbols-outlined absolute right-1 top-1/2 -translate-y-1/2 text-[14px] text-outline pointer-events-none">expand_more</span>
                                </div>
                              </td>
                              <td className="px-3 py-2 text-center">
                                {isEdited ? (
                                  <span className="material-symbols-outlined text-[16px] text-amber-500" title="Đã chỉnh sửa">edit</span>
                                ) : (
                                  <span className="material-symbols-outlined text-[16px] text-secondary" title="Theo thuật toán">auto_mode</span>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Apply button */}
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={handleApplyPreview}
                  disabled={applyingPreview || !previewResult}
                  className="rounded-lg bg-secondary px-4 py-2 text-label-md font-medium text-on-secondary transition-colors hover:bg-secondary/90 disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  {applyingPreview ? (
                    <><div className="size-4 animate-spin rounded-full border-2 border-on-secondary border-t-transparent" />Đang áp dụng…</>
                  ) : (
                    <><span className="material-symbols-outlined text-[18px]">check_circle</span>Áp dụng phương án{editedPreview.length > 0 ? ` (${editedPreview.length} thay đổi)` : ""}</>
                  )}
                </button>
                {editedPreview.length > 0 && (
                  <p className="text-label-sm text-on-surface-variant">
                    {editedPreview.length} thay đổi đang chờ áp dụng
                  </p>
                )}
              </div>
            </div>
          ) : (
            <div className="p-8 flex flex-col items-center gap-3">
              <span className="material-symbols-outlined text-5xl text-outline">auto_mode</span>
              {runningAutoSchedule && !previewResult ? (
                <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
                  <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                  Đang tạo preview...
                </div>
              ) : asMessage ? (
                <div className={`${asMessage.toLowerCase().includes('thành công') || asMessage.toLowerCase().includes('đã áp dụng') || asMessage.toLowerCase().includes('đã hủy') || asMessage.toLowerCase().includes('đã làm mới') ? 'bg-secondary-container border border-secondary/20 text-on-secondary-container' : 'bg-error-container border border-error/20 text-error'} rounded-lg px-4 py-2 text-label-sm flex items-center gap-2 max-w-sm text-center`}>
                  <span className="material-symbols-outlined text-[16px]">{asMessage.toLowerCase().includes('thành công') || asMessage.toLowerCase().includes('đã áp dụng') || asMessage.toLowerCase().includes('đã hủy') || asMessage.toLowerCase().includes('đã làm mới') ? 'check_circle' : 'error'}</span>
                  {asMessage}
                </div>
              ) : (
                <p className="text-on-surface-variant text-body-sm text-center">Chưa có preview. Hãy chạy Auto Schedule để tạo phương án.</p>
              )}
              {selectedPeriod?.status !== "DRAFT" && (
                <div className="bg-tertiary-container text-on-tertiary-container rounded-lg px-3 py-1.5 text-label-sm flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[14px]">info</span>
                  Chỉ kỳ lịch ở trạng thái <strong>DRAFT</strong> mới có thể xếp tự động
                </div>
              )}
              <div className="flex items-center gap-3">
                <div className="relative">
                  <select
                    className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest pl-3 pr-8 text-label-md text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer"
                    value={algorithmType}
                    onChange={(e) => {
                      const val = e.target.value as "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING";
                      void asActions.setAlgorithmType(val);
                    }}
                  >
                    <option value="GREEDY">GREEDY — Ưu tiên cân bằng tải</option>
                    <option value="ROUND_ROBIN">ROUND_ROBIN — Xen kẽ luân phiên</option>
                    <option value="BACKTRACKING">BACKTRACKING — Tìm kiếm tối ưu</option>
                  </select>
                  <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
                </div>
                <button
                  type="button"
                  onClick={() => void handlePreviewAutoSchedule()}
                  disabled={runningAutoSchedule || !selectedPeriodId || selectedPeriod?.status !== "DRAFT"}
                  className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-60"
                >
                  {runningAutoSchedule ? "Đang tạo preview…" : "Chạy Auto Schedule"}
                </button>
              </div>
            </div>
          )}
        </SectionCard>

        <SectionCard
          title="Top workload"
          description={`Ảnh chụp nhanh tải công việc của ${activeStaff.length} nhân sự đang hoạt động.`}
        >
          <div className="divide-y divide-outline-variant">
            {workloadSnapshot.length === 0 ? (
              <EmptyState className="py-10" icon="groups" title="Chưa đủ dữ liệu phân bổ" description="Hệ thống cần có lịch được gán cho loại lịch đang xem để tính workload." />
            ) : (
              workloadSnapshot.map((row) => (
                <div key={row.staffName} className="px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-label-md font-semibold text-on-surface">{row.staffName}</p>
                    <span className="text-label-sm text-on-surface-variant">{row.shifts} ca</span>
                  </div>
                  <div className="mt-2 h-2 rounded-full bg-surface-container-high">
                    <div className="h-2 rounded-full bg-primary" style={{ width: `${Math.min(100, row.shifts * 18)}%` }} />
                  </div>
                </div>
              ))
            )}
          </div>
        </SectionCard>

        {/* Quick Add Modal */}
        <Modal
          open={addModalDate !== null}
          onClose={() => setAddModalDate(null)}
          title="Thêm lịch nhanh"
          size="md"
        >
          {addModalDate && (
            <QuickAddForm
              date={addModalDate}
              periodId={selectedPeriodId}
              defaultShiftTypeId={selectedTab}
              staffList={activeStaff}
              onSuccess={() => {
                setAddModalDate(null);
                void wsActions.refreshWorkspace();
              }}
              onCancel={() => setAddModalDate(null)}
            />
          )}
        </Modal>

        {/* Shift Detail Modal — opened via ?scheduleId= from /duty-24/shift-detail/[id] redirect */}
        <Modal
          open={detailScheduleId !== null}
          onClose={() => {
            setDetailScheduleId(null);
            setDetailSchedule(null);
            router.replace("/monthly-schedule");
          }}
          title={detailSchedule ? `Chi tiết ca trực — ${detailSchedule.shiftType.name}` : "Chi tiết ca trực"}
          description={detailSchedule ? `${detailSchedule.staff.fullName} · ${new Date(detailSchedule.workDate).toLocaleDateString("vi-VN")}` : undefined}
          size="xl"
        >
          {detailLoading ? (
            <div className="flex h-48 items-center justify-center">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : detailSchedule ? (
            (() => {
              const vm = buildShiftDetailViewModel(detailSchedule);
              return (
                <div className="space-y-6">
                  <div className="flex items-center gap-3">
                    <span className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-semibold text-white ${vm.shiftColor}`}>
                      <span className="material-symbols-outlined text-[16px]">emergency</span>
                      {vm.shiftType}
                    </span>
                    {detailSchedule.hasConflict && (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-error-container px-3 py-1 text-xs font-semibold text-error border border-error/20">
                        <span className="material-symbols-outlined text-[14px]">warning</span>
                        Có xung đột
                      </span>
                    )}
                  </div>
                  <ShiftDetailInfo shift={vm} />
                  <ShiftDetailTable shift={vm} />
                </div>
              );
            })()
          ) : (
            <div className="flex h-32 items-center justify-center text-on-surface-variant">
              Không tìm thấy lịch trực.
            </div>
          )}
        </Modal>

        {/* Conflict Resolution Modal */}
        <ConflictResolutionModal
          open={resolvingConflict !== null}
          onClose={() => setResolvingConflict(null)}
          conflict={resolvingConflict}
          onRefresh={() => {
            void wsActions.refreshWorkspace();
            void wsActions.checkConflicts();
          }}
        />
      </section>
    </WorkflowShell>
  );
}
