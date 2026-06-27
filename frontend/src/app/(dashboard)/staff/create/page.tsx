"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import { ROLE_LABELS } from "@/lib/roleLabels";
import { BackButton } from "@/components/ui/BackButton";
import type { Specialty } from "@/types/api";

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

  function updateField(field: keyof StaffFormData, value: string | number | boolean | null | string[]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.username.trim() || !form.fullName.trim()) {
      toast.error("Cần nhập tên đăng nhập và họ tên.");
      return;
    }

    if (!form.password.trim()) {
      toast.error("Cần nhập mật khẩu.");
      return;
    }

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
        isActive: form.isActive,
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
    <>
      <BackButton href="/staff" variant="full" label="Quay lại" className="mb-4" />

      {loading ? (
        <div className="flex items-center justify-center py-24">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : (
        <section className="max-w-2xl mx-auto">
          <article className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
            <div className="flex items-center gap-3 px-6 py-4 border-b border-outline-variant bg-surface">
              <span className="material-symbols-outlined text-[22px] text-primary">person_add</span>
              <div>
                <h2 className="text-[18px] font-semibold text-on-surface">Thông tin nhân sự</h2>
                <p className="text-[12px] text-on-surface-variant">
                  Nhập thông tin để tạo tài khoản nhân sự mới trong hệ thống.
                </p>
              </div>
            </div>

            <form className="p-6 space-y-6" id="staff-create-form" onSubmit={handleSubmit}>
              <div className="grid grid-cols-2 gap-4">
                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-[13px] font-semibold text-on-surface">Tên đăng nhập <span className="text-error">*</span></span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="username"
                    onChange={(e) => updateField("username", e.target.value)}
                    required
                    value={form.username}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">Mật khẩu <span className="text-error">*</span></span>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="password"
                    onChange={(e) => updateField("password", e.target.value)}
                    placeholder="Nhập mật khẩu"
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

                <label className="flex flex-col gap-1.5 col-span-2">
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
                    <label htmlFor="create-staff-specialty" className="sr-only">Chuyên khoa</label>
                    <select
                      id="create-staff-specialty"
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
                    <label htmlFor="create-staff-role" className="sr-only">Vai trò</label>
                    <select
                      id="create-staff-role"
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
                onClick={() => router.push("/staff")}
                type="button"
              >
                Hủy bỏ
              </button>
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
                    <span className="material-symbols-outlined text-[18px]">save</span>
                    Tạo nhân sự
                  </>
                )}
              </button>
            </div>
          </article>
        </section>
      )}
    </>
  );
}
