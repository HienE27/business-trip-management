"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { ConflictInspector } from "@/components/schedule-summary/ConflictInspector";
import { MonthDateGrid } from "@/components/schedule-summary/MonthDateGrid";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { DutyTable } from "@/components/duty-24/DutyTable";
import type {
  ConflictCheckResponse,
  ConflictDetail,
  Schedule,
  SchedulePeriod,
  ShiftType,
  Staff,
} from "@/types/api";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";

const ruleCards = [
  {
    title: "Trực 24/24",
    detail: "Chọn ngày N, hệ thống hiểu ca từ 7h30 ngày N đến 7h30 ngày N+1.",
  },
  {
    title: "Nghỉ bù",
    detail: "Trực T2-T5 nghỉ bù ngày kế tiếp; trực T6/T7 đổi sang T3 tuần sau.",
  },
  {
    title: "Khóa ở",
    detail: "Ngày nghỉ bù bị khóa, không thể xếp thông tầm, dịch vụ hoặc chuyên gia.",
  },
];

const VIETNAMESE_DAY_NAMES: Record<number, string> = {
  0: "Chủ Nhật",
  1: "Thứ 2",
  2: "Thứ 3",
  3: "Thứ 4",
  4: "Thứ 5",
  5: "Thứ 6",
  6: "Thứ 7",
};

type DutyConflictState = {
  schedule: Schedule;
  detail: ConflictDetail;
};

type CalendarAnnotation = {
  date: string;
  label: string;
  tone?: "compLeave" | "warning" | "neutral";
  description?: string;
};

type DutyRow = {
  id: number;
  date: string;
  weekday: string;
  staff: string;
  specialty?: string;
  role?: string;
  compDay: string;
  status: string;
  statusTone: "success" | "warning" | "danger" | "neutral" | "info";
};

const WEEKDAY_PRESETS = [
  { label: "T2", value: 1 },
  { label: "T3", value: 2 },
  { label: "T4", value: 3 },
  { label: "T5", value: 4 },
  { label: "T6", value: 5 },
  { label: "T7", value: 6 },
  { label: "CN", value: 0 },
] as const;

const DUTY_24_PRESET_STORAGE_KEY = "medschedule.bulkPreset.duty24";

function toMonthDateKey(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getMonthDates(month: Date) {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const lastDate = new Date(year, monthIndex + 1, 0).getDate();

  return Array.from({ length: lastDate }, (_, index) => new Date(year, monthIndex, index + 1));
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const day = date.getDate().toString().padStart(2, "0");
  const month = (date.getMonth() + 1).toString().padStart(2, "0");
  return `${day}/${month}`;
}

function getDayOfWeek(dateStr: string): string {
  const date = new Date(dateStr);
  return VIETNAMESE_DAY_NAMES[date.getDay()] ?? "";
}

function getCompensationDisplay(schedule: Schedule): string {
  if (!schedule.compensationDate) {
    return "—";
  }

  return formatDate(schedule.compensationDate);
}

function determineStatus(
  schedule: Schedule,
  shiftTypes: ShiftType[],
  conflictMap: Map<number, ConflictDetail>,
): { status: string; tone: "success" | "warning" | "danger" | "neutral" | "info" } {
  if (conflictMap.has(schedule.id)) {
    return { status: "Chặn lưu", tone: "danger" };
  }

  const shiftType = shiftTypes.find((item) => item.id === schedule.shiftType.id);
  if (shiftType && shiftType.fatigueScore >= 7) {
    return { status: "Cảnh báo", tone: "warning" };
  }

  return { status: "Hợp lệ", tone: "success" };
}

export default function Duty24Page() {
  const router = useRouter();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [replacementCandidates, setReplacementCandidates] = useState<Staff[]>([]);
  const [selectedConflict, setSelectedConflict] = useState<DutyConflictState | null>(null);
  const [loadingReplacements, setLoadingReplacements] = useState(false);
  const [loading, setLoading] = useState(true);
  const [printing, setPrinting] = useState(false);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Bulk assignment state
  const [bulkMode, setBulkMode] = useState(false);
  const [bulkStaffId, setBulkStaffId] = useState<number | "">("");
  const [bulkDates, setBulkDates] = useState<Set<string>>(new Set());
  const [bulkNotes, setBulkNotes] = useState("");
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [bulkMonth, setBulkMonth] = useState(new Date());
  const [selectedWeekdays, setSelectedWeekdays] = useState<Set<number>>(new Set([1, 2, 3, 4, 5]));
  const [presetName, setPresetName] = useState("Lịch hành chính");

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const savedPreset = window.localStorage.getItem(DUTY_24_PRESET_STORAGE_KEY);
    if (!savedPreset) {
      return;
    }

    try {
      const parsed = JSON.parse(savedPreset) as { name?: string; weekdays?: number[] };
      if (parsed.name) {
        setPresetName(parsed.name);
      }
      if (Array.isArray(parsed.weekdays)) {
        setSelectedWeekdays(new Set(parsed.weekdays.filter((value) => Number.isInteger(value))));
      }
    } catch {
      window.localStorage.removeItem(DUTY_24_PRESET_STORAGE_KEY);
    }
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    window.localStorage.setItem(
      DUTY_24_PRESET_STORAGE_KEY,
      JSON.stringify({ name: presetName, weekdays: [...selectedWeekdays].sort((a, b) => a - b) }),
    );
  }, [presetName, selectedWeekdays]);

  const refreshPeriods = useCallback(async () => {
    const data = await api.get<SchedulePeriod[]>("/periods");
    const nextPeriods = data ?? [];
    setPeriods(nextPeriods);
    return nextPeriods;
  }, []);

  const refreshDutyData = useCallback(async (periodId: number) => {
    const [scheduleData, conflictResult] = await Promise.all([
      api.get<Schedule[]>(`/schedules/period/${periodId}`),
      api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`),
    ]);

    setSchedules(scheduleData ?? []);
    setConflictData(conflictResult);
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    if (!selectedPeriodId) {
      setMessage("Chưa chọn kỳ lịch để kiểm tra xung đột.");
      return null;
    }

    try {
      setCheckingConflicts(true);
      setMessage(null);
      const conflictRes = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
      setConflictData(conflictRes);
      setMessage(
        conflictRes?.hasConflicts
          ? `Phát hiện ${conflictRes.totalConflicts} xung đột cần xử lý trước khi công bố.`
          : "Không phát hiện xung đột. Kỳ lịch sẵn sàng để công bố.",
      );
      return conflictRes;
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể kiểm tra xung đột kỳ lịch."));
      return null;
    } finally {
      setCheckingConflicts(false);
    }
  }, [selectedPeriodId]);

  const handlePublishPeriod = useCallback(async () => {
    if (!selectedPeriodId) {
      setMessage("Chưa chọn kỳ lịch để công bố.");
      return;
    }

    const currentPeriod = periods.find((period) => period.id === selectedPeriodId);
    if (currentPeriod?.status !== "DRAFT") {
      setMessage("Chỉ có thể công bố kỳ lịch đang ở trạng thái DRAFT.");
      return;
    }

    const confirmed = window.confirm("Công bố kỳ lịch này? Sau khi công bố, bạn sẽ không thể chỉnh sửa lịch trong kỳ.");
    if (!confirmed) {
      return;
    }

    try {
      setPublishing(true);
      setMessage(null);
      const latestConflict = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
      setConflictData(latestConflict);

      if (latestConflict?.hasConflicts) {
        setMessage(`Kỳ lịch còn ${latestConflict.totalConflicts} xung đột. Vui lòng xử lý trước khi công bố.`);
        return;
      }

      await api.post(`/periods/${selectedPeriodId}/publish`, {});
      await refreshPeriods();
      await refreshDutyData(selectedPeriodId);
      setMessage("Đã lưu và công bố kỳ lịch thành công.");
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể công bố kỳ lịch trực 24/24."));
    } finally {
      setPublishing(false);
    }
  }, [periods, refreshDutyData, refreshPeriods, selectedPeriodId]);

  useEffect(() => {
    let active = true;

    const loadPeriods = async () => {
      try {
        setMessage(null);
        const data = await api.get<SchedulePeriod[]>("/periods");
        if (!active) {
          return;
        }

        const nextPeriods = data ?? [];
        setPeriods(nextPeriods);
        const activePeriod =
          nextPeriods.find((period) => period.status === "DRAFT") ??
          nextPeriods.find((period) => period.status === "PUBLISHED") ??
          nextPeriods[0];
        if (activePeriod) {
          setSelectedPeriodId(activePeriod.id);
          setBulkMonth(new Date(activePeriod.startDate));
        }
      } catch (err) {
        if (!active) {
          return;
        }

        setPeriods([]);
        setMessage(getErrorMessage(err, "Không thể tải danh sách kỳ lịch."));
      }
    };

    void loadPeriods();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    const loadShiftTypes = async () => {
      try {
        const data = await api.get<ShiftType[]>("/shift-types");
        if (!active) {
          return;
        }

        setShiftTypes(data ?? []);
      } catch (err) {
        if (!active) {
          return;
        }

        setShiftTypes([]);
        setMessage(getErrorMessage(err, "Không thể tải danh sách loại lịch."));
      }
    };

    void loadShiftTypes();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedPeriodId) {
      return;
    }

    let cancelled = false;
    setLoading(true);

    const loadPeriodData = async () => {
      try {
        await refreshDutyData(selectedPeriodId);
      } catch (err) {
        if (cancelled) {
          return;
        }

        setSchedules([]);
        setConflictData(null);
        setMessage(getErrorMessage(err, "Không thể tải dữ liệu lịch trực 24/24."));
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void loadPeriodData();

    return () => {
      cancelled = true;
    };
  }, [refreshDutyData, selectedPeriodId]);

  const selectedPeriod = useMemo(
    () => periods.find((period) => period.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  const isEditablePeriod = selectedPeriod?.status === "DRAFT";
  const statusBannerMessage = conflictData?.hasConflicts
    ? `${conflictData.totalConflicts} xung đột cần xử lý trước khi công bố.`
    : "Không phát hiện xung đột. Kỳ lịch sẵn sàng để công bố.";
  const canPublishPeriod =
    Boolean(selectedPeriodId) &&
    isEditablePeriod &&
    !loading &&
    !checkingConflicts &&
    !publishing &&
    !loadingReplacements &&
    !(conflictData?.hasConflicts ?? false);

  const conflictMap = useMemo(() => {
    const entries = (conflictData?.conflicts ?? []).map((detail) => [detail.scheduleId, detail] as const);
    return new Map<number, ConflictDetail>(entries);
  }, [conflictData]);

  const l01Schedules = useMemo(
    () => schedules.filter((schedule) => schedule.shiftType.id === "L01"),
    [schedules],
  );

  const conflictSchedules = useMemo(
    () => l01Schedules.filter((schedule) => conflictMap.has(schedule.id)),
    [l01Schedules, conflictMap],
  );

  const dutyRows = useMemo(() => {
    const sorted = [...l01Schedules].sort(
      (left, right) => new Date(left.workDate).getTime() - new Date(right.workDate).getTime(),
    );

    return sorted.map((schedule) => {
      const { status, tone } = determineStatus(schedule, shiftTypes, conflictMap);
      return {
        id: schedule.id,
        date: formatDate(schedule.workDate),
        weekday: getDayOfWeek(schedule.workDate),
        staff: schedule.staff.fullName,
        specialty: schedule.staff.specialtyName ?? undefined,
        role: schedule.staff.roles?.join(" / ") ?? undefined,
        compDay: getCompensationDisplay(schedule),
        status,
        statusTone: tone,
      } satisfies DutyRow;
    });
  }, [l01Schedules, shiftTypes, conflictMap]);

  const compensationDays = useMemo(() => {
    const days = new Set<string>();
    l01Schedules.forEach((schedule) => {
      if (schedule.compensationDate) {
        days.add(formatDate(schedule.compensationDate));
      }
    });
    return days;
  }, [l01Schedules]);
  const monthDates = useMemo(() => getMonthDates(bulkMonth), [bulkMonth]);

  const compensationAnnotations = useMemo<CalendarAnnotation[]>(() => {
    const annotations = new Map<string, CalendarAnnotation>();

    l01Schedules.forEach((schedule) => {
      if (!schedule.compensationDate) {
        return;
      }

      const dateKey = schedule.compensationDate.split("T")[0] ?? schedule.compensationDate;
      if (!annotations.has(dateKey)) {
        annotations.set(dateKey, {
          date: dateKey,
          label: "Nghỉ bù",
          tone: "compLeave",
          description: "Ngày nghỉ bù được khóa để không xếp thêm lịch khác.",
        });
      }
    });

    conflictSchedules.forEach((schedule) => {
      const dateKey = schedule.workDate.split("T")[0] ?? schedule.workDate;
      if (!annotations.has(dateKey)) {
        annotations.set(dateKey, {
          date: dateKey,
          label: "Xung đột",
          tone: "warning",
          description: "Ngày này đang có xung đột lịch cần xử lý trước khi công bố.",
        });
      }
    });

    return Array.from(annotations.values());
  }, [l01Schedules, conflictSchedules]);

  const handleToggleBulkDate = useCallback((date: string) => {
    setBulkDates((prev) => {
      const next = new Set(prev);
      if (next.has(date)) {
        next.delete(date);
      } else {
        next.add(date);
      }
      return next;
    });
  }, []);

  const handleSelectAllMonthDates = useCallback(() => {
    setBulkDates(new Set(monthDates.map((date) => toMonthDateKey(date))));
  }, [monthDates]);

  const handleSelectWorkingMonthDates = useCallback(() => {
    const nextDates = monthDates
      .filter((date) => {
        const weekday = date.getDay();
        return weekday !== 0 && weekday !== 6;
      })
      .map((date) => toMonthDateKey(date));
    setBulkDates(new Set(nextDates));
  }, [monthDates]);

  const handleSelectByWeekdays = useCallback(() => {
    const nextDates = monthDates
      .filter((date) => selectedWeekdays.has(date.getDay()))
      .map((date) => toMonthDateKey(date));
    setBulkDates(new Set(nextDates));
  }, [monthDates, selectedWeekdays]);

  const handleToggleWeekday = useCallback((weekday: number) => {
    setSelectedWeekdays((prev) => {
      const next = new Set(prev);
      if (next.has(weekday)) {
        next.delete(weekday);
      } else {
        next.add(weekday);
      }
      return next;
    });
  }, []);

  const handleClearBulkDates = useCallback(() => {
    setBulkDates(new Set());
  }, []);

  async function handleBulkAssign() {
    if (!selectedPeriodId || !bulkStaffId || bulkDates.size === 0) {
      setMessage("Vui lòng chọn nhân sự và ít nhất một ngày để gán hàng loạt.");
      return;
    }

    if (!isEditablePeriod) {
      setMessage("Chỉ có thể gán lịch khi kỳ lịch ở trạng thái DRAFT.");
      return;
    }

    setBulkSubmitting(true);
    const results: { date: string; ok: boolean }[] = [];

    try {
      for (const date of [...bulkDates].sort()) {
        try {
          await api.post("/schedules", {
            periodId: selectedPeriodId,
            staffId: bulkStaffId,
            workDate: date,
            shiftTypeId: "L01",
            notes: bulkNotes,
          });
          results.push({ date, ok: true });
        } catch {
          results.push({ date, ok: false });
        }
      }

      const okCount = results.filter((r) => r.ok).length;
      const failCount = results.length - okCount;
      setMessage(
        failCount === 0
          ? `Đã gán ${okCount} lịch trực 24/24 hàng loạt thành công.`
          : `Gán được ${okCount}/${results.length} lịch. ${failCount} lịch thất bại do trùng hoặc xung đột.`
      );

      setBulkStaffId("");
      setBulkDates(new Set());
      setBulkNotes("");
      setBulkMode(false);
      await refreshDutyData(selectedPeriodId);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể gán lịch trực hàng loạt."));
    } finally {
      setBulkSubmitting(false);
    }
  }

  async function handleSelectConflict(schedule: Schedule, detail: ConflictDetail) {
    setSelectedConflict({ schedule, detail });
    setReplacementCandidates([]);

    if (!selectedPeriodId) {
      return;
    }

    try {
      setLoadingReplacements(true);
      const replacements = await api.findReplacements(
        selectedPeriodId,
        schedule.workDate.split("T")[0] ?? schedule.workDate,
        schedule.shiftType.id,
        schedule.staff.id,
        5,
      );
      setReplacementCandidates(replacements ?? []);
    } catch (err) {
      setReplacementCandidates([]);
      setMessage(getErrorMessage(err, "Không thể lấy danh sách nhân sự thay thế."));
    } finally {
      setLoadingReplacements(false);
    }
  }

  function handleEditLatestDuty() {
    if (!isEditablePeriod) {
      setMessage("Chỉ có thể chỉnh sửa khi kỳ lịch đang ở trạng thái DRAFT.");
      return;
    }

    if (dutyRows.length === 0) {
      setMessage("Chưa có lịch trực để chỉnh sửa.");
      return;
    }

    const latestDuty = dutyRows[dutyRows.length - 1];
    router.push(`/duty-24/shift-detail/${latestDuty.id}`);
  }

  async function handlePrint() {
    try {
      setPrinting(true);
      setMessage(null);
      window.print();
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể mở chế độ in chi tiết lịch trực."));
    } finally {
      setPrinting(false);
    }
  }

  return (
    <DashboardShell
      activeCode="M02"
      title="Lịch trực 24/24"
      description="Xếp lịch trực cả tháng, tự tính nghỉ bù và kiểm tra xung đột hàng loạt."
    >
      {message && (
        <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-on-surface">
          {message}
        </div>
      )}

      <div className="rounded-xl border border-primary/30 bg-primary/5 px-5 py-3 shadow-sm backdrop-blur-sm">
        <div className="flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[20px]">info</span>
            <span className="text-label-md text-on-surface">{statusBannerMessage}</span>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleCheckConflicts}
              disabled={checkingConflicts || loading || publishing || !selectedPeriodId}
              className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-label-md text-on-surface shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] hover:bg-surface-container-low disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[16px]">sync</span>
              {checkingConflicts ? "Đang kiểm tra..." : "Kiểm tra xung đột"}
            </button>
            <button
              type="button"
              onClick={handlePublishPeriod}
              disabled={!canPublishPeriod}
              className="flex items-center gap-2 rounded-lg bg-primary px-5 py-2 text-label-md text-on-primary shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[16px]">publish</span>
              {publishing ? "Đang công bố..." : "Lưu & Công bố"}
            </button>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 text-label-md text-on-surface-variant">
        <Link className="flex items-center gap-1 transition-colors hover:text-primary" href="/duty-24">
          <span className="material-symbols-outlined text-[16px]">arrow_back</span>
          Quản lý lịch trực
        </Link>
        <span className="material-symbols-outlined text-[16px] text-outline">chevron_right</span>
        <span className="font-medium text-on-surface">Chi tiết lịch trực</span>
      </div>

      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-headline-md text-on-surface">Lịch trực 24/24</h1>
          <p className="mt-1 text-label-md text-on-surface-variant">Mã ca: {selectedPeriod?.periodName ?? "—"}</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleEditLatestDuty}
            disabled={loading || dutyRows.length === 0 || !isEditablePeriod}
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-on-surface shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] transition-colors hover:bg-surface-container-low disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[16px]">edit</span>
            Chỉnh sửa
          </button>
          <button
            type="button"
            onClick={handlePrint}
            disabled={printing || loading}
            className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] transition-colors hover:bg-primary/90 disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[16px]">print</span>
            {printing ? "Đang mở in..." : "In chi tiết lịch trực"}
          </button>
        </div>
      </div>

      <section className="grid gap-4 md:grid-cols-3">
        {ruleCards.map((rule) => (
          <article
            key={rule.title}
            className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] transition-colors hover:bg-surface-container-low"
          >
            <h2 className="text-title-lg text-on-surface">{rule.title}</h2>
            <p className="mt-2 text-label-md leading-relaxed text-on-surface-variant">{rule.detail}</p>
          </article>
        ))}
      </section>

      <div className="flex flex-wrap items-center gap-4">
        <label className="whitespace-nowrap text-label-md text-on-surface-variant">Chọn kỳ lịch:</label>
        <div className="relative">
          <select
            className="h-10 w-64 appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest pl-3 pr-10 text-label-md text-on-surface cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={selectedPeriodId ?? ""}
            onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
          >
            {periods.length === 0 && <option value="">— Không có kỳ lịch —</option>}
            {periods.map((period) => (
              <option key={period.id} value={period.id}>
                {period.periodName} ({formatDate(period.startDate)} – {formatDate(period.endDate)})
              </option>
            ))}
          </select>
          <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline">
            expand_more
          </span>
        </div>
        {selectedPeriod ? (
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-label-sm font-medium ${
              selectedPeriod.status === "PUBLISHED"
                ? "bg-secondary-fixed text-on-secondary-fixed"
                : selectedPeriod.status === "ARCHIVED"
                  ? "bg-surface-container-high text-outline"
                  : "bg-tertiary-fixed text-on-tertiary-fixed"
            }`}
          >
            {selectedPeriod.status}
          </span>
        ) : null}
        <span className="inline-flex items-center gap-1.5 rounded-full border border-outline-variant bg-surface px-3 py-1 text-label-sm font-medium text-on-surface">
          {conflictSchedules.length} xung đột
        </span>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : (
        <div className="grid gap-4 pb-20 lg:gap-6 xl:grid-cols-[1fr_360px]">
          <div className="space-y-4 lg:space-y-6">
            <ScheduleCalendarWidget
              schedules={l01Schedules}
              calendarAnnotations={compensationAnnotations}
              onRefresh={() => selectedPeriodId ? refreshDutyData(selectedPeriodId) : Promise.resolve()}
            />

            <SectionCard
              title="Bảng trực đã gán"
              description="Mỗi dòng là một ngày trực, ngày nghỉ bù hệ thống tự sinh. Nhấn vào một dòng để xem chi tiết."
            >
              <DutyTable rows={dutyRows} onRowClick={(row) => router.push(`/duty-24/shift-detail/${row.id}`)} />
            </SectionCard>
          </div>

          <aside className="space-y-4 lg:space-y-6">
            <SectionCard title="Quy tắc nghỉ bù" description="Áp dụng ngay khi gán ngày trực">
              <div className="space-y-3 px-5 py-4 text-label-md leading-relaxed text-on-surface-variant">
                <p>Trực Thứ 2 đến Thứ 5: nghỉ bù ngày kế tiếp và khóa trên bảng tháng.</p>
                <p>Trực Thứ 6 hoặc Thứ 7: chuyển sang tuần sau, bỏ qua Thứ 2 và Thứ 6.</p>
                <p>Trực Chủ Nhật: nghỉ bù Thứ 2 ngày hôm sau.</p>
              </div>
            </SectionCard>

            {isEditablePeriod && (
              <SectionCard title="Gán hàng loạt lịch trực" description="M02-F01 · chọn 1 nhân sự và nhiều ngày trực trong tháng">
                <div className="space-y-4 px-5 py-4">
                  <div className="flex items-center justify-between gap-3 rounded-lg border border-primary/20 bg-primary/5 p-3">
                    <div>
                      <p className="text-label-md font-semibold text-on-surface">Chế độ gán hàng loạt</p>
                      <p className="text-[12px] text-on-surface-variant">Tạo nhiều ca `L01` và hệ thống tự sinh nghỉ bù</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => setBulkMode((prev) => !prev)}
                      className={`flex h-8 w-8 items-center justify-center rounded-lg transition-colors ${
                        bulkMode ? "bg-primary text-on-primary" : "border border-outline-variant bg-surface text-on-surface-variant hover:bg-surface-container-low"
                      }`}
                      aria-label="Bật/tắt chế độ gán hàng loạt"
                    >
                      <span className="material-symbols-outlined text-[18px]">{bulkMode ? "close" : "add"}</span>
                    </button>
                  </div>

                  {bulkMode && (
                    <>
                      <div>
                        <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-duty-staff">
                          Nhân sự
                        </label>
                        <div className="relative">
                          <select
                            id="bulk-duty-staff"
                            className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                            value={bulkStaffId}
                            onChange={(e) => setBulkStaffId(e.target.value ? Number(e.target.value) : "")}
                          >
                            <option value="">Chọn nhân sự...</option>
                            {Array.from(new Map(l01Schedules.map((schedule) => [schedule.staff.id, schedule.staff])).values()).map((staff) => (
                              <option key={staff.id} value={staff.id}>{staff.fullName}</option>
                            ))}
                          </select>
                          <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline">expand_more</span>
                        </div>
                      </div>

                      <MonthDateGrid
                        month={bulkMonth}
                        onMonthChange={setBulkMonth}
                        selectedDates={bulkDates}
                        onToggleDate={handleToggleBulkDate}
                        highlightedDates={new Set(l01Schedules.map((schedule) => schedule.workDate.split("T")[0] ?? schedule.workDate))}
                        disabled={!isEditablePeriod}
                        helperText="Nhấn vào từng ngày để chọn hoặc bỏ chọn. Khi tạo L01, hệ thống sẽ tự sinh ngày nghỉ bù theo quy tắc." 
                      />

                      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                        <button
                          type="button"
                          onClick={handleSelectWorkingMonthDates}
                          disabled={!isEditablePeriod}
                          className="inline-flex h-10 items-center justify-center rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60"
                        >
                          Chọn ngày làm việc
                        </button>
                        <button
                          type="button"
                          onClick={handleSelectAllMonthDates}
                          disabled={!isEditablePeriod}
                          className="inline-flex h-10 items-center justify-center rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60"
                        >
                          Chọn tất cả
                        </button>
                        <button
                          type="button"
                          onClick={handleClearBulkDates}
                          disabled={bulkDates.size === 0}
                          className="inline-flex h-10 items-center justify-center rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60"
                        >
                          Bỏ chọn toàn bộ
                        </button>
                      </div>

                      <div className="space-y-2">
                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
                          <div>
                            <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-duty-preset-name">
                              Tên preset
                            </label>
                            <input
                              id="bulk-duty-preset-name"
                              value={presetName}
                              onChange={(e) => setPresetName(e.target.value)}
                              className="h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                              placeholder="Ví dụ: Lịch hành chính"
                            />
                          </div>
                          <div className="rounded-full bg-secondary-container px-3 py-2 text-[12px] font-semibold text-on-secondary-container">
                            Tự lưu trên máy này
                          </div>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Chọn theo thứ</p>
                          <button
                            type="button"
                            onClick={handleSelectByWeekdays}
                            disabled={selectedWeekdays.size === 0 || !isEditablePeriod}
                            className="inline-flex h-8 items-center justify-center rounded-lg border border-outline-variant bg-surface px-3 text-[12px] font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60"
                          >
                            Áp dụng theo thứ
                          </button>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {WEEKDAY_PRESETS.map((preset) => {
                            const active = selectedWeekdays.has(preset.value);
                            return (
                              <button
                                key={preset.value}
                                type="button"
                                onClick={() => handleToggleWeekday(preset.value)}
                                className={`inline-flex h-9 min-w-10 items-center justify-center rounded-full border px-3 text-[12px] font-semibold transition-colors ${
                                  active
                                    ? "border-primary bg-primary text-on-primary"
                                    : "border-outline-variant bg-surface text-on-surface-variant hover:bg-surface-container-low"
                                }`}
                              >
                                {preset.label}
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      <div>
                        <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-duty-notes">
                          Ghi chú
                        </label>
                        <textarea
                          id="bulk-duty-notes"
                          rows={2}
                          value={bulkNotes}
                          onChange={(e) => setBulkNotes(e.target.value)}
                          className="w-full resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
                          placeholder="Ghi chú chung cho các ca trực..."
                        />
                      </div>

                      <button
                        type="button"
                        onClick={handleBulkAssign}
                        disabled={bulkSubmitting || !bulkStaffId || bulkDates.size === 0}
                        className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-opacity hover:opacity-90 disabled:opacity-60"
                      >
                        {bulkSubmitting ? (
                          <>
                            <div className="mr-2 size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                            Đang gán {bulkDates.size} lịch...
                          </>
                        ) : (
                          <>
                            <span className="material-symbols-outlined mr-2 text-[18px]">playlist_add</span>
                            Gán {bulkDates.size} lịch trực 24/24
                          </>
                        )}
                      </button>
                    </>
                  )}
                </div>
              </SectionCard>
            )}

            {compensationDays.size > 0 ? (
              <SectionCard title={`${compensationDays.size} ngày nghỉ bù`} description="Các ngày nghỉ bù được tính tự động">
                <div className="flex flex-wrap gap-2 px-5 py-4">
                  {[...compensationDays].map((day) => (
                    <span
                      key={day}
                      className="inline-flex items-center gap-1.5 rounded-full bg-surface-container-high px-3 py-1 text-label-sm text-on-surface-variant"
                    >
                      <span className="material-symbols-outlined text-[14px]">event</span>
                      {day}
                    </span>
                  ))}
                </div>
              </SectionCard>
            ) : null}

            <ConflictInspector
              title={`${conflictSchedules.length} xung đột cần xử lý`}
              description="Nhấn vào từng dòng để xem lý do và nhân sự thay thế"
              conflicts={conflictSchedules
                .map((schedule) => conflictMap.get(schedule.id))
                .filter((detail): detail is ConflictDetail => Boolean(detail))}
              emptyLabel="Không có xung đột ở lịch trực 24/24."
              selectedConflict={selectedConflict?.detail ?? null}
              onSelect={(detail) => {
                const schedule = conflictSchedules.find((item) => item.id === detail.scheduleId);
                if (!schedule) {
                  return;
                }
                void handleSelectConflict(schedule, detail);
              }}
              onClose={() => {
                setSelectedConflict(null);
                setReplacementCandidates([]);
              }}
            />

            {selectedPeriod ? (
              <SectionCard title="Tổng quan kỳ lịch">
                <div className="space-y-3 px-5 py-4">
                  <div className="flex items-center justify-between">
                    <span className="text-label-md text-on-surface-variant">Tổng ca trực 24/24</span>
                    <span className="text-label-md font-semibold text-on-surface">{l01Schedules.length}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-label-md text-on-surface-variant">Nhân sự tham gia</span>
                    <span className="text-label-md font-semibold text-on-surface">{new Set(l01Schedules.map((schedule) => schedule.staff.id)).size}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-label-md text-on-surface-variant">Ngày nghỉ bù</span>
                    <span className="text-label-md font-semibold text-on-surface">{compensationDays.size}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-label-md text-on-surface-variant">Có xung đột</span>
                    <span className={`text-label-md font-semibold ${conflictData?.totalConflicts ? "text-error" : "text-secondary"}`}>
                      {conflictData?.totalConflicts ?? 0}
                    </span>
                  </div>
                </div>
              </SectionCard>
            ) : null}
          </aside>
        </div>
      )}

      <Modal
        open={!!selectedConflict}
        onClose={() => {
          setSelectedConflict(null);
          setReplacementCandidates([]);
        }}
        title="Chi tiết xung đột lịch trực"
        description={
          selectedConflict
            ? `${selectedConflict.detail.staffName} — ${new Date(selectedConflict.detail.workDate).toLocaleDateString("vi-VN")}`
            : ""
        }
        size="md"
      >
        {selectedConflict ? (
          <div className="space-y-4">
            <div className="rounded-lg border border-red-200 bg-red-50 p-4">
              <p className="text-label-md font-semibold text-error">Lý do xung đột</p>
              <ul className="mt-2 space-y-2 text-label-sm text-on-surface-variant">
                {selectedConflict.detail.conflictReasons.map((reason) => (
                  <li key={reason} className="flex items-start gap-2">
                    <span className="material-symbols-outlined mt-0.5 text-[16px] text-error">error</span>
                    <span>{reason}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
              <p className="text-label-md font-semibold text-on-surface">Đề xuất nhân sự thay thế</p>
              {loadingReplacements ? (
                <div className="flex items-center justify-center py-6">
                  <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                </div>
              ) : replacementCandidates.length === 0 ? (
                <p className="mt-3 text-label-sm text-on-surface-variant">Chưa tìm được nhân sự thay thế hợp lệ cho ca này.</p>
              ) : (
                <div className="mt-3 space-y-3">
                  {replacementCandidates.map((candidate) => (
                    <div key={candidate.id} className="rounded-lg border border-outline-variant bg-surface-container-low p-3">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <p className="text-label-md font-semibold text-on-surface">{candidate.fullName}</p>
                          <p className="text-[12px] text-on-surface-variant">{candidate.specialty?.name ?? "Không có chuyên khoa"}</p>
                        </div>
                        <span className="rounded-full bg-primary/10 px-2.5 py-1 text-[12px] font-medium text-primary">
                          Tối đa {candidate.maxShiftsPerMonth} ca/tháng
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <ModalFooter>
              <button
                type="button"
                onClick={() => {
                  setSelectedConflict(null);
                  setReplacementCandidates([]);
                }}
                className="rounded-lg border border-outline-variant px-4 py-2 text-label-md text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Đóng
              </button>
            </ModalFooter>
          </div>
        ) : null}
      </Modal>
    </DashboardShell>
  );
}
