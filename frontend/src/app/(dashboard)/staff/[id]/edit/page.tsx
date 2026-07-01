"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import { ROLE_LABELS } from "@/lib/roleLabels";
import { useRole, canViewAuditLog } from "@/hooks/useRole";
import type { Staff, Specialty, AuditHistory } from "@/types/api";
import { Skeleton } from "@/components/ui/Skeleton";
import { FormInput, FormSelect } from "@/components/ui";

type StaffStatus = "ACTIVE" | "ON_LEAVE" | "INACTIVE";

type StaffFormData = {
  username: string;
  fullName: string;
  password: string;
  phone: string;
  email: string;
  position: string;
  specialtyId: number | null;
  maxShiftsPerMonth: number;
  status: StaffStatus;
  roles: string[];
};

const emptyForm: StaffFormData = {
  username: "",
  fullName: "",
  password: "",
  phone: "",
  email: "",
  position: "",
  specialtyId: null,
  maxShiftsPerMonth: 5,
  status: "ACTIVE",
  roles: [],
};

// ─── Audit helpers ───────────────────────────────────────────────────────────

function fmtTime(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
  } catch { return dateStr; }
}

function fmtDateShort(dateStr: string) {
  try {
    const VI_DAY_SHORT = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
    const d = new Date(dateStr + "T12:00:00");
    return `${VI_DAY_SHORT[d.getDay()]}, ${d.toLocaleDateString("vi-VN")}`;
  } catch { return dateStr; }
}

const ACTION_STYLE: Record<string, { label: string; icon: string; iconBg: string; chipColor: string }> = {
  CREATE: { label: "Tạo mới", icon: "add_circle", iconBg: "bg-secondary-container text-secondary", chipColor: "text-secondary" },
  UPDATE: { label: "Cập nhật", icon: "edit",       iconBg: "bg-primary-fixed text-primary",   chipColor: "text-primary" },
  DELETE: { label: "Xóa",      icon: "delete",     iconBg: "bg-error-container text-error",  chipColor: "text-error"  },
};

function getActionStyle(action: string) {
  return ACTION_STYLE[action] ?? { label: action, icon: "info", iconBg: "bg-surface-container-high text-on-surface-variant", chipColor: "text-on-surface-variant" };
}

// ─── Tab definitions ───────────────────────────────────────────────────────────

type TabId = "info" | "audit";

const TABS_EDIT: { id: TabId; label: string; icon: string }[] = [
  { id: "info",   label: "Thông tin",           icon: "person" },
  { id: "audit",  label: "Lịch sử thay đổi",   icon: "history" },
];

// ─── Audit panel ─────────────────────────────────────────────────────────────

function AuditList({ staffId }: { staffId: number }) {
  const [records, setRecords] = useState<AuditHistory[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const res = await api.get<AuditHistory[]>(
          `/audit-history/table/staff/record/${staffId}`
        );
        setRecords(res ?? []);
      } catch { setRecords([]); }
      finally { setLoading(false); }
    })();
  }, [staffId]);

  if (loading) {
    return (
      <div className="divide-y divide-outline-variant">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="flex items-start gap-3 px-4 py-3">
            <Skeleton className="h-7 w-7 rounded-lg shrink-0" />
            <div className="flex flex-col min-w-0 flex-1 gap-2">
              <Skeleton className="h-3 w-48 rounded" />
              <Skeleton className="h-3 w-32 rounded" />
            </div>
            <Skeleton className="h-3 w-10 rounded shrink-0" />
          </div>
        ))}
      </div>
    );
  }

  if (records.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-3">
        <span className="material-symbols-outlined text-4xl text-outline">history</span>
        <p className="text-body-sm text-on-surface-variant">Chưa có thay đổi nào được ghi nhận.</p>
      </div>
    );
  }

  return (
    <div className="divide-y divide-outline-variant">
      {records.map((r) => {
        const st = getActionStyle(r.action);
        const userDisplay = r.userName ?? (r.userId != null && r.userId > 0 ? `#${r.userId}` : null);
        return (
          <div
            key={r.id}
            className="flex items-start gap-3 px-4 py-3 hover:bg-surface-container-low transition-colors"
          >
            <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${st.iconBg}`}>
              <span className="material-symbols-outlined text-[14px]">{st.icon}</span>
            </div>
            <div className="flex flex-col min-w-0 flex-1 gap-1">
              <div className="flex items-center gap-2 flex-wrap">
                <span className={`text-[12px] font-bold shrink-0 ${st.chipColor}`}>{st.label}</span>
                <span className="text-[12px] text-on-surface font-semibold shrink-0">Staff</span>
                <span className="text-[11px] text-on-surface-variant shrink-0">#{r.recordId}</span>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                {userDisplay && (
                  <span className="text-[12px] text-on-surface-variant shrink-0">{userDisplay}</span>
                )}
                {r.ipAddress && (
                  <span className="text-[11px] text-outline shrink-0">· {r.ipAddress}</span>
                )}
              </div>
            </div>
            <div className="flex flex-col items-end gap-0.5 shrink-0">
              <span className="text-[12px] text-on-surface-variant tabular-nums">
                {fmtTime(r.createdAt)}
              </span>
              <span className="text-[11px] text-outline">
                {fmtDateShort(r.createdAt.split("T")[0])}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ─── Main edit page ────────────────────────────────────────────────────────────

export default function StaffEditPage() {
  return <StaffEditContent />;
}

function StaffEditContent() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const staffId = Number(params.id);
  const role = useRole();
  const canSeeAudit = canViewAuditLog(role);

  const [staff, setStaff] = useState<Staff | null>(null);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [form, setForm] = useState<StaffFormData>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabId>("info");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string | undefined>>({});
  const toast = useToast();

  const fetchData = useCallback(async () => {
    if (isNaN(staffId)) {
      setMessage("ID nhân sự không hợp lệ.");
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const [staffRes, specRes] = await Promise.allSettled([
        api.get<Staff>(`/staff/${staffId}`),
        api.get<Specialty[]>("/specialties"),
      ]);

      if (staffRes.status === "fulfilled") {
        const s = staffRes.value;
        setStaff(s);
        const rawStatus = s.status?.toUpperCase() ?? "";
        setForm({
          username: s.username,
          fullName: s.fullName,
          password: "",
          phone: s.phone ?? "",
          email: s.email ?? "",
          position: s.position ?? "",
          specialtyId: s.specialty?.id ?? null,
          maxShiftsPerMonth: s.maxShiftsPerMonth,
          status: (rawStatus === "ACTIVE" || rawStatus === "ON_LEAVE" || rawStatus === "INACTIVE"
            ? rawStatus
            : s.isActive ? "ACTIVE" : "INACTIVE") as StaffStatus,
          roles: s.roles ?? [],
        });
      } else {
        setMessage(getErrorMessage(staffRes.reason, "Không thể tải thông tin nhân sự."));
      }

      if (specRes.status === "fulfilled") {
        setSpecialties(specRes.value ?? []);
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

  function updateField(field: keyof StaffFormData, value: string | number | null) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function validate(): boolean {
    const errors: Record<string, string | undefined> = {};
    if (!form.username.trim()) {
      errors.username = "Tên đăng nhập không được để trống.";
    }
    if (!form.fullName.trim()) {
      errors.fullName = "Họ tên không được để trống.";
    }
    if (form.password.trim() && form.password.trim().length < 6) {
      errors.password = "Mật khẩu phải có ít nhất 6 ký tự.";
    }
    if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      errors.email = "Email không hợp lệ.";
    }
    if (form.phone.trim() && !/^[0-9+\-\s]{9,15}$/.test(form.phone.trim().replace(/\s/g, ""))) {
      errors.phone = "Số điện thoại không hợp lệ.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!validate()) return;

    try {
      setSubmitting(true);
      const body: Record<string, unknown> = {
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        position: form.position.trim() || null,
        specialtyId: form.specialtyId,
        maxShiftsPerMonth: form.maxShiftsPerMonth,
        status: form.status,
        roles: form.roles,
      };
      if (form.password.trim()) {
        body.password = form.password.trim();
      }
      await api.put(`/staff/${staffId}`, body);
      toast.success("Đã cập nhật thông tin nhân sự.");
      setTimeout(() => {
        router.push(`/staff/${staffId}`);
      }, 1200);
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi lưu nhân sự."));
    } finally {
      setSubmitting(false);
    }
  }

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
          className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90"
        >
          Quay lại danh sách
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <nav aria-label="Đường dẫn" className="flex items-center gap-2 text-label-md text-on-surface-variant">
        <Link href="/staff" className="hover:text-primary transition-colors flex items-center gap-1">
          <span className="material-symbols-outlined text-[18px]">groups</span>
          Nhân sự
        </Link>
        <span className="material-symbols-outlined text-[16px]">chevron_right</span>
        <Link href={`/staff/${staffId}`} className="hover:text-primary transition-colors">
          {staff.fullName}
        </Link>
        <span className="material-symbols-outlined text-[16px]">chevron_right</span>
        <span className="text-on-surface font-medium">Chỉnh sửa</span>
      </nav>

      {/* Page Header */}
      <section className="flex items-center gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary-container">
          <span className="material-symbols-outlined text-[24px] text-primary">edit</span>
        </div>
        <div>
          <h1 className="text-headline-md font-semibold text-on-surface">Chỉnh sửa hồ sơ</h1>
          <p className="text-label-md text-on-surface-variant mt-0.5">
            Cập nhật thông tin nhân viên \u2013 mật khẩu chỉ thay đổi khi nhập mới.
          </p>
        </div>
      </section>

      {/* Tab bar */}
      <div
        role="tablist"
        aria-label="Hồ sơ nhân sự"
        className="border-b border-outline-variant flex overflow-x-auto bg-surface-container-low rounded-xl border border-outline-variant px-4"
      >
        {TABS_EDIT.map((tab) => {
          if (tab.id === "audit" && !canSeeAudit) return null;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              role="tab"
              type="button"
              aria-selected={isActive}
              aria-controls={`tabpanel-${tab.id}`}
              id={`tab-${tab.id}`}
              className={`flex items-center gap-2 px-4 py-3 font-label-md text-label-md border-b-2 whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                isActive
                  ? "text-primary border-primary"
                  : "text-on-surface-variant border-transparent hover:text-on-surface hover:bg-surface-container-high"
              }`}
              onClick={() => setActiveTab(tab.id)}
            >
              <span className="material-symbols-outlined text-[18px]">{tab.icon}</span>
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* "Thông tin" tab */}
      {activeTab === "info" && (
        <form
          id="staff-edit-form"
          onSubmit={handleSubmit}
          noValidate
          className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden"
        >
          <div className="px-6 py-5">
            {/* Account Info */}
            <div className="mb-6">
              <h2 className="text-title-md font-semibold text-on-surface flex items-center gap-2 mb-4">
                <span className="material-symbols-outlined text-primary text-[20px]">manage_accounts</span>
                Thông tin tài khoản
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <FormInput
                  label="Tên đăng nhập"
                  name="username"
                  autoComplete="username"
                  value={form.username}
                  disabled
                  required
                  icon="account_circle"
                  hint="Username không thể thay đổi."
                />

                <FormInput
                  label="Mật khẩu mới"
                  name="password"
                  autoComplete="new-password"
                  placeholder="Bỏ trống = giữ nguyên"
                  type="password"
                  value={form.password}
                  onChange={(e) => {
                    updateField("password", e.target.value);
                    if (fieldErrors.password) setFieldErrors((f) => ({ ...f, password: undefined }));
                  }}
                  error={fieldErrors.password}
                  disabled={submitting}
                  icon="lock"
                />
              </div>
            </div>

            {/* Personal Info */}
            <div className="mb-6">
              <h2 className="text-title-md font-semibold text-on-surface flex items-center gap-2 mb-4">
                <span className="material-symbols-outlined text-primary text-[20px]">badge</span>
                Thông tin cá nhân
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <FormInput
                  label="Họ tên"
                  name="fullName"
                  autoComplete="name"
                  placeholder="VD: Nguyễn Văn A"
                  value={form.fullName}
                  onChange={(e) => {
                    updateField("fullName", e.target.value);
                    if (fieldErrors.fullName) setFieldErrors((f) => ({ ...f, fullName: undefined }));
                  }}
                  error={fieldErrors.fullName}
                  required
                  disabled={submitting}
                  icon="badge"
                />

                <FormInput
                  label="Chức vụ"
                  name="position"
                  placeholder="VD: Bác sĩ, Điều dưỡng"
                  value={form.position}
                  onChange={(e) => updateField("position", e.target.value)}
                  disabled={submitting}
                  icon="work"
                />

                <FormInput
                  label="Email"
                  name="email"
                  autoComplete="email"
                  type="email"
                  placeholder="VD: abc@hospital.vn"
                  value={form.email}
                  onChange={(e) => {
                    updateField("email", e.target.value);
                    if (fieldErrors.email) setFieldErrors((f) => ({ ...f, email: undefined }));
                  }}
                  error={fieldErrors.email}
                  disabled={submitting}
                  icon="mail"
                />

                <FormInput
                  label="Số điện thoại"
                  name="phone"
                  autoComplete="tel"
                  type="tel"
                  inputMode="tel"
                  placeholder="VD: 0901234567"
                  value={form.phone}
                  onChange={(e) => {
                    updateField("phone", e.target.value);
                    if (fieldErrors.phone) setFieldErrors((f) => ({ ...f, phone: undefined }));
                  }}
                  error={fieldErrors.phone}
                  disabled={submitting}
                  icon="phone"
                />
              </div>
            </div>

            {/* Work Info */}
            <div>
              <h2 className="text-title-md font-semibold text-on-surface flex items-center gap-2 mb-4">
                <span className="material-symbols-outlined text-primary text-[20px]">work_outline</span>
                Phân công &amp; trạng thái
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <FormSelect
                  label="Chuyên khoa"
                  value={String(form.specialtyId ?? "")}
                  onChange={(e) => updateField("specialtyId", e.target.value ? parseInt(e.target.value) : null)}
                  options={specialties.map((s) => ({ value: String(s.id), label: s.name }))}
                  placeholder="Chưa phân khoa"
                  disabled={submitting}
                />

                <FormInput
                  label="Số ca tối đa / tháng"
                  name="maxShiftsPerMonth"
                  type="number"
                  min={1}
                  max={31}
                  value={String(form.maxShiftsPerMonth)}
                  onChange={(e) => updateField("maxShiftsPerMonth", parseInt(e.target.value) || 5)}
                  disabled={submitting}
                  icon="event"
                />

                <div className="space-y-1.5 md:col-span-2">
                  <label className="text-body-sm font-semibold text-on-surface">Vai trò</label>
                  <div className="flex flex-wrap gap-2">
                    {([
                      { value: "ADMIN", label: ROLE_LABELS.ADMIN },
                      { value: "MANAGER", label: ROLE_LABELS.MANAGER },
                      { value: "STAFF", label: ROLE_LABELS.STAFF },
                    ] as const).map((role) => {
                      const checked = form.roles.includes(role.value);
                      return (
                        <label
                          key={role.value}
                          className={`flex items-center gap-2 px-3 py-2 rounded-lg border cursor-pointer transition-all text-label-md ${
                            checked
                              ? "bg-primary/10 border-primary text-primary font-medium"
                              : "bg-surface-container-low border-outline-variant text-on-surface-variant hover:border-primary/50"
                          }`}
                        >
                          <input
                            type="checkbox"
                            className="sr-only"
                            checked={checked}
                            onChange={(e) => {
                              setForm((f) => ({
                                ...f,
                                roles: e.target.checked
                                  ? [...f.roles, role.value]
                                  : f.roles.filter((r) => r !== role.value),
                              }));
                            }}
                            disabled={submitting}
                          />
                          <span
                            className={`material-symbols-outlined text-[16px] transition-colors ${
                              checked ? "text-primary" : "text-outline"
                            }`}
                            aria-hidden="true"
                          >
                            {checked ? "check_box" : "check_box_outline_blank"}
                          </span>
                          {role.label}
                        </label>
                      );
                    })}
                  </div>
                  {form.roles.length === 0 && (
                    <p className="text-label-xs text-error flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">error</span>
                      Vui lòng chọn ít nhất một vai trò.
                    </p>
                  )}
                </div>

                <FormSelect
                  label="Trạng thái"
                  value={form.status}
                  onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as StaffStatus }))}
                  options={[
                    { value: "ACTIVE",   label: "Đang làm việc" },
                    { value: "ON_LEAVE", label: "Nghỉ phép" },
                    { value: "INACTIVE", label: "Dừng hoạt động" },
                  ]}
                  disabled={submitting}
                />
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant bg-surface">
            <Link
              href={`/staff/${staffId}`}
              className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-[14px] font-semibold text-on-surface transition-colors hover:bg-surface-container-low text-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            >
              Hủy bỏ
            </Link>
            <button
              className="flex-1 flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-[14px] font-semibold text-on-primary shadow-sm transition-colors hover:brightness-110 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
              disabled={submitting}
              form="staff-edit-form"
              type="submit"
            >
              {submitting ? (
                <>
                  <div className="size-4 animate-spin rounded-full border-2 border-on-primary border-t-transparent" />
                  Đang lưu...
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]" aria-hidden="true">save</span>
                  Lưu thay đổi
                </>
              )}
            </button>
          </div>
        </form>
      )}

      {/* "Lịch sử thay đổi" tab */}
      {canSeeAudit && activeTab === "audit" && (
        <div className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-outline-variant bg-surface">
            <h2 className="text-title-md font-semibold text-on-surface flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-[20px]">history</span>
              Lịch sử thay đổi
            </h2>
          </div>
          <AuditList staffId={staffId} />
        </div>
      )}
    </div>
  );
}
