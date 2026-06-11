"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { Staff, Schedule } from "@/types/api";

function getInitials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

function getRoleLabel(roles: string[]) {
  if (roles.includes("ADMIN")) return "Quản trị viên";
  if (roles.includes("MANAGER")) return "Quản lý";
  if (roles.includes("STAFF")) return "Nhân viên";
  return "Nhân sự";
}

function getStatusLabel(isActive: boolean) {
  return isActive ? "Đang làm việc" : "Đã nghỉ";
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("vi-VN");
}

const SHIFT_COLORS: Record<string, { badge: string; border: string; text: string }> = {
  L01: { badge: "bg-red-50 text-red-700", border: "border-red-400", text: "Trực 24/24" },
  L02: { badge: "bg-blue-50 text-blue-700", border: "border-blue-400", text: "Thông tầm" },
  L03: { badge: "bg-green-50 text-green-700", border: "border-green-400", text: "PK dịch vụ" },
  L04: { badge: "bg-purple-50 text-purple-700", border: "border-purple-400", text: "PK chuyên gia" },
};

export default function StaffDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
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
      <DashboardShell activeSection="staff" title="Chi tiết nhân sự" description="Đang tải...">
        <div className="flex items-center justify-center py-24">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      </DashboardShell>
    );
  }

  if (!staff) {
    return (
      <DashboardShell activeSection="staff" title="Chi tiết nhân sự" description="">
        <div className="flex flex-col items-center justify-center py-24 gap-4">
          <span className="material-symbols-outlined text-5xl text-outline">person_off</span>
          <p className="text-on-surface-variant">{message ?? "Không tìm thấy nhân sự."}</p>
          <Link
            href="/staff"
            className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90"
          >
            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
            Quay lại danh sách
          </Link>
        </div>
      </DashboardShell>
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
    <DashboardShell
      activeSection="staff"
      title={`Hồ sơ: ${staff.fullName}`}
      description={`Mã nhân viên ${staff.username} \u2013 ${getRoleLabel(staff.roles)}`}
    >
      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {/* Back + Actions */}
      <div className="flex items-center justify-between">
        <Link
          href="/staff"
          className="flex items-center gap-1.5 text-label-md text-on-surface-variant hover:text-primary transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          Quay lại danh sách
        </Link>
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
      <section className="grid gap-6 lg:grid-cols-[320px_1fr]">
        <article className="flex flex-col items-center rounded-xl border border-outline-variant bg-surface-container-lowest p-6 shadow-sm text-center">
          <div className="flex h-20 w-20 items-center justify-center rounded-full bg-primary text-on-primary text-2xl font-bold">
            {getInitials(staff.fullName)}
          </div>
          <h2 className="mt-4 text-headline-md font-semibold text-on-surface">{staff.fullName}</h2>
          <p className="mt-1 text-body-sm text-on-surface-variant">{getRoleLabel(staff.roles)}</p>
          <span className={`mt-3 inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[12px] font-semibold ${
            staff.isActive
              ? "bg-secondary-container text-secondary"
              : "bg-surface-container-high text-outline"
          }`}>
            <span className={`h-2 w-2 rounded-full ${staff.isActive ? "bg-secondary" : "bg-outline"}`} />
            {getStatusLabel(staff.isActive)}
          </span>

          <div className="mt-6 w-full space-y-3 text-left">
            {[
              { icon: "mail", label: "Email", value: staff.email ?? "Chưa cập nhật" },
              { icon: "phone", label: "Điện thoại", value: staff.phone ?? "Chưa cập nhật" },
              { icon: "local_hospital", label: "Chuyên khoa", value: staff.specialty?.name ?? "Chưa phân khoa" },
              { icon: "event", label: "Ngày vào làm", value: formatDate(staff.createdAt) },
            ].map((item) => (
              <div key={item.label} className="flex items-start gap-3 rounded-lg bg-surface px-3 py-2.5">
                <span className="material-symbols-outlined text-[18px] text-outline shrink-0 mt-0.5">
                  {item.icon}
                </span>
                <div className="min-w-0">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">{item.label}</p>
                  <p className="text-[13px] text-on-surface truncate">{item.value}</p>
                </div>
              </div>
            ))}
          </div>
        </article>

        <div className="space-y-6">
          {/* Workload Stats */}
          <section className="grid gap-4 sm:grid-cols-3">
            {[
              { label: "Tổng ca trực", value: schedules.length, icon: "event_available", accent: "bg-primary-fixed text-primary" },
              { label: "Giờ làm việc ước tính", value: `${totalHours}h`, icon: "schedule", accent: "bg-secondary-container text-secondary" },
              { label: "Giới hạn/tháng", value: staff.maxShiftsPerMonth, icon: "speed", accent: "bg-tertiary-fixed text-tertiary" },
            ].map((item) => (
              <article key={item.label} className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
                <div className="flex justify-between items-start">
                  <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">{item.label}</p>
                  <span className={`material-symbols-outlined p-1.5 rounded-md ${item.accent} text-[18px]`}>
                    {item.icon}
                  </span>
                </div>
                <p className="mt-3 text-display-lg font-bold text-on-surface">{item.value}</p>
              </article>
            ))}
          </section>

          {/* Shift breakdown */}
          <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <h3 className="text-title-lg font-semibold text-on-surface mb-4">Phân bổ loại lịch</h3>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              {(Object.entries(SHIFT_COLORS) as [string, typeof SHIFT_COLORS[string]][]).map(([type, color]) => (
                <div key={type} className={`rounded-lg border-l-4 p-3 ${color.border} bg-surface`}>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">{color.text}</p>
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
                  const color = SHIFT_COLORS[schedule.shiftType.id] ?? SHIFT_COLORS.L01;
                  return (
                    <div
                      key={schedule.id}
                      className={`flex items-center justify-between rounded-lg border-l-4 p-3 ${color.border} bg-surface`}
                    >
                      <div className="flex items-center gap-3">
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold ${color.badge}`}>
                          {schedule.shiftType.id}
                        </span>
                        <div>
                          <p className="text-[13px] font-medium text-on-surface">{color.text}</p>
                          <p className="text-[12px] text-on-surface-variant">
                            {schedule.period?.periodName ?? `Kỳ #${schedule.periodId}`}
                          </p>
                        </div>
                      </div>
                      <span className="text-[13px] font-medium text-on-surface-variant">
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
    </DashboardShell>
  );
}
