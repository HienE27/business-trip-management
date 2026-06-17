"use client";
/* eslint-disable react-hooks/exhaustive-deps */

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { getRoleLabel } from "@/lib/roleLabels";
import { useToast } from "@/hooks/useToast";
import type { Specialty } from "@/types/api";

type SpecialtyInfo = {
  id: number;
  name: string;
};

type StaffStatus = "active" | "on_leave" | "inactive";

type StaffResponse = {
  id: number;
  username: string;
  fullName: string;
  phone: string;
  email: string;
  position: string | null;
  specialty: SpecialtyInfo | null;
  maxShiftsPerMonth: number;
  isActive: boolean;
  status: StaffStatus;
  roles: string[];
  createdAt: string;
  updatedAt: string;
};

type StaffApiResponse = Omit<StaffResponse, "status" | "roles"> & {
  status: string;
  roles: string[] | null;
};

type StaffFormData = {
  username: string;
  fullName: string;
  password: string;
  phone: string;
  email: string;
  position: string;
  specialtyId: number | null;
  maxShiftsPerMonth: number;
  status: string;
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

function normalizeStaffStatus(status: string | null | undefined, isActive: boolean): StaffStatus {
  const normalized = status?.trim().toUpperCase();
  if (normalized === "ACTIVE") return "active";
  if (normalized === "ON_LEAVE") return "on_leave";
  if (normalized === "INACTIVE") return "inactive";
  return isActive ? "active" : "inactive";
}

function normalizeStaffRecord(record: StaffApiResponse): StaffResponse {
  return {
    ...record,
    status: normalizeStaffStatus(record.status, record.isActive),
    roles: (record.roles ?? []).map((role) => role.toUpperCase()),
  };
}

function getInitials(name: string) {  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

function getStatusLabel(record: StaffResponse) {
  if (record.status === "active") return "Đang làm việc";
  if (record.status === "on_leave") return "Nghỉ phép";
  return "Đã nghỉ";
}

function getStatusClass(record: StaffResponse) {
  if (record.status === "active") return "bg-secondary-container text-on-secondary-container border border-secondary/20";
  if (record.status === "on_leave") return "bg-tertiary-fixed text-on-tertiary-fixed-variant border border-tertiary/20";
  return "bg-surface-container-highest text-outline border border-outline-variant";
}

function getStatusDot(record: StaffResponse) {
  if (record.status === "active") return "bg-secondary";
  if (record.status === "on_leave") return "bg-tertiary";
  return "bg-outline";
}

export function StaffCrudPanel() {
  const searchParams = useSearchParams();
  const [records, setRecords] = useState<StaffResponse[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [form, setForm] = useState<StaffFormData>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [specialtyFilter, setSpecialtyFilter] = useState<number | "">("");
  const [positionFilter, setPositionFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const PAGE_SIZE = 10;

  // Sync global search ?q= URL param to local search state
  useEffect(() => {
    const q = searchParams.get("q");
    if (q && q !== searchKeyword) {
      setSearchKeyword(q);
    }
  }, [searchParams]);

  const fetchSpecialties = useCallback(async () => {
    try {
      const data = await api.get<Specialty[]>("/specialties/active");
      setSpecialties(data ?? []);
    } catch {
      // Silently fail - specialties are optional
    }
  }, []);

  const fetchStaff = useCallback(async () => {
    try {
      setLoading(true);
      const hasFilters = Boolean(searchKeyword.trim() || statusFilter || roleFilter || specialtyFilter || positionFilter);
      const params = new URLSearchParams();
      if (searchKeyword.trim()) {
        params.set("keyword", searchKeyword.trim());
      }
      if (statusFilter) {
        params.set("status", statusFilter.toUpperCase());
      }
      if (roleFilter) {
        params.set("role", roleFilter);
      }
      if (specialtyFilter) {
        params.set("specialtyId", String(specialtyFilter));
      }
      if (positionFilter.trim()) {
        params.set("position", positionFilter.trim());
      }

      const data = await api.get<StaffApiResponse[]>(hasFilters ? `/staff/search?${params.toString()}` : "/staff");
      const normalizedData = (data ?? []).map(normalizeStaffRecord);

      const seen = new Set<number>();
      const dedup = (list: StaffResponse[]): StaffResponse[] =>
        list.filter((r) => {
          if (seen.has(r.id)) return false;
          seen.add(r.id);
          return true;
        });

      setRecords(dedup(normalizedData));
    } catch {
      toast.error("Không thể tải danh sách nhân sự. Vui lòng kiểm tra kết nối backend.");
      setRecords([]);
    } finally {
      setLoading(false);
    }
  }, [roleFilter, searchKeyword, specialtyFilter, statusFilter]);

  useEffect(() => {
    fetchSpecialties();
  }, [fetchSpecialties]);

  useEffect(() => {
    const timer = setTimeout(() => fetchStaff(), 0);
    return () => clearTimeout(timer);
  }, [fetchStaff]);

  useEffect(() => {
    setCurrentPage(1);
  }, [searchKeyword, statusFilter, roleFilter, specialtyFilter, positionFilter]);

  const summary = useMemo(
    () => [
      ["Tổng nhân sự", String(records.length).padStart(2, "0")],
      ["Đang làm việc", String(records.filter((r) => r.isActive).length).padStart(2, "0")],
      ["Đã nghỉ", String(records.filter((r) => !r.isActive).length).padStart(2, "0")],
      [
        "Chuyên khoa",
        String(new Set(records.map((r) => r.specialty?.name).filter(Boolean)).size).padStart(2, "0"),
      ],
    ],
    [records],
  );

  const filteredRecords = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase();

    const deduped = records.filter((r, i, arr) =>
      arr.findIndex((x) => x.id === r.id) === i
    );

    return deduped.filter((record) => {
      const matchesKeyword = !keyword
        ? true
        : record.fullName.toLowerCase().includes(keyword) ||
          record.username.toLowerCase().includes(keyword) ||
          (record.specialty?.name ?? "").toLowerCase().includes(keyword) ||
          record.email.toLowerCase().includes(keyword);

      const matchesStatus =
        !statusFilter ||
        (statusFilter === "active" && record.status === "active") ||
        (statusFilter === "ON_LEAVE" && record.status === "on_leave") ||
        (statusFilter === "INACTIVE" && record.status === "inactive");

      const matchesRole =
        !roleFilter || record.roles.some((r) => r.toUpperCase() === roleFilter.toUpperCase());

      return matchesKeyword && matchesStatus && matchesRole;
    });
  }, [records, searchKeyword, statusFilter, roleFilter]);

  const pagedRecords = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return filteredRecords.slice(start, start + PAGE_SIZE);
  }, [filteredRecords, currentPage]);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(filteredRecords.length / PAGE_SIZE)),
    [filteredRecords.length]
  );

  const pageNumbers = useMemo(() => {
    const pages: (number | "...")[] = [];
    if (totalPages <= 7) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
      pages.push(1);
      if (currentPage > 3) pages.push("...");
      for (
        let i = Math.max(2, currentPage - 1);
        i <= Math.min(totalPages - 1, currentPage + 1);
        i++
      ) {
        pages.push(i);
      }
      if (currentPage < totalPages - 2) pages.push("...");
      pages.push(totalPages);
    }
    return pages;
  }, [totalPages, currentPage]);

  function handlePageClick(page: number | "...") {
    if (page === "...") return;
    setCurrentPage(page);
  }

  function handleExportExcel() {
    const rows = [
      ["Họ tên", "Tên đăng nhập", "Vai trò", "Chuyên khoa", "SĐT", "Email", "Trạng thái"],
      ...filteredRecords.map((r) => [
        r.fullName,
        r.username,
        getRoleLabel(r.roles),
        r.specialty?.name ?? "Chưa phân khoa",
        r.phone || "-",
        r.email || "-",
        getStatusLabel(r),
      ]),
    ];
    const csv = rows
      .map((row) =>
        row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(",")
      )
      .join("\n");
    const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `danh-sach-nhan-su-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  function updateField(field: keyof StaffFormData, value: string | number | null) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function openAddForm() {
    setForm({ ...emptyForm, roles: [] });
    setEditingId(null);
    setFormOpen(true);
  }

  function editStaff(record: StaffResponse) {
    setForm({
      username: record.username,
      fullName: record.fullName,
      password: "",
      phone: record.phone ?? "",
      email: record.email ?? "",
      position: record.position ?? "",
      specialtyId: record.specialty?.id ?? null,
      maxShiftsPerMonth: record.maxShiftsPerMonth ?? 5,
      status: record.status === "active" ? "ACTIVE" : record.status === "on_leave" ? "ON_LEAVE" : "INACTIVE",
      roles: record.roles ?? [],
    });
    setEditingId(record.id);
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditingId(null);
    setForm(emptyForm);
  }

  async function submitStaff(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.fullName.trim() || !form.username.trim()) {
      toast.error("Cần nhập họ tên và tên đăng nhập.");
      return;
    }

    try {
      setSubmitting(true);
      if (editingId !== null) {
        const body: Record<string, unknown> = { ...form };
        if (!form.password.trim()) {
          delete body.password;
        }
        await api.put(`/staff/${editingId}`, body);
        toast.success(`Đã cập nhật ${form.fullName}.`);
      } else {
        if (!form.password.trim()) {
          toast.error("Cần nhập mật khẩu khi thêm mới.");
          return;
        }
        await api.post("/staff", form);
        toast.success(`Đã thêm ${form.fullName}.`);
      }
      setForm(emptyForm);
      setEditingId(null);
      closeForm();
      await fetchStaff();
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi lưu nhân sự"));
    } finally {
      setSubmitting(false);
    }
  }

  async function deleteStaff(id: number, name: string) {
    if (!confirm(`Bạn có chắc muốn dừng hoạt động nhân sự "${name}"?`)) return;

    try {
      await api.delete(`/staff/${id}`);
      toast.success(`Đã dừng hoạt động ${name}.`);
      if (editingId === id) {
        closeForm();
      }
      await fetchStaff();
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi xóa nhân sự"));
    }
  }

  return (
    <div className="space-y-6">
      {/* Slide-in Drawer */}
      <div
        aria-label="Form nhân sự"
        aria-modal="true"
        className={`fixed inset-0 z-50 flex justify-end ${formOpen ? "pointer-events-auto" : "pointer-events-none"}`}
        role="dialog"
      >
        <div
          className={`absolute inset-0 bg-black/40 transition-opacity duration-300 ${formOpen ? "opacity-100" : "opacity-0"}`}
          onClick={closeForm}
        />
        <div
          className={`relative flex flex-col w-full max-w-[420px] h-full bg-surface-container-lowest shadow-sm transition-transform duration-300 ease-out ${formOpen ? "translate-x-0" : "translate-x-full"}`}
        >
          <div className="flex items-center justify-between px-6 py-5 border-b border-outline-variant shrink-0">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-container">
                <span className="material-symbols-outlined text-primary text-[20px]">
                  {editingId !== null ? "edit" : "person_add"}
                </span>
              </div>
              <div>
                <h2 className="text-headline-lg font-semibold text-on-surface">
                  {editingId !== null ? "Sửa nhân sự" : "Thêm nhân sự"}
                </h2>
                <p className="text-label-md text-on-surface-variant">
                  {editingId !== null ? "Cập nhật thông tin nhân viên" : "Nhập thông tin nhân viên mới"}
                </p>
              </div>
            </div>
            <button
              className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors"
              onClick={closeForm}
              title="Đóng"
              type="button"
            >
              <span className="material-symbols-outlined text-[20px]">close</span>
            </button>
          </div>

          <div className="flex-1 overflow-y-auto px-6 py-5">
            <form className="flex flex-col gap-5" id="staff-drawer-form" onSubmit={submitStaff}>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-body-sm font-semibold text-on-surface">Username <span className="text-error">*</span></span>
                  <input
                    aria-label="Username"
                    autoComplete="username"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="username"
                    onChange={(e) => updateField("username", e.target.value)}
                    required
                    value={form.username}
                  />
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-body-sm font-semibold text-on-surface">Họ tên <span className="text-error">*</span></span>
                  <input
                    aria-label="Ho ten"
                    autoComplete="name"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="fullName"
                    onChange={(e) => updateField("fullName", e.target.value)}
                    required
                    value={form.fullName}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-body-sm font-semibold text-on-surface">Chức vụ</span>
                  <input
                    aria-label="Chuc vu"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="position"
                    onChange={(e) => updateField("position", e.target.value)}
                    placeholder="VD: Bác sĩ, Điều dưỡng"
                    value={form.position}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-body-sm font-semibold text-on-surface">Vai trò</span>
                  <div className="relative">
                    <select
                      aria-label="Vai tro"
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 cursor-pointer"
                      multiple={false}
                      name="roles"
                      onChange={(e) => {
                        const val = e.target.value;
                        setForm((f) => ({ ...f, roles: val ? [val] : [] }));
                      }}
                      value={form.roles[0] ?? ""}
                    >
                      <option value="">Chọn vai trò</option>
                      <option value="ADMIN">Quản trị viên</option>
                      <option value="MANAGER">Quản lý lịch</option>
                      <option value="STAFF">Nhân viên</option>
                    </select>
                    <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
                  </div>
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-body-sm font-semibold text-on-surface">
                    {editingId !== null ? "Mật khẩu mới (bỏ trống = giữ nguyên)" : "Mật khẩu"}
                    {editingId === null && <span className="text-error"> *</span>}
                  </span>
                  <input
                    autoComplete="new-password"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="password"
                    onChange={(e) => updateField("password", e.target.value)}
                    type="password"
                    value={form.password}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-body-sm font-semibold text-on-surface">Email</span>
                  <input
                    aria-label="Email"
                    autoComplete="email"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    name="email"
                    onChange={(e) => updateField("email", e.target.value)}
                    type="email"
                    value={form.email}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-body-sm font-semibold text-on-surface">Số điện thoại</span>
                  <input
                    aria-label="So dien thoai"
                    autoComplete="tel"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    inputMode="tel"
                    name="phone"
                    onChange={(e) => updateField("phone", e.target.value)}
                    type="tel"
                    value={form.phone}
                  />
                </label>

                <label className="flex flex-col gap-1.5 col-span-2">
                  <span className="text-body-sm font-semibold text-on-surface">Chuyên khoa</span>
                  <div className="relative">
                    <select
                      aria-label="Chuyen khoa"
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
                    <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
                  </div>
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-body-sm font-semibold text-on-surface">Max ca / tháng</span>
                  <input
                    aria-label="So ca toi da moi thang"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    min={1}
                    name="maxShiftsPerMonth"
                    onChange={(e) => updateField("maxShiftsPerMonth", parseInt(e.target.value) || 5)}
                    type="number"
                    value={form.maxShiftsPerMonth}
                  />
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className="text-body-sm font-semibold text-on-surface">Trạng thái</span>
                  <div className="relative">
                    <select
                      aria-label="Trang thai"
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 cursor-pointer"
                      name="status"
                      onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
                      value={form.status}
                    >
                      <option value="ACTIVE">Đang làm việc</option>
                      <option value="ON_LEAVE">Nghỉ phép</option>
                      <option value="INACTIVE">Dừng hoạt động</option>
                    </select>
                    <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
                  </div>
                </label>
              </div>
            </form>
          </div>

          <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant shrink-0">
            <button
              aria-label="Hủy bỏ"
              className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-body-sm font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
              onClick={closeForm}
              type="button"
            >
              Hủy bỏ
            </button>
            <button
              className="flex-1 flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-body-sm font-semibold text-on-primary shadow-sm transition-colors hover:brightness-110 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
              disabled={submitting}
              form="staff-drawer-form"
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
                  {editingId !== null ? "Cập nhật" : "Lưu nhân sự"}
                </>
              )}
            </button>
          </div>
        </div>
      </div>

      <section className="flex flex-col justify-between gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest p-3 md:p-4 shadow-sm sm:flex-row sm:items-center">
        <div>
          <p className="text-label-sm text-on-surface-variant">Nhân sự</p>
          <p className="mt-0.5 text-body-sm text-on-surface-variant leading-snug">
            Quản lý cơ sở dữ liệu nhân viên, chức vụ và trạng thái hoạt động trong hệ thống.
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-3">
          <button
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 h-10 text-label-md font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            onClick={handleExportExcel}
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">download</span>
            Xuất Excel
          </button>
          <button
            className="flex items-center gap-2 rounded-lg bg-primary px-4 h-10 text-label-md font-medium text-on-primary shadow-sm transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            onClick={openAddForm}
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">add</span>
            Thêm nhân viên
          </button>
        </div>
      </section>

      <section className="grid gap-3 md:grid-cols-4">
        {summary.map(([label, value]) => (
          <div
            className="rounded-lg border border-outline-variant bg-surface-container-lowest p-3 shadow-sm hover:bg-surface-container-low transition-colors"
            key={label}
          >
            <p className="text-label-md font-medium text-on-surface-variant">{label}</p>
            <p className="mt-1 text-headline-lg font-bold leading-[28px] text-on-surface">{value}</p>
          </div>
        ))}
      </section>

      <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-3 shadow-sm flex flex-wrap lg:flex-nowrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px]">
          <span aria-hidden="true" className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
            search
          </span>
          <input
            aria-label="Tìm kiếm nhân sự"
            autoComplete="off"
            className="w-full rounded-lg border border-transparent bg-surface-container-low py-2 pl-9 pr-3 text-body-sm text-on-surface transition-all placeholder:text-outline focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            name="staffSearch"
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="Tìm kiếm tên, email hoặc mã nhân viên..."
            value={searchKeyword}
          />
        </div>

        <div className="relative w-full lg:w-40">
          <select
            aria-label="Loc theo chuc vu"
            className="w-full appearance-none rounded-lg border border-transparent bg-surface-container-low py-2 pl-3 pr-8 text-body-sm text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            onChange={(e) => setRoleFilter(e.target.value)}
            value={roleFilter}
          >
            <option value="">Tất cả Chức vụ</option>
            <option value="ADMIN">Quản trị viên</option>
            <option value="MANAGER">Quản lý lịch</option>
            <option value="STAFF">Nhân viên</option>
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
            expand_more
          </span>
        </div>

        <div className="relative w-full lg:w-40">
          <select
            aria-label="Lọc theo khoa phòng"
            className="w-full appearance-none rounded-lg border border-transparent bg-surface-container-low py-2 pl-3 pr-8 text-body-sm text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={specialtyFilter}
            onChange={(e) => setSpecialtyFilter(e.target.value === "" ? "" : Number(e.target.value) || 0)}
          >
            <option value="">Tất cả Khoa/Phòng</option>
            {specialties.map((spec) => (
              <option key={spec.id} value={spec.id}>{spec.name}</option>
            ))}
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
            expand_more
          </span>
        </div>

        <div className="relative w-full lg:w-40">
          <select
            aria-label="Loc theo trang thai"
            className="w-full appearance-none rounded-lg border border-transparent bg-surface-container-low py-2 pl-3 pr-8 text-body-sm text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            onChange={(e) => setStatusFilter(e.target.value)}
            value={statusFilter}
          >
            <option value="">Trạng thái</option>
            <option value="active">Đang làm việc</option>
            <option value="on_leave">Nghỉ phép</option>
            <option value="inactive">Đã nghỉ</option>
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
            expand_more
          </span>
        </div>

        <div className="relative w-full lg:w-40">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">badge</span>
          <input
            type="text"
            aria-label="Lọc theo chức vụ"
            className="w-full h-10 pl-9 pr-3 rounded-lg border border-transparent bg-surface-container-low text-body-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
            placeholder="Chức vụ..."
            value={positionFilter}
            onChange={(e) => setPositionFilter(e.target.value)}
          />
        </div>
      </section>

      <section className="rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden flex flex-col">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="flex items-center justify-center py-16">
              <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : (
            <table className="w-full border-collapse text-left">
              <thead className="bg-surface-container-low border-b border-outline-variant">
                <tr>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Nhân viên</th>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Mã NV</th>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Chức vụ</th>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Vai trò</th>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Khoa/Phòng</th>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">SĐT</th>
                  <th className="px-3 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Trạng thái</th>
                  <th className="px-3 py-2.5 text-right text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {pagedRecords.length === 0 ? (
                  <tr>
                    <td className="px-3 py-8 text-center text-body-sm text-on-surface-variant" colSpan={8}>
                      {searchKeyword || statusFilter
                        ? "Không tìm thấy nhân sự phù hợp"
                        : "Chưa có nhân sự nào"}
                    </td>
                  </tr>
                ) : (
                  pagedRecords.map((record) => (
                    <tr className="group transition-colors hover:bg-surface-container-low h-11" key={record.id}>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2.5">
                          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-fixed font-bold text-label-sm text-on-primary-fixed-variant">
                            {getInitials(record.fullName)}
                          </div>
                          <div>
                            <p className="text-label-md font-semibold text-on-surface leading-tight">{record.fullName}</p>
                            <p className="text-label-sm text-on-surface-variant leading-tight">{record.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-2 text-label-md text-on-surface-variant">{record.username}</td>
                      <td className="px-3 py-2 text-label-md text-on-surface">{record.position || "—"}</td>
                      <td className="px-3 py-2 text-label-md text-on-surface">{getRoleLabel(record.roles)}</td>
                      <td className="px-3 py-2 text-label-md text-on-surface">{record.specialty?.name ?? "—"}</td>
                      <td className="px-3 py-2 text-label-md text-on-surface-variant">{record.phone || "-"}</td>
                      <td className="px-3 py-2">
                        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-label-sm font-medium ${getStatusClass(record)}`}>
                          <span className={`h-1 w-1 rounded-full ${getStatusDot(record)}`} />
                          {getStatusLabel(record)}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-right">
                        <div className="flex items-center justify-end gap-0.5">
                          <Link
                            aria-label={`Xem chi tiết ${record.fullName}`}
                            className="p-1 rounded text-outline hover:text-primary hover:bg-surface-container transition-colors"
                            href={`/staff/${record.id}`}
                            title="Xem chi tiết"
                          >
                            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">visibility</span>
                          </Link>
                          <button
                            aria-label={`Chỉnh sửa ${record.fullName}`}
                            className="p-1 rounded text-outline hover:text-primary hover:bg-surface-container transition-colors"
                            onClick={() => editStaff(record)}
                            title="Chỉnh sửa"
                            type="button"
                          >
                            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">edit</span>
                          </button>
                          <button
                            aria-label={`Xóa ${record.fullName}`}
                            className="p-1 rounded text-outline hover:text-error hover:bg-error-container transition-colors"
                            onClick={() => deleteStaff(record.id, record.fullName)}
                            title="Xóa"
                            type="button"
                          >
                            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">delete</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>

        <div className="flex items-center justify-between border-t border-surface-variant bg-surface-container-lowest px-4 py-3">
          <p className="text-body-sm text-on-surface-variant">
            Hiển thị{" "}
            <span className="font-medium text-on-surface">
              {(currentPage - 1) * PAGE_SIZE + 1}
            </span>{" "}
            đến{" "}
            <span className="font-medium text-on-surface">
              {Math.min(currentPage * PAGE_SIZE, filteredRecords.length)}
            </span>{" "}
            trong số{" "}
            <span className="font-medium text-on-surface">{filteredRecords.length}</span> nhân viên
          </p>
          <div className="flex items-center gap-1">
            <button
              aria-label="Trang truoc"
              className="p-1.5 rounded-md text-outline-variant hover:bg-surface-container hover:text-on-surface disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              disabled={currentPage === 1}
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              type="button"
            >
              <span aria-hidden="true" className="material-symbols-outlined text-[20px]">chevron_left</span>
            </button>
            {pageNumbers.map((page, idx) =>
              page === "..." ? (
                <span className="px-1 text-outline-variant font-label-md" key={`ellipsis-${idx}`}>
                  ...
                </span>
              ) : (
                <button
                  key={page}
                  className={`w-8 h-8 rounded-md font-label-md flex items-center justify-center transition-colors ${
                    currentPage === page
                      ? "bg-primary text-on-primary"
                      : "text-on-surface-variant hover:bg-surface-container"
                  }`}
                  onClick={() => handlePageClick(page)}
                  type="button"
                >
                  {page}
                </button>
              )
            )}
            <button
              aria-label="Trang sau"
              className="p-1.5 rounded-md text-on-surface-variant hover:bg-surface-container hover:text-on-surface disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              disabled={currentPage === totalPages}
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              type="button"
            >
              <span aria-hidden="true" className="material-symbols-outlined text-[20px]">chevron_right</span>
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
