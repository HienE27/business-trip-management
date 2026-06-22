"use client";
/* eslint-disable react-hooks/exhaustive-deps */

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { ROLE_LABELS } from "@/lib/roleLabels";
import type { Staff, Schedule, SchedulePeriod } from "@/types/api";

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

function formatDate(dateStr: string): string {
  if (!dateStr) return "—";
  return new Date(dateStr).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

// Role labels — shared via @/lib/roleLabels

const SHIFT_TYPE_BADGE: Record<string, string> = {
  L01: "bg-[var(--color-shift-24)]/10 text-[var(--color-on-shift-24)] border border-[var(--color-shift-24)]/30",
  L02: "bg-[var(--color-shift-all-day)]/10 text-[var(--color-on-shift-all-day)] border border-[var(--color-shift-all-day)]/30",
  L03: "bg-[var(--color-shift-service)]/10 text-[var(--color-on-shift-service)] border border-[var(--color-shift-service)]/30",
  L04: "bg-[var(--color-shift-expert)]/10 text-[var(--color-on-shift-expert)] border border-[var(--color-shift-expert)]/30",
};

const SHIFT_TYPE_BORDER: Record<string, string> = {
  L01: "border-l-[var(--color-shift-24)]",
  L02: "border-l-[var(--color-shift-all-day)]",
  L03: "border-l-[var(--color-shift-service)]",
  L04: "border-l-[var(--color-shift-expert)]",
};

const SHIFT_TYPE_ICONS: Record<string, string> = {
  L01: "emergency",
  L02: "schedule",
  L03: "medical_services",
  L04: "stethoscope",
};

export default function StaffProfilePage() {
  const router = useRouter();
  const [staff, setStaff] = useState<Staff | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [schedulesLoading, setSchedulesLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<"info" | "schedule" | "stats">("info");
  const [message, setMessage] = useState<string | null>(null);

  const userId = staff?.id ?? null;

  const stats = {
    total: schedules.length,
    L01: schedules.filter((s) => s.shiftType.id === "L01").length,
    L02: schedules.filter((s) => s.shiftType.id === "L02").length,
    L03: schedules.filter((s) => s.shiftType.id === "L03").length,
    L04: schedules.filter((s) => s.shiftType.id === "L04").length,
    conflicts: schedules.filter((s) => s.hasConflict).length,
  };

  // Load profile on mount
  useEffect(() => {
    let cancelled = false;

    const loadProfile = async () => {
      setLoading(true);
      setMessage(null);
      try {
        const [profileData, periodsData] = await Promise.all([
          api.get<Staff>("/staff/me"),
          api.get<SchedulePeriod[]>("/periods"),
        ]);
        if (cancelled) return;
        setStaff(profileData);
        setPeriods(periodsData ?? []);
        const preferred = (periodsData ?? []).find(
          (p) => p.status === "PUBLISHED" || p.status === "DRAFT"
        ) ?? (periodsData ?? [])[0];
        if (preferred) {
          setSelectedPeriodId(preferred.id);
        }
      } catch (err) {
        if (cancelled) return;
        setStaff(null);
        setPeriods([]);
        setSelectedPeriodId(null);
        const msg = getErrorMessage(err, "Không thể tải hồ sơ nhân sự.");
        setMessage(msg);
        if (msg.toLowerCase().includes("401") || msg.toLowerCase().includes("unauthorized")) {
          router.replace("/login");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void loadProfile();
    return () => { cancelled = true; };
  }, [router]);

  // Load schedules for selected period
  const cancelledRef = useRef(false);

  const loadSchedules = async () => {
    if (!userId || !selectedPeriodId) return;
    try {
      setSchedulesLoading(true);
      const data = await api.get<Schedule[]>(`/schedules/staff/${userId}`);
      if (cancelledRef.current) return;
      const filtered = (data ?? []).filter((s) => s.periodId === selectedPeriodId);
      setSchedules(filtered);
    } catch {
      if (!cancelledRef.current) { setSchedules([]); setMessage("Không thể tải lịch trực của bạn."); }
    } finally {
      if (!cancelledRef.current) setSchedulesLoading(false);
    }
  };

  // Reload schedules when period changes (after profile is loaded)
  useEffect(() => {
    if (!userId || !selectedPeriodId) return;
    cancelledRef.current = false;
    void loadSchedules();
    return () => { cancelledRef.current = true; };
  }, [userId, selectedPeriodId]);

  if (loading) {
    return (
      <>
        <div className="flex flex-col items-center justify-center gap-4 py-20">
          <span className="material-symbols-outlined text-6xl text-outline animate-pulse">account_circle</span>
          <p className="text-center text-on-surface-variant">Đang tải hồ sơ cá nhân…</p>
        </div>
      </>
    );
  }

  if (!staff) {
    return (
      <>
        <div className="flex flex-col items-center justify-center py-20 gap-4">
          <span className="material-symbols-outlined text-6xl text-outline">person_off</span>
          <p className="text-on-surface-variant text-lg">Không tìm thấy hồ sơ nhân sự.</p>
          <Link
            href="/login"
            className="px-4 py-2 bg-primary text-on-primary rounded-lg hover:bg-primary/90 transition-colors"
          >
            Đăng nhập lại
          </Link>
        </div>
      </>
    );
  }

  return (
    <>
      {message && (
        <div className="rounded-xl border border-tertiary-container bg-tertiary-container/30 px-3 py-2.5 text-[13px] text-on-surface">
          {message}
        </div>
      )}

      {/* Profile Header Card */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm p-4">
        <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4">
          {/* Avatar */}
          <div className="relative shrink-0">
            <div className="h-20 w-20 rounded-full bg-primary-fixed flex items-center justify-center text-2xl font-bold text-on-primary-fixed-variant select-none">
              {getInitials(staff.fullName)}
            </div>
            <span
              className={`absolute bottom-1 right-1 h-3.5 w-3.5 rounded-full border-2 border-surface-container-lowest ${
                staff.isActive ? "bg-secondary" : "bg-outline"
              }`}
            />
          </div>

          {/* Info */}
          <div className="flex-1 text-center sm:text-left">
            <h1 className="font-headline-lg text-on-surface">{staff.fullName}</h1>
            <p className="text-[13px] text-on-surface-variant mt-0.5">@{staff.username}</p>

            {/* Role badges */}
            <div className="flex flex-wrap gap-1.5 mt-2 justify-center sm:justify-start">
              {staff.roles.map((role) => (
                <span
                  key={role}
                  className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-primary-fixed/30 text-primary border border-primary/20"
                >
                  {ROLE_LABELS[role] ?? role}
                </span>
              ))}
            </div>

            {/* Status */}
            <div className="mt-2">
              <span
                className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium ${
                  staff.isActive
                    ? "bg-secondary-container text-on-secondary-container border border-secondary/20"
                    : "bg-surface-container-highest text-outline border border-outline"
                }`}
              >
                <span
                  className={`h-1.5 w-1.5 rounded-full ${staff.isActive ? "bg-secondary" : "bg-outline"}`}
                />
                {staff.isActive ? "Đang làm việc" : "Đã nghỉ"}
              </span>
            </div>
          </div>

          {/* Edit button */}
          <div className="shrink-0">
            <button
              type="button"
              onClick={() => router.push(`/staff/${staff?.id}/edit`)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-primary text-on-primary rounded-lg hover:bg-primary/90 transition-colors text-[12px] font-medium"
            >
              <span className="material-symbols-outlined text-[16px]">edit</span>
              Chỉnh sửa
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Main content */}
        <div className="lg:col-span-8 flex flex-col">
          {/* Tab Header */}
          <div className="flex border-b border-outline-variant bg-surface-container-lowest rounded-t-xl overflow-hidden" role="tablist" aria-label="Hồ sơ cá nhân">
            {[
              { key: "info", label: "Thông tin", icon: "person" },
              { key: "schedule", label: "Lịch công tác", icon: "calendar_month" },
              { key: "stats", label: "Thống kê", icon: "bar_chart" },
            ].map((tab) => (
              <button
                aria-selected={activeTab === tab.key}
                aria-controls={`profile-tabpanel-${tab.key}`}
                className={`flex-1 flex items-center justify-center gap-1.5 py-2.5 text-[12px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  activeTab === tab.key
                    ? "bg-surface-container-lowest text-primary border-b-2 border-primary"
                    : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low"
                }`}
                id={`profile-tab-${tab.key}`}
                key={tab.key}
                onClick={() => setActiveTab(tab.key as typeof activeTab)}
                role="tab"
                type="button"
              >
                <span aria-hidden="true" className="material-symbols-outlined text-[16px]">{tab.icon}</span>
                {tab.label}
              </button>
            ))}
          </div>

          <div
            className="bg-surface-container-lowest border border-outline-variant border-t-0 rounded-b-xl p-4 shadow-sm"
            id={`profile-tabpanel-${activeTab}`}
            role="tabpanel"
            aria-labelledby={`profile-tab-${activeTab}`}
          >

            {/* Tab: Info */}
            {activeTab === "info" && (
              <div className="space-y-5">
                <div>
                  <h3 className="font-title-lg text-on-surface mb-3">Thông tin cá nhân</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {[
                      { label: "Họ và tên", value: staff.fullName },
                      { label: "Tên đăng nhập", value: staff.username },
                      { label: "Email", value: staff.email ?? "—" },
                      { label: "Số điện thoại", value: staff.phone ?? "—" },
                    ].map((item) => (
                      <div key={item.label} className="bg-surface rounded-lg p-3">
                        <p className="text-[11px] text-on-surface-variant">{item.label}</p>
                        <p className="font-label-md text-on-surface mt-0.5">{item.value}</p>
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <h3 className="font-title-lg text-on-surface mb-3">Thông tin công việc</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {[
                      { label: "Chuyên khoa", value: staff.specialty?.name ?? "Chưa phân khoa" },
                      { label: "Số ca trực tối đa/tháng", value: `${staff.maxShiftsPerMonth} ca` },
                      { label: "Ngày tạo tài khoản", value: formatDate(staff.createdAt) },
                      { label: "Vai trò", value: staff.roles.map((r) => ROLE_LABELS[r] ?? r).join(", ") },
                    ].map((item) => (
                      <div key={item.label} className="bg-surface-container-low rounded-lg p-4">
                        <p className="text-label-sm text-on-surface-variant">{item.label}</p>
                        <p className="font-label-md text-on-surface mt-1">{item.value}</p>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* Tab: Schedule */}
            {activeTab === "schedule" && (
              <div className="space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <h3 className="font-title-lg text-on-surface">Lịch công tác</h3>
                  <div className="relative w-full sm:w-64">
                    <span aria-hidden="true" className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">date_range</span>
                    <label htmlFor="profile-schedule-period" className="sr-only">Chọn kỳ xếp lịch</label>
                    <select
                      id="profile-schedule-period"
                      className="w-full pl-10 pr-8 py-2 bg-surface-container-low border border-outline-variant text-body-sm text-on-surface rounded-lg focus:outline-none focus:ring-1 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer"
                      value={selectedPeriodId ?? ""}
                      onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
                    >
                      <option value="" disabled>Chọn kỳ xếp lịch</option>
                      {periods.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.periodName}
                        </option>
                      ))}
                    </select>
                    <span aria-hidden="true" className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">expand_more</span>
                  </div>
                </div>

                {schedulesLoading ? (
                  <div className="flex items-center justify-center py-12">
                    <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                  </div>
                ) : schedules.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-outline-variant bg-surface px-5 py-10 text-center">
                    <p className="text-body-sm text-on-surface-variant">
                      Chưa có lịch tải lên cho kỳ đang chọn.
                    </p>
                    <button
                      type="button"
                      onClick={() => void loadSchedules()}
                      className="mt-4 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90"
                      disabled={!selectedPeriodId}
                    >
                      Tải lịch kỳ này
                    </button>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse" aria-label="Page Table">
                      <thead>
                        <tr className="bg-surface-container-low border-b border-outline-variant">
                          {["Ngày", "Loại lịch", "Trạng thái"].map((h) => (
                            <th scope="col" key={h}
                              className="py-3 px-4 text-label-sm text-on-surface-variant font-semibold">
                              {h}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant">
                        {schedules.map((s) => (
                          <tr
                            key={s.id}
                            className="hover:bg-surface-container-low transition-colors"
                          >
                            <td className="py-3 px-4">
                              <div className="flex items-center gap-2">
                                <span className="material-symbols-outlined text-on-surface-variant text-[18px]">calendar_today</span>
                                <span className="font-label-md text-on-surface">
                                  {formatDate(s.workDate)}
                                </span>
                              </div>
                            </td>
                            <td className="py-3 px-4">
                              <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-label-sm font-medium ${SHIFT_TYPE_BADGE[s.shiftType.id] ?? "bg-surface-container-low border-outline-variant text-on-surface"}`}>
                                <span className="material-symbols-outlined text-[14px]">{SHIFT_TYPE_ICONS[s.shiftType.id] ?? "event"}</span>
                                {s.shiftType.name}
                              </span>
                            </td>
                            <td className="py-3 px-4">
                              <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold border ${
                                s.hasConflict
                                  ? "bg-error-container text-error border-error/20"
                                  : "bg-secondary-container text-secondary border-secondary/20"
                              }`}>
                                <span className={`h-1.5 w-1.5 rounded-full ${s.hasConflict ? "bg-error" : "bg-secondary"}`} />
                                {s.hasConflict ? "Xung đột" : "Hợp lệ"}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {/* Tab: Stats */}
            {activeTab === "stats" && (
              <div className="space-y-6">
                <h3 className="font-title-lg text-on-surface">Thống kê lịch công tác</h3>

                {/* Summary */}
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  {[
                    {
                      label: "Tổng ca trực",
                      value: stats.total,
                      icon: "event_available",
                      color: "text-primary bg-primary-fixed",
                    },
                    {
                      label: "Xung đột",
                      value: stats.conflicts,
                      icon: "warning",
                      color: stats.conflicts > 0 ? "text-error bg-error-container" : "text-outline bg-surface-container-highest",
                    },
                    {
                      label: "Loại lịch đã xếp",
                      value: [stats.L01, stats.L02, stats.L03, stats.L04].filter(Boolean).length,
                      icon: "category",
                      color: "text-on-surface-variant bg-surface-container-low",
                    },
                  ].map((item) => (
                    <div
                      key={item.label}
                      className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5 flex flex-col gap-2"
                    >
                      <div className="flex justify-between items-start">
                        <p className="text-label-sm text-on-surface-variant">{item.label}</p>
                        <span className={`material-symbols-outlined text-[20px] p-1.5 rounded-md ${item.color}`}>
                          {item.icon}
                        </span>
                      </div>
                      <p className="font-display-lg text-on-surface">{item.value}</p>
                    </div>
                  ))}
                </div>

                {/* By shift type */}
                <div>
                  <h4 className="font-title-md text-on-surface mb-3">Chi tiết theo loại lịch</h4>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    {[
                      { label: "Trực 24/24", value: stats.L01, color: "bg-[var(--color-shift-24)]", bg: "bg-[var(--color-shift-24)]/10 border-[var(--color-shift-24)]/30" },
                      { label: "Thông tầm", value: stats.L02, color: "bg-[var(--color-shift-all-day)]", bg: "bg-[var(--color-shift-all-day)]/10 border-[var(--color-shift-all-day)]/30" },
                      { label: "Khám dịch vụ", value: stats.L03, color: "bg-[var(--color-shift-service)]", bg: "bg-[var(--color-shift-service)]/10 border-[var(--color-shift-service)]/30" },
                      { label: "Khám chuyên gia", value: stats.L04, color: "bg-[var(--color-shift-expert)]", bg: "bg-[var(--color-shift-expert)]/10 border-[var(--color-shift-expert)]/30" },
                    ].map((item) => (
                      <div key={item.label} className={`rounded-xl border p-4 ${item.bg}`}>
                        <div className="flex items-center gap-2 mb-2">
                          <span className={`h-2.5 w-2.5 rounded-full ${item.color}`} />
                          <p className="text-label-sm font-medium text-on-surface">{item.label}</p>
                        </div>
                        <p className="font-headline-lg text-on-surface">{item.value}</p>
                        <p className="text-label-sm text-on-surface-variant mt-1">ca trực</p>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Workload progress */}
                {staff.maxShiftsPerMonth > 0 && (
                  <div>
                    <h4 className="font-title-md text-on-surface mb-3">Tải trọng tháng này</h4>
                    <div className="bg-surface-container-low rounded-xl p-5 border border-outline-variant">
                      <div className="flex justify-between items-center mb-2">
                        <span className="text-label-md text-on-surface">Số ca đã xếp / Tối đa</span>
                        <span className="text-label-md font-bold text-on-surface">
                          {stats.total} / {staff.maxShiftsPerMonth}
                        </span>
                      </div>
                      <div className="w-full bg-surface-variant rounded-full h-2.5 overflow-hidden">
                        <div
                          className={`h-2.5 rounded-full transition-all ${
                            stats.total > staff.maxShiftsPerMonth ? "bg-error" : "bg-primary"
                          }`}
                          style={{
                            width: `${Math.min(100, (stats.total / staff.maxShiftsPerMonth) * 100)}%`,
                          }}
                        />
                      </div>
                      {stats.total > staff.maxShiftsPerMonth && (
                        <p className="text-label-sm text-error mt-2 flex items-center gap-1">
                          <span className="material-symbols-outlined text-[14px]">warning</span>
                          Vượt quá giới hạn cho phép
                        </p>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Sidebar */}
        <div className="lg:col-span-4 flex flex-col gap-4">
          {/* Quick Actions */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm p-5">
            <h3 className="font-title-md text-on-surface mb-4 flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-[20px]">settings</span>
              Cài đặt
            </h3>
            <div className="space-y-2">
              {[
                { label: "Đổi mật khẩu", icon: "lock", href: "/settings?tab=password" },
                { label: "Cài đặt thông báo", icon: "notifications", href: "/settings?tab=notifications" },
                { label: "Xem lịch cá nhân", icon: "calendar_month", href: "/monthly-schedule" },
                { label: "Yêu cầu đổi ca", icon: "swap_horiz", href: "/swap-requests" },
              ].map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-body-sm text-on-surface hover:bg-surface-container-low transition-colors"
                >
                  <span className="material-symbols-outlined text-[20px] text-on-surface-variant">
                    {item.icon}
                  </span>
                  {item.label}
                </Link>
              ))}
            </div>
          </div>

          {/* Account Info Card */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm p-5">
            <h3 className="font-title-md text-on-surface mb-4 flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-[20px]">info</span>
              Thông tin tài khoản
            </h3>
            <div className="space-y-3">
              {[
                { label: "Mã nhân viên", value: staff.username },
                { label: "Chuyên khoa", value: staff.specialty?.name ?? "—" },
                { label: "Trạng thái", value: staff.isActive ? "Hoạt động" : "Không hoạt động" },
              ].map((item) => (
                <div key={item.label} className="flex justify-between items-center py-1">
                  <span className="text-label-sm text-on-surface-variant">{item.label}</span>
                  <span className="font-label-md text-on-surface">{item.value}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Recent Activity */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm p-5">
            <h3 className="font-title-md text-on-surface mb-4 flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-[20px]">history</span>
              Hoạt động gần đây
            </h3>
            {schedules.length > 0 ? (
              <div className="space-y-3">
                {schedules.slice(0, 3).map((s) => (
                  <div key={s.id} className="flex items-start gap-3">
                    <span className="material-symbols-outlined text-on-surface-variant text-[18px] mt-0.5">event</span>
                    <div className="min-w-0">
                      <p className="text-label-sm text-on-surface truncate">{s.shiftType.name}</p>
                      <p className="text-label-sm text-on-surface-variant">{formatDate(s.workDate)}</p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-label-sm text-on-surface-variant text-center py-4">Chưa có hoạt động nào.</p>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
