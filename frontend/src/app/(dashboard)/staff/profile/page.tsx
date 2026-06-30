"use client";
/* eslint-disable react-hooks/exhaustive-deps */

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { ROLE_LABELS } from "@/lib/roleLabels";
import type { Staff, Schedule, SchedulePeriod } from "@/types/api";
import { SHIFT_COLORS, SHIFT_TYPE_BADGES } from "@/lib/shift-colors";
import { BackButton } from "@/components/ui/BackButton";

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

type StatusKey = "ACTIVE" | "ON_LEAVE" | "INACTIVE";

const STATUS_LABEL: Record<StatusKey, string> = {
  ACTIVE: "Đang làm việc",
  ON_LEAVE: "Nghỉ phép",
  INACTIVE: "Nghỉ việc",
};

const STATUS_CLASS: Record<StatusKey, string> = {
  ACTIVE: "bg-secondary-container text-secondary",
  ON_LEAVE: "bg-tertiary-fixed text-on-tertiary-fixed-variant",
  INACTIVE: "bg-surface-container-high text-outline",
};

const STATUS_DOT: Record<StatusKey, string> = {
  ACTIVE: "bg-secondary",
  ON_LEAVE: "bg-tertiary",
  INACTIVE: "bg-outline",
};

function formatDate(dateStr: string): string {
  if (!dateStr) return "—";
  return new Date(dateStr).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

const SHIFT_ICON_MAP: Record<string, string> = {
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
        if (preferred) setSelectedPeriodId(preferred.id);
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
      if (!cancelledRef.current) { setSchedules([]); setMessage("Không thể tải lịch trực."); }
    } finally {
      if (!cancelledRef.current) setSchedulesLoading(false);
    }
  };

  useEffect(() => {
    if (!userId || !selectedPeriodId) return;
    cancelledRef.current = false;
    void loadSchedules();
    return () => { cancelledRef.current = true; };
  }, [userId, selectedPeriodId]);

  if (loading) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center gap-4 animate-pulse">
        <div className="size-20 rounded-full bg-surface-container" />
        <div className="space-y-2 text-center">
          <div className="h-6 w-32 bg-surface-container rounded-lg" />
          <div className="h-4 w-24 bg-surface-container rounded" />
        </div>
      </div>
    );
  }

  if (!staff) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center gap-4">
        <span className="material-symbols-outlined text-7xl text-outline">person_off</span>
        <p className="text-headline-md text-on-surface-variant">Không tìm thấy hồ sơ</p>
        <Link href="/login" className="px-5 py-2.5 bg-primary text-on-primary rounded-lg hover:bg-primary/90 transition-colors font-label-md">
          Đăng nhập lại
        </Link>
      </div>
    );
  }

  const workloadPercent = Math.min(100, (stats.total / (staff.maxShiftsPerMonth || 1)) * 100);
  const isOverload = stats.total > staff.maxShiftsPerMonth;

  return (
    <div className="space-y-6 animate-fade-in">
      {message && (
        <div className="rounded-xl border border-tertiary-container bg-tertiary-container/30 px-4 py-3 text-body-sm text-on-surface flex items-center gap-2">
          <span className="material-symbols-outlined text-[20px]">warning</span>
          {message}
        </div>
      )}

      <BackButton href="/dashboard" variant="full" />

      {/* Hero Section */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
        <div className="bg-gradient-to-r from-primary/5 to-primary/10 px-6 py-5 border-b border-outline-variant">
          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4">
            {/* Avatar */}
            <div className="relative shrink-0">
              <div className="size-16 rounded-full bg-primary text-on-primary flex items-center justify-center text-2xl font-bold shadow-md ring-4 ring-primary/20">
                {getInitials(staff.fullName)}
              </div>
              <span
                className={`absolute -bottom-0.5 -right-0.5 size-5 rounded-full border-2 border-surface-container-lowest ${
                  staff.isActive ? "bg-secondary" : "bg-outline"
                }`}
              />
            </div>

            {/* Info */}
            <div className="flex-1 text-center sm:text-left">
              <h1 className="text-headline-md font-semibold text-on-surface">{staff.fullName}</h1>
              <p className="text-label-md text-on-surface-variant mt-0.5">@{staff.username}</p>
              <div className="flex flex-wrap gap-2 mt-2 justify-center sm:justify-start">
                {staff.roles.map((role) => (
                  <span
                    key={role}
                    className="px-2.5 py-1 rounded-full text-label-sm font-semibold bg-primary/10 text-primary"
                  >
                    {ROLE_LABELS[role] ?? role}
                  </span>
                ))}
                <span
                  className={`mt-2 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold ${STATUS_CLASS[staff.status as StatusKey] ?? STATUS_CLASS.INACTIVE}`}
                >
                  <span className={`h-1.5 w-1.5 rounded-full ${STATUS_DOT[staff.status as StatusKey] ?? STATUS_DOT.INACTIVE}`} />
                  {STATUS_LABEL[staff.status as StatusKey] ?? "Nghỉ việc"}
                </span>
              </div>
            </div>

            {/* Edit button */}
            <button
              type="button"
              onClick={() => router.push(`/staff/${staff.id}/edit`)}
              className="shrink-0 flex items-center gap-1.5 px-4 py-2 bg-primary text-on-primary rounded-lg hover:bg-primary/90 transition-colors text-label-md font-medium shadow-sm"
            >
              <span className="material-symbols-outlined text-[16px]">edit</span>
              Chỉnh sửa
            </button>
          </div>
        </div>

        {/* Quick stats */}
        <div className="grid grid-cols-2 md:grid-cols-4 divide-x divide-y divide-outline-variant">
          {[
            { label: "Tổng ca trực", value: stats.total, icon: "event_available" },
            { label: "Xung đột", value: stats.conflicts, icon: "warning", danger: stats.conflicts > 0 },
            { label: "Loại lịch", value: [stats.L01, stats.L02, stats.L03, stats.L04].filter(Boolean).length, icon: "category" },
            { label: "Ca tối đa/tháng", value: staff.maxShiftsPerMonth, icon: "trending_up" },
          ].map((item) => (
            <div key={item.label} className="px-4 py-3 text-center hover:bg-surface-container-low transition-colors">
              <div className="flex items-center justify-center gap-1.5 mb-1">
                <span className="material-symbols-outlined text-[16px] text-primary">{item.icon}</span>
              </div>
              <p className={`text-xl font-bold ${item.danger ? "text-error" : "text-on-surface"}`}>{item.value}</p>
              <p className="text-label-xs text-on-surface-variant mt-0.5">{item.label}</p>
            </div>
          ))}
        </div>
      </section>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Main Content */}
        <div className="lg:col-span-8 flex flex-col gap-4">
          {/* Tabs */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
            {/* Tab Header */}
            <div className="flex border-b border-outline-variant bg-surface-container-low" role="tablist">
              {[
                { key: "info", label: "Thông tin", icon: "person" },
                { key: "schedule", label: "Lịch công tác", icon: "calendar_month" },
                { key: "stats", label: "Thống kê", icon: "bar_chart" },
              ].map((tab) => (
                <button
                  key={tab.key}
                  aria-selected={activeTab === tab.key}
                  className={`flex-1 flex items-center justify-center gap-2 py-3.5 text-label-md font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary ${
                    activeTab === tab.key
                      ? "bg-surface-container-lowest text-primary border-b-2 border-primary"
                      : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest/50"
                  }`}
                  onClick={() => setActiveTab(tab.key as typeof activeTab)}
                  role="tab"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">{tab.icon}</span>
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Tab Content */}
            <div className="p-5">
              {/* Info Tab */}
              {activeTab === "info" && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 animate-fade-in">
                  {/* Personal Info */}
                  <div className="bg-surface-container-low rounded-xl p-5 border border-outline-variant">
                    <h3 className="text-title-md font-semibold text-on-surface mb-4 flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary text-[20px]">person_outline</span>
                      Thông tin cá nhân
                    </h3>
                    <div className="space-y-4">
                      {[
                        { label: "Họ và tên", value: staff.fullName, icon: "badge" },
                        { label: "Tên đăng nhập", value: staff.username, icon: "account_circle" },
                        { label: "Email", value: staff.email || "—", icon: "mail" },
                        { label: "Số điện thoại", value: staff.phone || "—", icon: "phone" },
                      ].map((item) => (
                        <div key={item.label} className="flex items-start gap-3">
                          <div className="size-9 rounded-lg bg-primary-fixed flex items-center justify-center shrink-0">
                            <span className="material-symbols-outlined text-primary text-[18px]">{item.icon}</span>
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-label-sm text-on-surface-variant">{item.label}</p>
                            <p className="text-label-md text-on-surface font-medium truncate">{item.value}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Work Info */}
                  <div className="bg-surface-container-low rounded-xl p-5 border border-outline-variant">
                    <h3 className="text-title-md font-semibold text-on-surface mb-4 flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary text-[20px]">work_outline</span>
                      Thông tin công việc
                    </h3>
                    <div className="space-y-4">
                      {[
                        { label: "Mã nhân viên", value: staff.staffCode || staff.username, icon: "badge" },
                        { label: "Chuyên khoa", value: staff.specialty?.name || "Chưa phân khoa", icon: "local_hospital" },
                        { label: "Số ca tối đa", value: `${staff.maxShiftsPerMonth} ca/tháng`, icon: "event" },
                        { label: "Ngày tham gia", value: formatDate(staff.createdAt), icon: "calendar_today" },
                      ].map((item) => (
                        <div key={item.label} className="flex items-start gap-3">
                          <div className="size-9 rounded-lg bg-secondary-container flex items-center justify-center shrink-0">
                            <span className="material-symbols-outlined text-secondary text-[18px]">{item.icon}</span>
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-label-sm text-on-surface-variant">{item.label}</p>
                            <p className="text-label-md text-on-surface font-medium truncate">{item.value}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* Schedule Tab */}
              {activeTab === "schedule" && (
                <div className="space-y-4 animate-fade-in">
                  <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                    <h3 className="text-title-md font-semibold text-on-surface flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary text-[20px]">calendar_month</span>
                      Lịch công tác
                    </h3>
                    <div className="relative w-full sm:w-64">
                      <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">date_range</span>
                      <select
                        className="w-full pl-9 pr-8 py-2.5 bg-surface-container-low border border-outline-variant text-label-md text-on-surface rounded-lg focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/30 appearance-none cursor-pointer"
                        value={selectedPeriodId ?? ""}
                        onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
                      >
                        <option value="" disabled>Chọn kỳ lịch</option>
                        {periods.map((p) => (
                          <option key={p.id} value={p.id}>{p.periodName}</option>
                        ))}
                      </select>
                      <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
                    </div>
                  </div>

                  {schedulesLoading ? (
                    <div className="flex items-center justify-center py-16">
                      <div className="size-10 animate-spin rounded-full border-3 border-primary border-t-transparent" />
                    </div>
                  ) : schedules.length === 0 ? (
                    <div className="rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low p-10 text-center">
                      <span className="material-symbols-outlined text-6xl text-outline mb-4 block">event_busy</span>
                      <p className="text-title-md text-on-surface-variant mb-2">Chưa có lịch trực</p>
                      <p className="text-label-md text-on-surface-variant">Chưa có lịch được xếp cho kỳ này</p>
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left">
                        <thead>
                          <tr className="bg-surface-container-low border-b border-outline-variant">
                            <th className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Ngày</th>
                            <th className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Loại lịch</th>
                            <th className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Trạng thái</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-outline-variant">
                          {schedules.map((s) => {
                            const shiftId = s.shiftType.id as "L01" | "L02" | "L03" | "L04";
                            const badge = SHIFT_TYPE_BADGES[shiftId] || SHIFT_TYPE_BADGES.L01;
                            const icon = SHIFT_ICON_MAP[shiftId] || SHIFT_ICON_MAP.L01;
                            return (
                              <tr key={s.id} className="hover:bg-surface-container-low transition-colors border-l-4 border-l-transparent hover:border-l-primary">
                                <td className="px-4 py-3.5">
                                  <div className="flex items-center gap-2">
                                    <span className="material-symbols-outlined text-on-surface-variant text-[18px]">calendar_today</span>
                                    <span className="text-label-md text-on-surface">{formatDate(s.workDate)}</span>
                                  </div>
                                </td>
                                <td className="px-4 py-3.5">
                                  <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-label-sm font-medium border ${badge}`}>
                                    <span className="material-symbols-outlined text-[14px]">{icon}</span>
                                    {s.shiftType.name}
                                  </span>
                                </td>
                                <td className="px-4 py-3.5">
                                  <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-label-sm font-medium ${
                                    s.hasConflict
                                      ? "bg-error-container text-error"
                                      : "bg-secondary-container text-secondary"
                                  }`}>
                                    <span className={`size-2 rounded-full ${s.hasConflict ? "bg-error animate-pulse" : "bg-secondary"}`} />
                                    {s.hasConflict ? "Xung đột" : "Hợp lệ"}
                                  </span>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}

              {/* Stats Tab */}
              {activeTab === "stats" && (
                <div className="space-y-6 animate-fade-in">
                  <h3 className="text-title-md font-semibold text-on-surface flex items-center gap-2">
                    <span className="material-symbols-outlined text-primary text-[20px]">bar_chart</span>
                    Thống kê chi tiết
                  </h3>

                  {/* Shift type breakdown */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    {(["L01", "L02", "L03", "L04"] as const).map((id) => {
                      const colors = SHIFT_COLORS[id];
                      const value = stats[id];
                      return (
                        <div key={id} className={`rounded-xl border p-4 ${colors.bg}`}>
                          <div className="flex items-center gap-2 mb-3">
                            <span className={`size-3 rounded-full ${colors.dot}`} />
                            <p className={`text-label-sm font-medium ${colors.text}`}>{colors.label}</p>
                          </div>
                          <p className={`text-[32px] font-bold ${colors.text} leading-none`}>{value}</p>
                          <p className={`text-label-sm mt-1 ${colors.text} opacity-70`}>ca trực</p>
                        </div>
                      );
                    })}
                  </div>

                  {/* Workload Progress */}
                  <div className="bg-surface-container-low rounded-xl p-5 border border-outline-variant">
                    <div className="flex items-center justify-between mb-4">
                      <h4 className="text-title-sm font-semibold text-on-surface flex items-center gap-2">
                        <span className="material-symbols-outlined text-primary text-[20px]">speed</span>
                        Tải trọng tháng này
                      </h4>
                      <span className="text-label-md font-bold text-on-surface">
                        {stats.total} / {staff.maxShiftsPerMonth} ca
                      </span>
                    </div>
                    <div className="w-full bg-surface-container-highest rounded-full h-3 overflow-hidden">
                      <div
                        className={`h-3 rounded-full transition-all duration-500 ${
                          isOverload ? "bg-error" : workloadPercent > 80 ? "bg-amber-500" : "bg-primary"
                        }`}
                        style={{ width: `${workloadPercent}%` }}
                      />
                    </div>
                    <div className="flex justify-between mt-2 text-label-sm text-on-surface-variant">
                      <span>0</span>
                      <span>{staff.maxShiftsPerMonth} ca</span>
                    </div>
                    {isOverload && (
                      <div className="mt-3 flex items-center gap-2 text-error text-label-sm">
                        <span className="material-symbols-outlined text-[16px] animate-pulse">warning</span>
                        Đã vượt quá giới hạn cho phép
                      </div>
                    )}
                  </div>

                  {/* Conflict alert */}
                  {stats.conflicts > 0 && (
                    <div className="rounded-xl border border-error-container bg-error-container/30 p-5 flex items-start gap-4">
                      <div className="size-10 rounded-full bg-error-container flex items-center justify-center shrink-0">
                        <span className="material-symbols-outlined text-error text-[20px]">warning</span>
                      </div>
                      <div>
                        <h4 className="text-title-sm font-semibold text-error">Có {stats.conflicts} xung đột lịch</h4>
                        <p className="text-label-sm text-on-surface-variant mt-1">
                          Vui lòng liên hệ quản lý để được giải quyết
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Sidebar */}
        <div className="lg:col-span-4 flex flex-col gap-4">
          {/* Quick Actions */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-outline-variant bg-surface-container-low">
              <h3 className="text-title-sm font-semibold text-on-surface flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">apps</span>
                Thao tác nhanh
              </h3>
            </div>
            <div className="p-4 space-y-2">
              {[
                { label: "Xem lịch cá nhân", icon: "calendar_month", href: "/monthly-schedule", color: "text-primary bg-primary-fixed" },
                { label: "Yêu cầu đổi ca", icon: "swap_horiz", href: "/swap-requests", color: "text-secondary bg-secondary-container" },
                { label: "Đổi mật khẩu", icon: "lock", href: "/settings?tab=password", color: "text-on-surface-variant bg-surface-container-low" },
                { label: "Cài đặt thông báo", icon: "notifications", href: "/settings?tab=notifications", color: "text-on-surface-variant bg-surface-container-low" },
              ].map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-center gap-3 px-4 py-3 rounded-xl text-label-md font-medium text-on-surface hover:bg-surface-container-low transition-all group"
                >
                  <div className={`size-9 rounded-lg flex items-center justify-center shrink-0 ${item.color}`}>
                    <span className="material-symbols-outlined text-[18px]">{item.icon}</span>
                  </div>
                  {item.label}
                  <span className="material-symbols-outlined text-on-surface-variant ml-auto text-[18px] group-hover:translate-x-1 transition-transform">chevron_right</span>
                </Link>
              ))}
            </div>
          </div>

          {/* Account Summary */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-outline-variant bg-surface-container-low">
              <h3 className="text-title-sm font-semibold text-on-surface flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">info</span>
                Tóm tắt tài khoản
              </h3>
            </div>
            <div className="p-5 space-y-4">
              {[
                { label: "Vai trò", value: staff.roles.map((r) => ROLE_LABELS[r] ?? r).join(", "), icon: "shield" },
                { label: "Chuyên khoa", value: staff.specialty?.name || "—", icon: "local_hospital" },
                { label: "Trạng thái", value: STATUS_LABEL[staff.status as StatusKey] ?? "Nghỉ việc", icon: "verified_user" },
              ].map((item) => (
                <div key={item.label} className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
                    <span className="material-symbols-outlined text-[16px]">{item.icon}</span>
                    {item.label}
                  </div>
                  <span className={`text-label-sm font-medium text-on-surface`}>
                    {item.value}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* Recent Activity */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-outline-variant bg-surface-container-low">
              <h3 className="text-title-sm font-semibold text-on-surface flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">history</span>
                Hoạt động gần đây
              </h3>
            </div>
            <div className="p-5">
              {schedules.length > 0 ? (
                <div className="space-y-3">
                  {schedules.slice(0, 5).map((s, idx) => {
                    const shiftId = s.shiftType.id as "L01" | "L02" | "L03" | "L04";
                    const badge = SHIFT_TYPE_BADGES[shiftId] || SHIFT_TYPE_BADGES.L01;
                    const icon = SHIFT_ICON_MAP[shiftId] || SHIFT_ICON_MAP.L01;
                    return (
                      <div key={s.id} className="flex items-center gap-3">
                        <div className={`size-8 rounded-lg flex items-center justify-center shrink-0 border ${badge}`}>
                          <span className="material-symbols-outlined text-[14px]">{icon}</span>
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-label-sm font-medium text-on-surface truncate">{s.shiftType.name}</p>
                          <p className="text-label-sm text-on-surface-variant">{formatDate(s.workDate)}</p>
                        </div>
                        {idx === 0 && (
                          <span className="px-2 py-0.5 rounded-full bg-primary-fixed text-primary text-[10px] font-bold">Mới</span>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="text-center py-6">
                  <span className="material-symbols-outlined text-4xl text-outline mb-2 block">event_busy</span>
                  <p className="text-label-sm text-on-surface-variant">Chưa có lịch trực</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
