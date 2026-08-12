"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { getRoleLabel } from "@/lib/roleLabels";
import type { Staff, Schedule } from "@/types/api";

function getInitials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

// getRoleLabel imported from @/lib/roleLabels

type StatusKey = "ACTIVE" | "ON_LEAVE" | "INACTIVE";

const STATUS_LABEL: Record<StatusKey, string> = {
  ACTIVE: "Đang làm việc",
  ON_LEAVE: "Nghỉ phép",
  INACTIVE: "Nghỉ việc",
};

const STATUS_CLASS: Record<StatusKey, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-800",
  ON_LEAVE: "bg-amber-100 text-amber-800 border border-amber-300",
  INACTIVE: "bg-surface-container-high text-outline",
};

const STATUS_DOT: Record<StatusKey, string> = {
  ACTIVE: "bg-emerald-100",
  ON_LEAVE: "bg-tertiary",
  INACTIVE: "bg-outline",
};

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("vi-VN");
}

const SHIFT_BADGE: Record<string, string> = {
  L01: "bg-shift-24 text-on-shift-24 border border-shift-24",
  L02: "bg-shift-all-day text-on-shift-all-day border border-shift-all-day",
  L03: "bg-shift-service text-on-shift-service border border-shift-service",
  L04: "bg-shift-expert text-on-shift-expert border border-shift-expert",
};

const SHIFT_BORDER: Record<string, string> = {
  L01: "border-l-[var(--color-shift-24)]",
  L02: "border-l-[var(--color-shift-all-day)]",
  L03: "border-l-[var(--color-shift-service)]",
  L04: "border-l-[var(--color-shift-expert)]",
};

export default function StaffDetailPage() {
  return <StaffDetailContent />;
}

function StaffDetailContent() {
  const params = useParams<{ id: string }>();
  const staffId = Number(params.id);

  const [staff, setStaff] = useState<Staff | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    if (isNaN(staffId)) {
      setMessage("ID nhân sự không hợp lệ.");
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setMessage(null);

      const [staffRes, scheduleRes] = await Promise.allSettled([
        api.get<Staff>(`/staff/${staffId}`),
        api.get<Schedule[]>(`/schedules/staff/${staffId}`),
      ]);

      if (staffRes.status === "fulfilled") {
        setStaff(staffRes.value);
      } else {
        setMessage(getErrorMessage(staffRes.reason, "Không thể tải thông tin nhân sự."));
      }

      if (scheduleRes.status === "fulfilled") {
        setSchedules(scheduleRes.value ?? []);
      } else {
        setMessage((prev) => prev || getErrorMessage(scheduleRes.reason, "Không thể tải lịch trực của nhân sự này."));
      }
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải dữ liệu."));
    } finally {
      setLoading(false);
    }
  }, [staffId]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!staff) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-4">
        <span className="material-symbols-outlined text-5xl text-outline">person_off</span>
        <p className="text-on-surface-variant">{message ?? "Không tìm thấy nhân sự."}</p>
        <Link
          href="/staff"
          className="flex items-center gap-2 rounded-lg bg-blue-100 px-4 py-2 text-label-md text-blue-800 transition-colors hover:bg-blue-100/90"
        >
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          Quay lại danh sách
        </Link>
      </div>
    );
  }

  const totalHours = schedules.reduce((sum, s) => {
    if (s.shiftType.id === "L01") return sum + 24;
    if (s.shiftType.id === "L02") return sum + 8;
    if (s.shiftType.id === "L03" || s.shiftType.id === "L04") return sum + 4;
    return sum;
  }, 0);

  const shiftByType: Record<string, number> = {
    L01: schedules.filter((s) => s.shiftType.id === "L01").length,
    L02: schedules.filter((s) => s.shiftType.id === "L02").length,
    L03: schedules.filter((s) => s.shiftType.id === "L03").length,
    L04: schedules.filter((s) => s.shiftType.id === "L04").length,
  };

  return (
    <>
      {message && (
        <div className="rounded-lg border border-red-300 bg-red-100 text-red-800 border border-red-300 px-4 py-3 text-sm text-red-800">
          {message}
        </div>
      )}

      {/* Back + Header */}
      <nav aria-label="Đường dẫn" className="flex items-center gap-2 text-label-md text-on-surface-variant mb-3">
        <Link href="/staff" className="hover:text-blue-800 transition-colors flex items-center gap-1">
          <span className="material-symbols-outlined text-[18px]">groups</span>
          Nhân sự
        </Link>
        <span className="material-symbols-outlined text-[16px]">chevron_right</span>
        <span className="text-on-surface font-medium">{staff.fullName}</span>
      </nav>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link
            href={`/staff/${staffId}/edit`}
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
          >
            <span className="material-symbols-outlined text-[18px]">edit</span>
            Chỉnh sửa
          </Link>
        </div>
      </div>

      {/* Profile Card */}
      <section className="grid gap-4 lg:grid-cols-[280px_1fr]">
        <article className="flex flex-col items-center rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-blue-100 text-blue-800 text-xl font-bold">
            {getInitials(staff.fullName)}
          </div>
          <h2 className="mt-3 text-headline-md font-semibold text-on-surface">{staff.fullName}</h2>
          <p className="mt-0.5 text-[12px] text-on-surface-variant">{getRoleLabel(staff.roles)}</p>
          <span className={`mt-2 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold ${STATUS_CLASS[staff.status as StatusKey] ?? STATUS_CLASS.INACTIVE}`}>
            <span className={`h-1.5 w-1.5 rounded-full ${STATUS_DOT[staff.status as StatusKey] ?? STATUS_DOT.INACTIVE}`} />
            {STATUS_LABEL[staff.status as StatusKey] ?? "Nghỉ việc"}
          </span>

          <div className="mt-4 w-full space-y-2 text-left">
            {[
              { icon: "mail", label: "Email", value: staff.email ?? "Chưa cập nhật" },
              { icon: "phone", label: "Điện thoại", value: staff.phone ?? "Chưa cập nhật" },
              { icon: "local_hospital", label: "Chuyên khoa", value: staff.specialty?.name ?? "Chưa phân khoa" },
              { icon: "event", label: "Ngày vào làm", value: formatDate(staff.createdAt) },
            ].map((item) => (
              <div key={item.label} className="flex items-start gap-2.5 rounded-lg bg-surface px-2.5 py-2">
                <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">
                  {item.icon}
                </span>
                <div className="min-w-0">
                  <p className="text-[11px] text-on-surface-variant">{item.label}</p>
                  <p className="text-[12px] text-on-surface truncate">{item.value}</p>
                </div>
              </div>
            ))}
          </div>
        </article>

        <div className="space-y-4">
          {/* Workload Stats */}
          <section className="grid gap-3 sm:grid-cols-3">
            {[
              { label: "Tổng ca trực", value: schedules.length, icon: "event_available", accent: "bg-blue-100 text-blue-800" },
              { label: "Giờ làm việc ước tính", value: `${totalHours}h`, icon: "schedule", accent: "bg-emerald-100 text-emerald-800" },
              { label: "Giới hạn/tháng", value: staff.maxShiftsPerMonth, icon: "speed", accent: "bg-amber-100 text-amber-800" },
            ].map((item) => (
              <article key={item.label} className="rounded-lg border border-outline-variant bg-surface-container-lowest p-3 shadow-sm">
                <div className="flex justify-between items-start">
                  <p className="text-[11px] text-on-surface-variant">{item.label}</p>
                  <span className={`material-symbols-outlined p-1 rounded ${item.accent} text-[14px]`}>
                    {item.icon}
                  </span>
                </div>
                <p className="mt-1 text-[20px] font-bold leading-none text-on-surface">{item.value}</p>
              </article>
            ))}
          </section>

          {/* Shift breakdown */}
          <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
            <h3 className="text-title-lg font-semibold text-on-surface mb-3">Phân bổ loại lịch</h3>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              {(Object.entries(SHIFT_BADGE) as [string, string][]).map(([type]) => (
                <div key={type} className={`rounded-lg border-l-4 p-3 bg-surface ${SHIFT_BORDER[type] ?? "border-l-[var(--color-outline)]"}`}>
                  <p className="text-label-sm text-on-surface-variant">
                    {type === "L01" ? "Trực 24/24" : type === "L02" ? "Thông tầm" : type === "L03" ? "PK dịch vụ" : "PK chuyên gia"}
                  </p>
                  <p className="mt-1 text-headline-md font-bold text-on-surface">{shiftByType[type] ?? 0} ca</p>
                </div>
              ))}
            </div>
          </section>

          {/* Recent schedules */}
          <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <h3 className="text-title-lg font-semibold text-on-surface mb-4">
              Lịch trực gần đây
            </h3>
            {schedules.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant py-8 text-center">
                Chưa có lịch trực nào cho nhân sự này.
              </p>
            ) : (
              <div className="space-y-3">
                {schedules.slice(0, 10).map((schedule) => {
                  const badge = SHIFT_BADGE[schedule.shiftType.id] ?? "bg-surface-container-low text-on-surface border-outline-variant";
                  return (
                    <div
                      key={schedule.id}
                      className={`flex items-center justify-between rounded-lg border-l-4 p-3 bg-surface ${SHIFT_BORDER[schedule.shiftType.id] ?? "border-l-[var(--color-outline)]"}`}
                    >
                      <div className="flex items-center gap-3">
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-label-sm font-semibold ${badge}`}>
                          {schedule.shiftType.id}
                        </span>
                        <div>
                          <p className="text-label-md font-medium text-on-surface">{schedule.shiftType.name}</p>
                          <p className="text-label-md text-on-surface-variant">
                            {schedule.period?.periodName ?? `Kỳ #${schedule.periodId}`}
                          </p>
                        </div>
                      </div>
                      <span className="text-label-md font-medium text-on-surface-variant">
                        {formatDate(schedule.workDate)}
                      </span>
                    </div>
                  );
                })}
                {schedules.length > 10 && (
                  <p className="text-center text-label-sm text-on-surface-variant pt-2">
                    +{schedules.length - 10} lịch trực khác
                  </p>
                )}
              </div>
            )}
          </section>
        </div>
      </section>
    </>
  );
}
