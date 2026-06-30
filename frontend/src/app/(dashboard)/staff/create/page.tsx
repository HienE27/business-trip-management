"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import { ROLE_LABELS } from "@/lib/roleLabels";
import { FormInput, FormSelect } from "@/components/ui";
import type { Specialty } from "@/types/api";

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

export default function StaffCreatePage() {
  return <StaffCreateContent />;
}

function StaffCreateContent() {
  const router = useRouter();
  const toast = useToast();

  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [form, setForm] = useState<StaffFormData>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string | undefined>>({});

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      const specRes = await api.get<Specialty[]>("/specialties");
      setSpecialties(specRes ?? []);
    } catch {
      toast.error("Không thể tải dữ liệu chuyên khoa.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

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
    } else if (form.username.trim().length < 3) {
      errors.username = "Tên đăng nhập phải có ít nhất 3 ký tự.";
    }
    if (!form.fullName.trim()) {
      errors.fullName = "Họ tên không được để trống.";
    }
    if (!form.password.trim()) {
      errors.password = "Mật khẩu không được để trống.";
    } else if (form.password.trim().length < 6) {
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
      await api.post("/staff", {
        username: form.username.trim(),
        fullName: form.fullName.trim(),
        password: form.password.trim(),
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        position: form.position.trim() || null,
        specialtyId: form.specialtyId,
        maxShiftsPerMonth: form.maxShiftsPerMonth,
        status: form.status,
        roles: form.roles,
      });
      toast.success("Đã tạo nhân sự thành công.");
      setTimeout(() => {
        router.push("/staff");
      }, 1200);
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi tạo nhân sự."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <nav aria-label="Đường dẫn" className="flex items-center gap-2 text-label-md text-on-surface-variant">
        <Link href="/staff" className="hover:text-primary transition-colors flex items-center gap-1">
          <span className="material-symbols-outlined text-[18px]">groups</span>
          Nhân sự
        </Link>
        <span className="material-symbols-outlined text-[16px]">chevron_right</span>
        <span className="text-on-surface font-medium">Thêm nhân sự</span>
      </nav>

      {loading ? (
        <div className="flex items-center justify-center py-24">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : (
        <>
          {/* Page Header */}
          <section className="flex items-center gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary-container">
              <span className="material-symbols-outlined text-[24px] text-primary">person_add</span>
            </div>
            <div>
              <h1 className="text-headline-md font-semibold text-on-surface">Thêm nhân sự mới</h1>
              <p className="text-label-md text-on-surface-variant mt-0.5">
                Nhập thông tin để tạo tài khoản nhân sự mới trong hệ thống.
              </p>
            </div>
          </section>

          <form className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden" id="staff-create-form" onSubmit={handleSubmit} noValidate>
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
                    placeholder="Nhập tên đăng nhập"
                    value={form.username}
                    onChange={(e) => {
                      updateField("username", e.target.value);
                      if (fieldErrors.username) setFieldErrors((f) => ({ ...f, username: undefined }));
                    }}
                    error={fieldErrors.username}
                    required
                    disabled={submitting}
                    icon="account_circle"
                  />

                  <FormInput
                    label="Mật khẩu"
                    name="password"
                    autoComplete="new-password"
                    placeholder="Nhập mật khẩu (tối thiểu 6 ký tự)"
                    type="password"
                    value={form.password}
                    onChange={(e) => {
                      updateField("password", e.target.value);
                      if (fieldErrors.password) setFieldErrors((f) => ({ ...f, password: undefined }));
                    }}
                    error={fieldErrors.password}
                    required
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
                      { value: "ACTIVE", label: "Đang làm việc" },
                      { value: "ON_LEAVE", label: "Nghỉ phép" },
                      { value: "INACTIVE", label: "Nghỉ việc" },
                    ]}
                    disabled={submitting}
                  />
                </div>
              </div>
            </div>

            <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant bg-surface">
              <Link
                href="/staff"
                className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-[14px] font-semibold text-on-surface transition-colors hover:bg-surface-container-low text-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
              >
                Hủy bỏ
              </Link>
              <button
                className="flex-1 flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-[14px] font-semibold text-on-primary shadow-sm transition-colors hover:brightness-110 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
                disabled={submitting}
                form="staff-create-form"
                type="submit"
              >
                {submitting ? (
                  <>
                    <div className="size-4 animate-spin rounded-full border-2 border-on-primary border-t-transparent" />
                    Đang tạo...
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[18px]" aria-hidden="true">save</span>
                    Tạo nhân sự
                  </>
                )}
              </button>
            </div>
          </form>
        </>
      )}
    </div>
  );
}
