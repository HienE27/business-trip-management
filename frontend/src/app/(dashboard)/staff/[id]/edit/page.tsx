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
import { BackButton } from "@/components/ui/BackButton";

type StaffFormData = {
  username: string;
  fullName: string;
  password: string;
  phone: string;
  email: string;
  position: string;
  specialtyId: number | null;
  maxShiftsPerMonth: number;
  isActive: boolean;
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
  isActive: true,
  roles: [],
};

// ─── Audit helpers (same pattern as /audit-history page) ───────────────────────

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

const TABS_EDIT: { id: TabId; label: string }[] = [
  { id: "info",  label: "Thông tin" },
  { id: "audit", label: "Lịch sử thay đổi" },
];

// ─── Audit panel (inline, same query as /audit-history/table/{tableName}/{recordId}) ──

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
        const userDisplay = r.userName ?? (r.userId > 0 ? `#${r.userId}` : null);
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
        setForm({
          username: s.username,
          fullName: s.fullName,
          password: "",
          phone: s.phone ?? "",
          email: s.email ?? "",
          position: s.position ?? "",
          specialtyId: s.specialty?.id ?? null,
          maxShiftsPerMonth: s.maxShiftsPerMonth,
          isActive: s.isActive,
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

  function updateField(field: keyof StaffFormData, value: string | number | boolean | null | string[]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.fullName.trim() || !form.username.trim()) {
      toast.error("Cần nhập họ tên và tên đăng nhập.");
      return;
    }

    try {
      setSubmitting(true);
      const body: Record<string, unknown> = {
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        position: form.position.trim() || null,
        specialtyId: form.specialtyId,
        maxShiftsPerMonth: form.maxShiftsPerMonth,
        isActive: form.isActive,
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

  return (
    <>
      <BackButton href="/staff" variant="full" label="Quay lại" className="mb-4" />

      {loading ? (
        <div className="flex items-center justify-center py-24">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : !staff ? (
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
      ) : (
        <section className="max-w-2xl mx-auto">
          <article className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
            {/* ── Tab bar ── */}
            <div
              role="tablist"
              aria-label="Hồ sơ nhân sự"
              className="border-b border-outline-variant flex overflow-x-auto bg-surface-container-low px-4"
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
                    className={`px-4 py-3 font-label-md text-label-md border-b-2 whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                      isActive
                        ? "text-primary border-primary"
                        : "text-on-surface-variant border-transparent hover:text-on-surface hover:bg-surface-container-high"
                    }`}
                    onClick={() => setActiveTab(tab.id)}
                  >
                    {tab.label}
                  </button>
                );
              })}
            </div>

            {/* ── Tab panels (preserve form state via display:none) ── */}

            {/* "Thông tin" tab — edit form */}
            <div
              id="tabpanel-info"
              role="tabpanel"
              aria-labelledby="tab-info"
              hidden={activeTab !== "info"}
              className={activeTab === "info" ? undefined : "hidden"}
            >
              <div className="flex items-center gap-3 px-6 py-4 border-b border-outline-variant bg-surface">
                <span className="material-symbols-outlined text-[22px] text-primary">edit</span>
                <div>
                  <h2 className="text-[18px] font-semibold text-on-surface">Chỉnh sửa hồ sơ</h2>
                  <p className="text-[12px] text-on-surface-variant">
                    Cập nhật thông tin nhân viên \u2013 mật khẩu chỉ thay đổi khi nhập mới.
                  </p>
                </div>
              </div>

              <form className="p-6 space-y-6" id="staff-edit-form" onSubmit={handleSubmit}>
              <div className="grid grid-cols-2 gap-4">
                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-[13px] font-semibold text-on-surface">Username <span className="text-error">*</span></span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="username"
                    value={form.username}
                    disabled
                  />
                  <p className="text-[11px] text-outline">Username không thể thay đổi.</p>
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-[13px] font-semibold text-on-surface">Họ tên <span className="text-error">*</span></span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="fullName"
                    onChange={(e) => updateField("fullName", e.target.value)}
                    required
                    value={form.fullName}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">Mật khẩu mới</span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="password"
                    onChange={(e) => updateField("password", e.target.value)}
                    placeholder="Bỏ trống = giữ nguyên"
                    type="password"
                    value={form.password}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">Chức vụ</span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="position"
                    onChange={(e) => updateField("position", e.target.value)}
                    placeholder="VD: Bác sĩ, Điều dưỡng"
                    type="text"
                    value={form.position}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">Email</span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="email"
                    onChange={(e) => updateField("email", e.target.value)}
                    type="email"
                    value={form.email}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">Số điện thoại</span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="phone"
                    onChange={(e) => updateField("phone", e.target.value)}
                    type="tel"
                    value={form.phone}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">Số ca tối đa / tháng</span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    max={31}
                    min={1}
                    name="maxShiftsPerMonth"
                    onChange={(e) => updateField("maxShiftsPerMonth", parseInt(e.target.value) || 5)}
                    type="number"
                    value={form.maxShiftsPerMonth}
                  />
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-[13px] font-semibold text-on-surface">Chuyên khoa</span>
                  <div className="relative">
                    <label htmlFor="edit-staff-specialty" className="sr-only">Chuyên khoa</label>
                    <select
                      id="edit-staff-specialty"
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 cursor-pointer"
                      name="specialty"
                      onChange={(e) => updateField("specialtyId", e.target.value ? parseInt(e.target.value) : null)}
                      value={form.specialtyId ?? ""}
                    >
                      <option value="">Chưa phân khoa</option>
                      {specialties.map((spec) => (
                        <option key={spec.id} value={spec.id}>{spec.name}</option>
                      ))}
                    </select>
                    <span aria-hidden="true" className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
                  </div>
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-[13px] font-semibold text-on-surface">Vai trò</span>
                  <div className="relative">
                    <label htmlFor="edit-staff-role" className="sr-only">Vai trò</label>
                    <select
                      id="edit-staff-role"
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 cursor-pointer"
                      name="role"
                      onChange={(e) =>
                        updateField(
                          "roles",
                          e.target.value ? [e.target.value] : []
                        )
                      }
                      value={form.roles[0] ?? ""}
                    >
                    <option value="">Chưa phân quyền</option>
                    <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
                    <option value="MANAGER">{ROLE_LABELS.MANAGER}</option>
                    <option value="STAFF">{ROLE_LABELS.STAFF}</option>
                    </select>
                    <span aria-hidden="true" className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
                  </div>
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-[13px] font-semibold text-on-surface">Trạng thái hoạt động</span>
                  <div className="flex items-center gap-3">
                    <button
                      aria-checked={form.isActive}
                      aria-label="Trạng thái hoạt động"
                      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 cursor-pointer ${form.isActive ? "bg-secondary" : "bg-surface-variant"}`}
                      role="switch"
                      type="button"
                      onClick={() => updateField("isActive", !form.isActive)}
                      onKeyDown={(e) => {
                        if (e.key === " " || e.key === "Enter") {
                          e.preventDefault();
                          updateField("isActive", !form.isActive);
                        }
                      }}
                    >
                      <span
                        className={`inline-block h-4 w-4 transform rounded-full bg-[var(--color-surface-container-lowest)] shadow transition-transform ${form.isActive ? "translate-x-6" : "translate-x-1"}`}
                      />
                    </button>
                    <span className="text-[13px] font-medium text-on-surface">
                      {form.isActive ? "Đang hoạt động" : "Đã dừng hoạt động"}
                    </span>
                  </div>
                </label>
              </div>
            </form>

            <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant bg-surface">
              <button
                className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-[14px] font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
                onClick={() => router.push(`/staff/${staffId}`)}
                type="button"
              >
                Hủy bỏ
              </button>
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
                    <span className="material-symbols-outlined text-[18px]">save</span>
                    Lưu thay đổi
                  </>
                )}
              </button>
            </div>
            </div>

            {/* "Lịch sử thay đổi" tab */}
            {canSeeAudit && (
              <div
                id="tabpanel-audit"
                role="tabpanel"
                aria-labelledby="tab-audit"
                className={activeTab === "audit" ? undefined : "hidden"}
              >
                <div className="flex items-center gap-3 px-6 py-4 border-b border-outline-variant bg-surface">
                  <span className="material-symbols-outlined text-[22px] text-primary">history</span>
                  <div>
                    <h2 className="text-[18px] font-semibold text-on-surface">Lịch sử thay đổi</h2>
                    <p className="text-[12px] text-on-surface-variant">
                      Nhật ký chỉnh sửa của nhân viên \u2013 cập nhật mỗi khi lưu thay đổi.
                    </p>
                  </div>
                </div>
                <AuditList staffId={staffId} />
              </div>
            )}
          </article>
        </section>
      )}
    </>
  );
}
