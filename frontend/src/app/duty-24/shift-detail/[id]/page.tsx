"use client";

import Link from "next/link";
import { notFound, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { ShiftDetailInfo } from "@/components/shift-detail/ShiftDetailInfo";
import { ShiftDetailTable } from "@/components/shift-detail/ShiftDetailTable";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { Schedule } from "@/types/api";
import type { ShiftDetailStatus, ShiftDetailViewModel } from "@/types/shift-detail";

type ShiftDetailPageProps = {
  params: Promise<{ id: string }>;
};

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("vi-VN");
}

function formatWeekday(dateStr: string): string {
  const date = new Date(dateStr);
  const label = new Intl.DateTimeFormat("vi-VN", { weekday: "long" }).format(date);
  return label.charAt(0).toUpperCase() + label.slice(1);
}

function formatTimeRange(startTime?: string | null, endTime?: string | null): string {
  if (!startTime || !endTime) {
    return "Theo cấu hình loại ca";
  }

  const start = startTime.slice(0, 5);
  const end = endTime.slice(0, 5);
  return `${start} - ${end}`;
}

function formatPeriodRange(startDate?: string, endDate?: string): string {
  if (!startDate || !endDate) {
    return "—";
  }

  return `${formatDate(startDate)} - ${formatDate(endDate)}`;
}

function resolveStatus(schedule: Schedule): ShiftDetailStatus {
  if (schedule.hasConflict) {
    return "pending";
  }
  return "approved";
}

function resolveRoleBadge(roles: string[]): "primary" | "secondary" | "neutral" {
  if (roles.includes("ADMIN")) {
    return "primary";
  }
  if (roles.includes("MANAGER")) {
    return "secondary";
  }
  return "neutral";
}

function buildShiftDetail(schedule: Schedule): ShiftDetailViewModel {
  const roles = schedule.staff.roles ?? [];
  const specialtyName = schedule.staff.specialtyName ?? "Chưa cập nhật chuyên khoa";

  return {
    id: String(schedule.id),
    code: `L01-${schedule.id}`,
    department: schedule.shiftType.id,
    departmentFull: specialtyName,
    date: formatDate(schedule.workDate),
    weekday: formatWeekday(schedule.workDate),
    shiftType: schedule.shiftType.name,
    shiftTime: formatTimeRange(schedule.shiftType.startTime, schedule.shiftType.endTime),
    status: resolveStatus(schedule),
    compensationDate: schedule.compensationDate ? formatDate(schedule.compensationDate) : null,
    periodName: schedule.period?.periodName,
    periodRange: formatPeriodRange(schedule.period?.startDate, schedule.period?.endDate),
    specialtyName,
    roles,
    notes: schedule.notes,
    conflictReasons: schedule.conflictReasons ?? [],
    staff: [
      {
        id: String(schedule.staff.id),
        name: schedule.staff.fullName,
        initials: schedule.staff.fullName.charAt(0).toUpperCase(),
        role: roles.length > 0 ? roles.join(" / ") : "STAFF",
        roleBadge: resolveRoleBadge(roles),
        department: specialtyName,
        departmentFull: specialtyName,
        position: schedule.shiftType.description ?? "Tham gia ca trực theo phân công",
        note: schedule.hasConflict
          ? "Ca trực đang có xung đột, xem chi tiết cảnh báo bên trên."
          : schedule.notes ?? undefined,
        avatarColor: "bg-primary-fixed-dim text-on-primary-fixed",
      },
    ],
  };
}

function ShiftDetailLoadingState() {
  return (
    <div className="flex min-h-[320px] flex-col items-center justify-center gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest">
      <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      <p className="text-label-md text-on-surface-variant">Đang tải chi tiết ca trực...</p>
    </div>
  );
}

function ShiftDetailErrorState({ error }: { error: string }) {
  const router = useRouter();

  return (
    <div className="flex min-h-[320px] flex-col items-center justify-center gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest px-6 text-center">
      <span className="material-symbols-outlined text-[48px] text-error">error</span>
      <p className="max-w-xl text-label-md text-on-surface-variant">{error}</p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <button
          className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90"
          onClick={() => router.refresh()}
          type="button"
        >
          <span className="material-symbols-outlined text-[16px]">refresh</span>
          Thử lại
        </button>
        <Link
          className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low"
          href="/duty-24"
        >
          <span className="material-symbols-outlined text-[16px]">arrow_back</span>
          Về danh sách trực
        </Link>
      </div>
    </div>
  );
}

export default function ShiftDetailPage({ params }: ShiftDetailPageProps) {
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [scheduleId, setScheduleId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const resolvedParams = await params;
        const nextScheduleId = Number(resolvedParams.id);

        if (!Number.isInteger(nextScheduleId)) {
          notFound();
        }

        if (!cancelled) {
          setScheduleId(nextScheduleId);
          setIsLoading(true);
          setError(null);
        }

        const scheduleData = await api.get<Schedule>(`/schedules/${nextScheduleId}`);

        if (scheduleData.shiftType.id !== "L01") {
          notFound();
        }

        if (!cancelled) {
          setSchedule(scheduleData);
        }
      } catch (err) {
        if (!cancelled) {
          const message = getErrorMessage(err, "Không thể tải chi tiết ca trực.");
          setSchedule(null);
          setError(message);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [params]);

  const shiftDetail = useMemo(() => {
    if (!schedule) {
      return null;
    }

    return buildShiftDetail(schedule);
  }, [schedule]);

  return (
    <DashboardShell
      activeCode="M02"
      title="Lịch trực 24/24"
      description="Quản lý lịch trực"
    >
      <div className="flex items-center gap-2 text-label-md text-on-surface-variant">
        <Link className="flex items-center gap-1 transition-colors hover:text-primary" href="/duty-24">
          <span className="material-symbols-outlined text-[16px]">arrow_back</span>
          Quản lý lịch trực
        </Link>
        <span className="material-symbols-outlined text-[16px] text-outline">chevron_right</span>
        <span className="font-medium text-on-surface">Chi tiết ca trực</span>
      </div>

      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-headline-md text-on-surface">Chi tiết ca trực</h1>
          <p className="mt-1 text-label-md text-on-surface-variant">
            Mã ca: {shiftDetail?.code ?? (scheduleId ? `L01-${scheduleId}` : "—")}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-on-surface shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] transition-colors hover:bg-surface-container-low"
            href="/duty-24"
          >
            <span className="material-symbols-outlined text-[16px]">list_alt</span>
            Về danh sách trực
          </Link>
        </div>
      </div>

      {isLoading ? <ShiftDetailLoadingState /> : null}
      {!isLoading && error ? <ShiftDetailErrorState error={error} /> : null}
      {!isLoading && !error && shiftDetail ? (
        <>
          <ShiftDetailInfo shift={shiftDetail} />
          <ShiftDetailTable shift={shiftDetail} />
        </>
      ) : null}
    </DashboardShell>
  );
}
