"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { ConflictInspector } from "@/components/schedule-summary/ConflictInspector";
import { MonthDateGrid } from "@/components/schedule-summary/MonthDateGrid";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictCheckResponse, ConflictDetail, Schedule, SchedulePeriod, Staff } from "@/types/api";

type ConflictState = {
  schedule: Schedule;
  detail: ConflictDetail;
};

type CalendarAnnotation = {
  date: string;
  label: string;
  tone?: "compLeave" | "warning" | "neutral";
  description?: string;
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

const ALL_DAY_PRESET_STORAGE_KEY = "medschedule.bulkPreset.allDay";

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

export default function AllDayPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [staffList, setStaffList] = useState<Staff[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [selectedStaffId, setSelectedStaffId] = useState<number | "">("");
  const [selectedDate, setSelectedDate] = useState("");
  const [notes, setNotes] = useState("");
  const [selectedConflict, setSelectedConflict] = useState<ConflictState | null>(null);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

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

    const savedPreset = window.localStorage.getItem(ALL_DAY_PRESET_STORAGE_KEY);
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
      window.localStorage.removeItem(ALL_DAY_PRESET_STORAGE_KEY);
    }
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    window.localStorage.setItem(
      ALL_DAY_PRESET_STORAGE_KEY,
      JSON.stringify({ name: presetName, weekdays: [...selectedWeekdays].sort((a, b) => a - b) }),
    );
  }, [presetName, selectedWeekdays]);

  const refreshPeriods = useCallback(async () => {
    const periodsData = await api.get<SchedulePeriod[]>("/periods");
    const nextPeriods = periodsData ?? [];
    setPeriods(nextPeriods);
    return nextPeriods;
  }, []);

  const refreshPeriodData = useCallback(async (periodId: number) => {
    const [scheduleData, conflictResult] = await Promise.all([
      api.get<Schedule[]>(`/schedules/period/${periodId}`),
      api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`),
    ]);

    setSchedules(scheduleData ?? []);
    setConflictData(conflictResult);
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    if (!selectedPeriodId) {
      setMessage({ type: "error", text: "Chưa chọn kỳ lịch để kiểm tra xung đột." });
      return null;
    }

    try {
      setCheckingConflicts(true);
      setMessage(null);
      const conflictRes = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
      setConflictData(conflictRes);
      setMessage({
        type: conflictRes?.hasConflicts ? "error" : "success",
        text: conflictRes?.hasConflicts
          ? `Phát hiện ${conflictRes.totalConflicts} xung đột cần xử lý trước khi công bố.`
          : "Không phát hiện xung đột. Kỳ lịch sẵn sàng để công bố.",
      });
      return conflictRes;
    } catch (err) {
      setMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể kiểm tra xung đột kỳ lịch."),
      });
      return null;
    } finally {
      setCheckingConflicts(false);
    }
  }, [selectedPeriodId]);

  const handlePublishPeriod = useCallback(async () => {
    if (!selectedPeriodId) {
      setMessage({ type: "error", text: "Chưa chọn kỳ lịch để công bố." });
      return;
    }

    const currentPeriod = periods.find((period) => period.id === selectedPeriodId);
    if (currentPeriod?.status !== "DRAFT") {
      setMessage({ type: "error", text: "Chỉ có thể công bố kỳ lịch đang ở trạng thái DRAFT." });
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
        setMessage({
          type: "error",
          text: `Kỳ lịch còn ${latestConflict.totalConflicts} xung đột. Vui lòng xử lý trước khi công bố.`,
        });
        return;
      }

      await api.post(`/periods/${selectedPeriodId}/publish`, {});
      await refreshPeriods();
      await refreshPeriodData(selectedPeriodId);
      setMessage({ type: "success", text: "Đã lưu và công bố kỳ lịch thành công." });
    } catch (err) {
      setMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể công bố kỳ lịch thông tầm."),
      });
    } finally {
      setPublishing(false);
    }
  }, [periods, refreshPeriodData, refreshPeriods, selectedPeriodId]);

  useEffect(() => {
    let active = true;

    const loadSetup = async () => {
      try {
        setLoading(true);
        setMessage(null);
        const [periodsData, staffData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active"),
        ]);

        if (!active) {
          return;
        }

        setPeriods(periodsData ?? []);
        setStaffList(staffData ?? []);
        const nextPeriods = periodsData ?? [];
        const activePeriod =
          nextPeriods.find((period) => period.status === "DRAFT") ??
          nextPeriods.find((period) => period.status === "PUBLISHED") ??
          nextPeriods[0];

        if (activePeriod) {
          setSelectedPeriodId(activePeriod.id);
          setBulkMonth(new Date(activePeriod.startDate));
        } else {
          setLoading(false);
        }
      } catch (err) {
        if (!active) {
          return;
        }

        setPeriods([]);
        setStaffList([]);
        setLoading(false);
        setMessage({
          type: "error",
          text: getErrorMessage(err, "Không thể tải dữ liệu ban đầu."),
        });
      }
    };

    void loadSetup();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedDate) return;
    const isCompensation = schedules.some((s) => {
      if (!s.compensationDate) return false;
      const dateKey = s.compensationDate.split("T")[0] ?? s.compensationDate;
      return dateKey === selectedDate;
    });
    if (isCompensation) {
      setSelectedDate("");
    }
  }, [selectedDate, schedules]);

  useEffect(() => {
    if (!selectedPeriodId) return;

    let active = true;

    const loadPeriodData = async () => {
      try {
        setLoading(true);
        await refreshPeriodData(selectedPeriodId);
      } catch (err) {
        if (!active) {
          return;
        }

        setSchedules([]);
        setConflictData(null);
        setMessage({
          type: "error",
          text: getErrorMessage(err, "Không thể tải dữ liệu lịch thông tầm."),
        });
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadPeriodData();

    return () => {
      active = false;
    };
  }, [refreshPeriodData, selectedPeriodId]);

  const l02Schedules = useMemo(
    () => schedules.filter((schedule) => schedule.shiftType.id === "L02"),
    [schedules],
  );

  const conflictMap = useMemo(() => {
    const entries = (conflictData?.conflicts ?? []).map((detail) => [detail.scheduleId, detail] as const);
    return new Map<number, ConflictDetail>(entries);
  }, [conflictData]);

  const l02ConflictSchedules = useMemo(
    () => l02Schedules.filter((schedule) => conflictMap.has(schedule.id)),
    [l02Schedules, conflictMap],
  );

  const selectedPeriod = useMemo(
    () => periods.find((period) => period.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  const isEditablePeriod = selectedPeriod?.status === "DRAFT";
  const canPublishPeriod =
    Boolean(selectedPeriodId) &&
    isEditablePeriod &&
    !loading &&
    !submitting &&
    !checkingConflicts &&
    !publishing &&
    !conflictData?.hasConflicts;

  const compensationAnnotations = useMemo<CalendarAnnotation[]>(() => {
    const annotations = new Map<string, CalendarAnnotation>();

    schedules.forEach((schedule) => {
      if (!schedule.compensationDate) {
        return;
      }

      const dateKey = schedule.compensationDate.split("T")[0] ?? schedule.compensationDate;
      if (!annotations.has(dateKey)) {
        annotations.set(dateKey, {
          date: dateKey,
          label: "Nghỉ bù",
          tone: "compLeave",
          description: "Ngày nghỉ bù từ lịch trực 24/24, không được gán lịch thông tầm.",
        });
      }
    });

    return Array.from(annotations.values());
  }, [schedules]);

  const compensationDateSet = useMemo(
    () => new Set(compensationAnnotations.map((annotation) => annotation.date)),
    [compensationAnnotations],
  );
  const monthDates = useMemo(() => getMonthDates(bulkMonth), [bulkMonth]);
  const isBlockedCompensationDate = selectedDate ? compensationDateSet.has(selectedDate) : false;

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
        const iso = toMonthDateKey(date);
        return weekday !== 0 && weekday !== 6 && !compensationDateSet.has(iso);
      })
      .map((date) => toMonthDateKey(date));
    setBulkDates(new Set(nextDates));
  }, [compensationDateSet, monthDates]);

  const handleSelectByWeekdays = useCallback(() => {
    const nextDates = monthDates
      .filter((date) => {
        const iso = toMonthDateKey(date);
        return selectedWeekdays.has(date.getDay()) && !compensationDateSet.has(iso);
      })
      .map((date) => toMonthDateKey(date));
    setBulkDates(new Set(nextDates));
  }, [compensationDateSet, monthDates, selectedWeekdays]);

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

  const stats = useMemo(() => {
    const uniqueStaff = new Set(l02Schedules.map((schedule) => schedule.staff.id)).size;
    const uniqueDays = new Set(l02Schedules.map((schedule) => schedule.workDate.split("T")[0])).size;
    return {
      total: l02Schedules.length,
      uniqueStaff,
      uniqueDays,
      conflicts: l02ConflictSchedules.length,
    };
  }, [l02Schedules, l02ConflictSchedules]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    if (!selectedPeriodId || !selectedStaffId || !selectedDate) {
      setMessage({ type: "error", text: "Vui lòng điền đầy đủ thông tin." });
      return;
    }

    if (!isEditablePeriod) {
      setMessage({ type: "error", text: "Chỉ có thể thêm lịch khi kỳ lịch ở trạng thái DRAFT." });
      return;
    }

    if (compensationDateSet.has(selectedDate)) {
      setMessage({ type: "error", text: "Ngày này là ngày nghỉ bù từ lịch trực 24/24, không thể gán lịch thông tầm." });
      return;
    }

    setSubmitting(true);
    try {
      await api.post("/schedules", {
        periodId: selectedPeriodId,
        staffId: selectedStaffId,
        workDate: selectedDate,
        shiftTypeId: "L02",
        notes,
      });
      setMessage({ type: "success", text: "Gắn lịch thông tầm thành công!" });
      setSelectedStaffId("");
      setSelectedDate("");
      setNotes("");
      await refreshPeriodData(selectedPeriodId);
    } catch (err) {
      setMessage({
        type: "error",
        text: getErrorMessage(err, "Gắn lịch thất bại. Kiểm tra xung đột L01 hoặc ngày nghỉ bù."),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleBulkAssign = async () => {
    if (!selectedPeriodId || !bulkStaffId || bulkDates.size === 0) {
      setMessage({ type: "error", text: "Vui lòng chọn nhân sự và ít nhất một ngày để gán hàng loạt." });
      return;
    }

    if (!isEditablePeriod) {
      setMessage({ type: "error", text: "Chỉ có thể gán lịch khi kỳ lịch ở trạng thái DRAFT." });
      return;
    }

    const blockedDates = [...bulkDates].filter((d) => compensationDateSet.has(d));
    if (blockedDates.length > 0) {
      setMessage({ type: "error", text: `Có ${blockedDates.length} ngày là ngày nghỉ bù từ lịch trực 24/24 và bị khóa.` });
      return;
    }

    setBulkSubmitting(true);
    const results: { date: string; ok: boolean; error?: string }[] = [];

    try {
      for (const date of [...bulkDates].sort()) {
        try {
          await api.post("/schedules", {
            periodId: selectedPeriodId,
            staffId: bulkStaffId,
            workDate: date,
            shiftTypeId: "L02",
            notes: bulkNotes,
          });
          results.push({ date, ok: true });
        } catch (err) {
          results.push({ date, ok: false, error: getErrorMessage(err) });
        }
      }

      const okCount = results.filter((r) => r.ok).length;
      const failCount = results.filter((r) => !r.ok).length;

      if (failCount === 0) {
        setMessage({ type: "success", text: `Đã gán ${okCount} lịch thông tầm hàng loạt thành công!` });
      } else {
        setMessage({ type: "error", text: `Gán được ${okCount}/${results.length} lịch. ${failCount} lịch thất bại (ngày đã có lịch hoặc xung đột).` });
      }

      setBulkStaffId("");
      setBulkDates(new Set());
      setBulkNotes("");
      setBulkMode(false);
      await refreshPeriodData(selectedPeriodId);
    } catch (err) {
      setMessage({ type: "error", text: getErrorMessage(err, "Lỗi gán lịch hàng loạt.") });
    } finally {
      setBulkSubmitting(false);
    }
  };

  async function handleExportExcel() {
    if (!selectedPeriodId) {
      setMessage({ type: "error", text: "Chưa chọn kỳ lịch để xuất Excel." });
      return;
    }

    try {
      setExporting(true);
      setMessage(null);
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${selectedPeriodId}`,
        { credentials: "include" },
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `lich-thong-tam-${selectedPeriodId}.xlsx`;
      anchor.click();
      URL.revokeObjectURL(url);
      setMessage({ type: "success", text: "Đã xuất Excel cho kỳ lịch đang chọn." });
    } catch (err) {
      setMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể xuất Excel lịch thông tầm."),
      });
    } finally {
      setExporting(false);
    }
  }

  async function handlePrint() {
    try {
      setPrinting(true);
      setMessage(null);
      window.print();
    } catch (err) {
      setMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể mở chế độ in lịch thông tầm."),
      });
    } finally {
      setPrinting(false);
    }
  }

  return (
    <DashboardShell
      activeCode="M03"
      title="Lịch thông tầm"
      description="Tạo lịch thông tầm, theo dõi xung đột với trực 24/24 và quản lý theo kỳ lịch"
    >
      <div className="flex items-center gap-2 text-label-md text-on-surface-variant">
        <Link className="flex items-center gap-1 transition-colors hover:text-primary" href="/all-day">
          <span className="material-symbols-outlined text-[16px]">arrow_back</span>
          Quản lý lịch thông tầm
        </Link>
        <span className="material-symbols-outlined text-[16px] text-outline">chevron_right</span>
        <span className="font-medium text-on-surface">Bảng lịch tháng</span>
      </div>

      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-headline-md text-on-surface">Lịch thông tầm</h1>
          <p className="mt-1 text-label-md text-on-surface-variant">
            Theo dõi riêng các ca `L02`, tránh trùng trực `L01` và ngày nghỉ bù.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleCheckConflicts}
            disabled={checkingConflicts || loading || !selectedPeriodId}
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-primary transition-colors hover:bg-surface-container-low shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[16px]">rule</span>
            {checkingConflicts ? "Đang kiểm tra..." : "Kiểm tra xung đột"}
          </button>
          <button
            type="button"
            onClick={handlePublishPeriod}
            disabled={!canPublishPeriod}
            className="flex items-center gap-2 rounded-lg border border-secondary/30 bg-secondary-container/20 px-4 py-2 text-label-md text-secondary transition-colors hover:bg-secondary-container/30 shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[16px]">publish</span>
            {publishing ? "Đang công bố..." : "Lưu & Công bố"}
          </button>
          <button
            type="button"
            onClick={handlePrint}
            disabled={printing || loading}
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-primary transition-colors hover:bg-surface-container-low shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[16px]">print</span>
            {printing ? "Đang mở in..." : "In lịch"}
          </button>
          <button
            type="button"
            onClick={handleExportExcel}
            disabled={exporting || loading || !selectedPeriodId}
            className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[16px]">download</span>
            {exporting ? "Đang xuất..." : "Xuất Excel"}
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-3 shadow-[0_1px_3px_0_rgba(0,0,0,0.05)]">
        <div className="flex items-center gap-3">
          <label className="whitespace-nowrap text-label-md text-on-surface-variant">Kỳ lịch:</label>
          <div className="relative">
            <select
              className="h-9 w-64 appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest pl-3 pr-10 text-label-md text-on-surface cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              value={selectedPeriodId ?? ""}
              onChange={(event) => setSelectedPeriodId(Number(event.target.value))}
            >
              {periods.length === 0 && <option value="">Không có kỳ lịch</option>}
              {periods.map((period) => (
                <option key={period.id} value={period.id}>{period.periodName}</option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline">expand_more</span>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full border border-outline-variant bg-surface px-3 py-1.5 text-label-md text-on-surface">
            <span className="font-semibold text-primary">{stats.total}</span> lịch L02
          </span>
          <span className="rounded-full border border-outline-variant bg-surface px-3 py-1.5 text-label-md text-on-surface">
            <span className="font-semibold text-primary">{stats.uniqueDays}</span> ngày có lịch
          </span>
          <span className="rounded-full border border-outline-variant bg-surface px-3 py-1.5 text-label-md text-on-surface">
            <span className="font-semibold text-primary">{stats.uniqueStaff}</span> nhân sự
          </span>
          <span className="rounded-full border border-outline-variant bg-surface px-3 py-1.5 text-label-md text-on-surface">
            Trạng thái kỳ: <span className="font-semibold text-primary">{selectedPeriod?.status ?? "—"}</span>
          </span>
          <span className={`rounded-full px-3 py-1.5 text-label-md ${stats.conflicts > 0 ? "border border-red-200 bg-red-50 text-error" : "border border-secondary/20 bg-secondary-container/20 text-secondary"}`}>
            {stats.conflicts} xung đột
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-12 lg:gap-6">
        <div className="pb-20 lg:col-span-9 lg:pb-24">
          {loading ? (
            <div className="flex items-center justify-center rounded-xl border border-outline-variant bg-surface-container-lowest p-20 shadow-[0_1px_3px_0_rgba(0,0,0,0.05)]">
              <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : (
            <ScheduleCalendarWidget
              schedules={l02Schedules}
              calendarAnnotations={compensationAnnotations}
              onRefresh={async () => {
                if (selectedPeriodId) {
                  try {
                    await refreshPeriodData(selectedPeriodId);
                  } catch (err) {
                    setMessage({
                      type: "error",
                      text: getErrorMessage(err, "Không thể làm mới lịch thông tầm."),
                    });
                  }
                }
              }}
            />
          )}
        </div>

        <div className="flex flex-col gap-4 lg:col-span-3">

          {/* Bulk Assignment Toggle */}
          {isEditablePeriod && (
            <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-primary text-[20px]">library_add</span>
                  <div>
                    <p className="text-label-md font-semibold text-on-surface">Gán hàng loạt</p>
                    <p className="text-[12px] text-on-surface-variant">Chọn 1 nhân sự + nhiều ngày trong tháng</p>
                  </div>
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
            </div>
          )}

          {bulkMode && (
            <SectionCard title="Gán hàng loạt lịch thông tầm" description="M03-F01 · gán L02 cho 1 nhân sự vào nhiều ngày">
              <div className="space-y-4 px-5 py-4">
                <div>
                  <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-al-staff">
                    Nhân sự
                  </label>
                  <div className="relative">
                    <select
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                      id="bulk-al-staff"
                      value={bulkStaffId}
                      onChange={(e) => setBulkStaffId(e.target.value ? Number(e.target.value) : "")}
                    >
                      <option value="">Chọn nhân sự...</option>
                      {staffList.map((staff) => (
                        <option key={staff.id} value={staff.id}>{staff.fullName}</option>
                      ))}
                    </select>
                    <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline">expand_more</span>
                  </div>
                </div>

                      <div>
                        <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-al-period">
                          Kỳ xếp lịch
                        </label>
                        <div className="relative">
                          <select
                            className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                            id="bulk-al-period"
                            value={selectedPeriodId ?? ""}
                            onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
                            disabled={!isEditablePeriod && Boolean(selectedPeriodId)}
                          >
                            <option value="">Chọn kỳ...</option>
                            {periods.map((period) => (
                              <option key={period.id} value={period.id}>{period.periodName}</option>
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
                        blockedDates={compensationDateSet}
                        highlightedDates={new Set(l02Schedules.map((schedule) => schedule.workDate.split("T")[0] ?? schedule.workDate))}
                        disabled={!isEditablePeriod}
                        helperText="Nhấn vào từng ngày để chọn hoặc bỏ chọn. Ngày nghỉ bù từ lịch trực 24/24 sẽ bị khóa."
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
                            <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-al-preset-name">
                              Tên preset
                            </label>
                            <input
                              id="bulk-al-preset-name"
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
                        <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="bulk-al-notes">
                          Ghi chú
                        </label>
                        <textarea
                          className="w-full resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                          id="bulk-al-notes"
                          rows={2}
                          value={bulkNotes}
                          onChange={(e) => setBulkNotes(e.target.value)}
                          disabled={!isEditablePeriod}
                          placeholder="Ghi chú chung cho các lịch..."
                        />
                      </div>

                {bulkDates.size > 0 && ([...bulkDates].filter((d) => compensationDateSet.has(d)).length) > 0 && (
                  <div className="rounded-lg border border-error/20 bg-error-container/30 px-3 py-2 text-[12px] text-error">
                    Có ngày nghỉ bù trong danh sách chọn. Những ngày này sẽ bị bỏ qua.
                  </div>
                )}

                <button
                  className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-opacity hover:opacity-90 disabled:opacity-60"
                  disabled={bulkSubmitting || bulkDates.size === 0 || !bulkStaffId}
                  type="button"
                  onClick={handleBulkAssign}
                >
                  {bulkSubmitting ? (
                    <>
                      <div className="mr-2 size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                      Đang gán {bulkDates.size} lịch...
                    </>
                  ) : (
                    <>
                      <span className="material-symbols-outlined mr-2 text-[18px]">playlist_add</span>
                      Gán {bulkDates.size} lịch thông tầm
                    </>
                  )}
                </button>
              </div>
            </SectionCard>
          )}

          <SectionCard title="Gán nhanh lịch thông tầm" description="M03-F01 · tạo lịch L02 theo ngày">
            <form className="space-y-4 px-5 py-4" onSubmit={handleSubmit}>
              <div>
                <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="al-period">
                  Kỳ xếp lịch
                </label>
                <div className="relative">
                  <select
                    className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                    id="al-period"
                    value={selectedPeriodId ?? ""}
                    onChange={(event) => setSelectedPeriodId(Number(event.target.value))}
                    disabled={!isEditablePeriod && Boolean(selectedPeriodId)}
                  >
                    <option value="">Chọn kỳ...</option>
                    {periods.map((period) => (
                      <option key={period.id} value={period.id}>{period.periodName}</option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline">expand_more</span>
                </div>
              </div>

              <div>
                <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="al-staff">
                  Nhân sự
                </label>
                <div className="relative">
                  <select
                    className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                    id="al-staff"
                    value={selectedStaffId}
                    onChange={(event) => setSelectedStaffId(event.target.value ? Number(event.target.value) : "")}
                    disabled={!isEditablePeriod}
                  >
                    <option value="">Chọn nhân sự...</option>
                    {staffList.map((staff) => (
                      <option key={staff.id} value={staff.id}>{staff.fullName}</option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[20px] text-outline">expand_more</span>
                </div>
              </div>

              <div>
                <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="al-date">
                  Ngày
                </label>
                <input
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                  id="al-date"
                  type="date"
                  value={selectedDate}
                  onChange={(event) => setSelectedDate(event.target.value)}
                  disabled={!isEditablePeriod}
                />
                {selectedDate && compensationDateSet.has(selectedDate) ? (
                  <p className="mt-2 text-sm text-error">Ngày này là ngày nghỉ bù từ lịch trực 24/24 và đang bị khóa.</p>
                ) : null}
              </div>

              <div>
                <label className="mb-2 block text-label-sm uppercase tracking-wider text-on-surface-variant" htmlFor="al-notes">
                  Ghi chú
                </label>
                <textarea
                  className="w-full resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                  id="al-notes"
                  rows={2}
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  disabled={!isEditablePeriod}
                />
              </div>

              {message ? (
                <div className={`flex items-center gap-2 rounded-lg px-3 py-2 text-label-md font-medium ${message.type === "success" ? "bg-secondary-container text-on-secondary-container" : "bg-error-container text-on-error-container"}`}>
                  <span className="material-symbols-outlined text-[16px]">{message.type === "success" ? "check_circle" : "error"}</span>
                  {message.text}
                </div>
              ) : null}

              <button
                className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-opacity hover:opacity-90 disabled:opacity-60"
                disabled={submitting || !isEditablePeriod || isBlockedCompensationDate}
                type="submit"
              >
                {submitting ? (
                  <>
                    <div className="mr-2 size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    Đang xử lý...
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined mr-2 text-[18px]">add</span>
                    Gán lịch
                  </>
                )}
              </button>
            </form>
          </SectionCard>

          <ConflictInspector
            title="Cảnh báo trực tiếp"
            description="M03-F02 · xung đột L02 với L01 hoặc ngày nghỉ bù"
            conflicts={l02ConflictSchedules
              .map((schedule) => conflictMap.get(schedule.id))
              .filter((detail): detail is ConflictDetail => Boolean(detail))}
            emptyLabel="Không có xung đột ở lịch thông tầm."
            selectedConflict={selectedConflict?.detail ?? null}
            onSelect={(detail) => {
              const schedule = l02Schedules.find((item) => item.id === detail.scheduleId);
              if (!schedule) {
                return;
              }
              setSelectedConflict({ schedule, detail });
            }}
            onClose={() => setSelectedConflict(null)}
          />

          <SectionCard title="Chú thích loại lịch">
            <ul className="flex flex-col gap-3 px-5 py-4">
              {[
                { color: "bg-primary-container border-l-primary", label: "Trực 24/24", colorName: "Xanh dương" },
                { color: "bg-secondary-container border-l-secondary", label: "Thông tầm", colorName: "Xanh lá" },
                { color: "bg-tertiary-fixed border-l-tertiary", label: "Dịch vụ", colorName: "Cam" },
                { color: "bg-expert/10 border-l-expert", label: "Chuyên gia", colorName: "Tím" },
              ].map((item) => (
                <li key={item.label} className="flex items-center gap-3">
                  <div className={`h-4 w-4 shrink-0 rounded border-l-2 ${item.color}`} />
                  <span className="text-label-md text-on-surface">{item.label}</span>
                  <span className="ml-auto rounded bg-surface-container-high px-2 py-0.5 text-label-sm text-on-surface-variant">{item.colorName}</span>
                </li>
              ))}
            </ul>
          </SectionCard>

          <SectionCard title="Ràng buộc thông tầm">
            <div className="space-y-2 px-5 py-4 text-label-md leading-relaxed text-on-surface-variant">
              <p>- Cùng nhân sự, cùng ngày không được có cả `L01` và `L02`.</p>
              <p>- Nhân sự có ngày nghỉ bù thì không được gán lịch thông tầm.</p>
              <p>- Khi kỳ không ở trạng thái `DRAFT`, thao tác tạo mới sẽ bị backend từ chối.</p>
            </div>
          </SectionCard>
        </div>
      </div>

    </DashboardShell>
  );
}
