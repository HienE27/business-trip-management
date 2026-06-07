"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";

type SpecialtyInfo = {
  id: number;
  name: string;
};

type StaffResponse = {
  id: number;
  username: string;
  fullName: string;
  phone: string;
  email: string;
  specialty: SpecialtyInfo | null;
  maxShiftsPerMonth: number;
  isActive: boolean;
  status: "active" | "on_leave" | "inactive";
  roles: string[];
  createdAt: string;
  updatedAt: string;
};

type StaffFormData = {
  username: string;
  fullName: string;
  password: string;
  phone: string;
  email: string;
  specialtyId: number | null;
  maxShiftsPerMonth: number;
};

const fallbackStaffRecords: StaffResponse[] = [
  {
    id: 1,
    username: "NV-0124",
    fullName: "Hoang Ngoc Anh",
    phone: "0987 654 321",
    email: "ngocanh.h@medops.vn",
    specialty: { id: 1, name: "Khoa Kham benh" },
    maxShiftsPerMonth: 6,
    isActive: true,
    status: "active",
    roles: ["STAFF"],
    createdAt: "2026-05-01T08:00:00.000Z",
    updatedAt: "2026-05-28T09:15:00.000Z",
  },
  {
    id: 2,
    username: "NV-0285",
    fullName: "Tran Minh Tuan",
    phone: "0912 345 678",
    email: "tuan.tm@medops.vn",
    specialty: { id: 2, name: "Khoa Cap cuu" },
    maxShiftsPerMonth: 5,
    isActive: true,
    status: "on_leave",
    roles: ["STAFF"],
    createdAt: "2026-05-02T08:00:00.000Z",
    updatedAt: "2026-05-27T14:20:00.000Z",
  },
  {
    id: 3,
    username: "NV-0310",
    fullName: "Le Thi Thanh",
    phone: "0909 112 233",
    email: "thanh.lt@medops.vn",
    specialty: { id: 3, name: "Khoa Chan doan hinh anh" },
    maxShiftsPerMonth: 5,
    isActive: true,
    status: "active",
    roles: ["STAFF"],
    createdAt: "2026-05-03T08:00:00.000Z",
    updatedAt: "2026-05-26T10:45:00.000Z",
  },
  {
    id: 4,
    username: "NV-0042",
    fullName: "Pham Van Dung",
    phone: "",
    email: "dung.pv@medops.vn",
    specialty: { id: 4, name: "Khoa Ngoai tong hop" },
    maxShiftsPerMonth: 4,
    isActive: false,
    status: "inactive",
    roles: ["STAFF"],
    createdAt: "2026-05-04T08:00:00.000Z",
    updatedAt: "2026-05-20T16:30:00.000Z",
  },
];

const emptyForm: StaffFormData = {
  username: "",
  fullName: "",
  password: "",
  phone: "",
  email: "",
  specialtyId: null,
  maxShiftsPerMonth: 5,
};

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

function getStatusLabel(record: StaffResponse) {
  if (record.status === "active") return "Dang lam viec";
  if (record.status === "on_leave") return "Nghi phep";
  return "Da nghi";
}

function getStatusClass(record: StaffResponse) {
  if (record.status === "active") return "bg-green-50 text-green-700 border border-green-200";
  if (record.status === "on_leave") return "bg-orange-50 text-orange-700 border border-orange-200";
  return "bg-gray-100 text-gray-600 border border-gray-200";
}

function getStatusDot(record: StaffResponse) {
  if (record.status === "active") return "bg-green-500";
  if (record.status === "on_leave") return "bg-orange-500";
  return "bg-gray-400";
}

export function StaffCrudPanel() {
  const [records, setRecords] = useState<StaffResponse[]>([]);
  const [form, setForm] = useState<StaffFormData>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState<"success" | "error" | "info">("info");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const fetchStaff = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<StaffResponse[]>("/staff");

      if (data && data.length > 0) {
        setRecords(data);
        setMessage("");
      } else {
        showMessage(
          "Danh sách nhân sự từ backend đang trống. Đang hiển thị dữ liệu mẫu để tiếp tục demo giao diện.",
          "info",
        );
        setRecords(fallbackStaffRecords);
      }
    } catch {
      showMessage(
        "Không thể tải danh sách nhân sự từ backend. Đang hiển thị dữ liệu mẫu để tiếp tục demo giao diện.",
        "info",
      );
      setRecords(fallbackStaffRecords);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStaff();
  }, [fetchStaff]);

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

    return records.filter((record) => {
      const matchesKeyword = !keyword
        ? true
        : record.fullName.toLowerCase().includes(keyword) ||
          record.username.toLowerCase().includes(keyword) ||
          (record.specialty?.name ?? "").toLowerCase().includes(keyword) ||
          record.email.toLowerCase().includes(keyword);

      const matchesStatus =
        !statusFilter ||
        (statusFilter === "active" && record.status === "active") ||
        (statusFilter === "on_leave" && record.status === "on_leave") ||
        (statusFilter === "inactive" && record.status === "inactive");

      return matchesKeyword && matchesStatus;
    });
  }, [records, searchKeyword, statusFilter]);

  function showMessage(msg: string, type: "success" | "error" | "info" = "info") {
    setMessage(msg);
    setMessageType(type);
    if (type === "success") {
      setTimeout(() => setMessage(""), 4000);
    }
  }

  function updateField(field: keyof StaffFormData, value: string | number | null) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submitStaff(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.fullName.trim() || !form.username.trim()) {
      showMessage("Cần nhập họ tên và tên đăng nhập.", "error");
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
        showMessage(`Đã cập nhật ${form.fullName}.`, "success");
      } else {
        if (!form.password.trim()) {
          showMessage("Cần nhập mật khẩu khi thêm mới.", "error");
          return;
        }
        await api.post("/staff", form);
        showMessage(`Đã thêm ${form.fullName}.`, "success");
      }
      setForm(emptyForm);
      setEditingId(null);
      await fetchStaff();
    } catch (err) {
      showMessage(err instanceof Error ? err.message : "Lỗi lưu nhân sự", "error");
    } finally {
      setSubmitting(false);
    }
  }

  function editStaff(record: StaffResponse) {
    setForm({
      username: record.username,
      fullName: record.fullName,
      password: "",
      phone: record.phone ?? "",
      email: record.email ?? "",
      specialtyId: record.specialty?.id ?? null,
      maxShiftsPerMonth: record.maxShiftsPerMonth ?? 5,
    });
    setEditingId(record.id);
    showMessage(`Đang sửa ${record.fullName}.`, "info");
  }

  async function deleteStaff(id: number, name: string) {
    if (!confirm(`Bạn có chắc muốn ngừng hoạt động nhân sự "${name}"?`)) return;

    try {
      await api.delete(`/staff/${id}`);
      showMessage(`Đã ngừng hoạt động ${name}.`, "success");
      if (editingId === id) {
        setEditingId(null);
        setForm(emptyForm);
      }
      await fetchStaff();
    } catch (err) {
      showMessage(err instanceof Error ? err.message : "Lỗi xóa nhân sự", "error");
    }
  }

  return (
    <div className="space-y-6">
      <section className="flex flex-col justify-between gap-5 rounded-xl border border-outline-variant bg-surface-container-lowest p-6 shadow-sm sm:flex-row sm:items-center">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-variant">Nhân sự</p>
          <p className="mt-1 text-[14px] text-on-surface-variant">
            Quản lý cơ sở dữ liệu nhân viên, chức vụ và trạng thái hoạt động trong hệ thống.
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-3">
          <button
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 h-10 text-[13px] font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">download</span>
            Xuất Excel
          </button>
          <button
            className="flex items-center gap-2 rounded-lg bg-primary px-4 h-10 text-[13px] font-medium text-on-primary shadow-sm transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">add</span>
            Thêm nhân viên
          </button>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-4">
        {summary.map(([label, value]) => (
          <div
            className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm hover:bg-surface-container-low transition-colors"
            key={label}
          >
            <p className="text-[13px] font-medium text-on-surface-variant">{label}</p>
            <p className="mt-3 text-[32px] font-bold leading-[40px] text-on-surface">{value}</p>
          </div>
        ))}
      </section>

      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm flex flex-wrap lg:flex-nowrap items-center gap-4">
        <div className="relative flex-1 min-w-[240px]">
          <span aria-hidden="true" className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">
            search
          </span>
          <input
            aria-label="Tìm kiếm nhân sự"
            autoComplete="off"
            className="w-full rounded-lg border border-transparent bg-surface-container-low py-2.5 pl-10 pr-4 text-sm text-on-surface transition-all placeholder:text-outline focus-visible:border-primary focus:bg-surface-container-lowest focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
            name="staffSearch"
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="Tìm kiếm tên, email hoặc mã nhân viên…"
            value={searchKeyword}
          />
        </div>

        <div className="relative w-full lg:w-48">
          <select aria-label="Lọc theo chức vụ" className="w-full appearance-none rounded-lg border border-transparent bg-surface-container-low py-2.5 pl-3 pr-8 text-sm text-on-surface transition-all focus-visible:border-primary focus:bg-surface-container-lowest focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary">
            <option value="">Tất cả Chức vụ</option>
            <option value="BS">Bác sĩ</option>
            <option value="DD">Điều dưỡng</option>
            <option value="KTV">Kỹ thuật viên</option>
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">
            expand_more
          </span>
        </div>

        <div className="relative w-full lg:w-48">
          <select aria-label="Lọc theo khoa phòng" className="w-full appearance-none rounded-lg border border-transparent bg-surface-container-low py-2.5 pl-3 pr-8 text-sm text-on-surface transition-all focus-visible:border-primary focus:bg-surface-container-lowest focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary">
            <option value="">Tất cả Khoa/Phòng</option>
            <option value="kham-benh">Khoa Khám bệnh</option>
            <option value="cap-cuu">Khoa Cấp cứu</option>
            <option value="noi-tong-hop">Khoa Nội tổng hợp</option>
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">
            expand_more
          </span>
        </div>

        <div className="relative w-full lg:w-48">
          <select
            aria-label="Lọc theo trạng thái"
            className="w-full appearance-none rounded-lg border border-transparent bg-surface-container-low py-2.5 pl-3 pr-8 text-sm text-on-surface transition-all focus-visible:border-primary focus:bg-surface-container-lowest focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
            onChange={(e) => setStatusFilter(e.target.value)}
            value={statusFilter}
          >
            <option value="">Trang thai</option>
            <option value="active">Dang lam viec</option>
            <option value="on_leave">Nghi phep</option>
            <option value="inactive">Da nghi</option>
          </select>
          <span aria-hidden="true" className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">
            expand_more
          </span>
        </div>
      </section>

      {message ? (
        <div
          aria-live="polite"
          className={`rounded-lg border px-4 py-3 text-sm ${
            messageType === "error"
              ? "border-error/20 bg-error-container text-on-error-container"
              : messageType === "success"
                ? "border-on-secondary-container/10 bg-secondary-container text-on-secondary-container"
                : "border-outline-variant bg-surface-container-low text-on-surface"
          }`}
        >
          {message}
        </div>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden flex flex-col">
          <div className="overflow-x-auto">
            {loading ? (
              <div className="flex items-center justify-center py-16">
                <svg className="size-6 animate-spin text-outline" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
                </svg>
              </div>
            ) : (
              <table className="w-full border-collapse text-left">
                <thead className="bg-surface-container-low border-b border-outline-variant">
                  <tr>
                    <th className="px-6 py-4 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Nhân viên</th>
                    <th className="px-6 py-4 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Mã NV</th>
                    <th className="px-6 py-4 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Chức vụ</th>
                    <th className="px-6 py-4 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Khoa/Phòng</th>
                    <th className="px-6 py-4 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Số điện thoại</th>
                    <th className="px-6 py-4 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Trạng thái</th>
                    <th className="px-6 py-4 text-right text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {filteredRecords.length === 0 ? (
                    <tr>
                      <td className="px-6 py-10 text-center text-sm text-on-surface-variant" colSpan={7}>
                        {searchKeyword || statusFilter
                          ? "Không tìm thấy nhân sự phù hợp"
                          : "Chưa có nhân sự nào"}
                      </td>
                    </tr>
                  ) : (
                    filteredRecords.map((record) => (
                      <tr className="group transition-colors hover:bg-surface-container-low" key={record.id}>
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-3">
                            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-fixed font-bold text-on-primary-fixed-variant">
                              {getInitials(record.fullName)}
                            </div>
                            <div>
                              <p className="text-[13px] font-semibold text-on-surface">{record.fullName}</p>
                              <p className="text-sm text-on-surface-variant">{record.email}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-sm text-on-surface-variant">{record.username}</td>
                        <td className="px-6 py-4 text-sm text-on-surface">{getRoleLabel(record.roles)}</td>
                        <td className="px-6 py-4 text-sm text-on-surface">{record.specialty?.name ?? "Chưa phân khoa"}</td>
                        <td className="px-6 py-4 text-sm text-on-surface-variant">{record.phone || "-"}</td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ${getStatusClass(record)}`}>
                            <span className={`h-1.5 w-1.5 rounded-full ${getStatusDot(record)}`} />
                            {getStatusLabel(record)}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <Link
                              aria-label={`Xem chi tiet ${record.fullName}`}
                              className="p-1.5 rounded-md text-outline hover:text-primary hover:bg-surface-container transition-colors"
                              href={`/staff/profile?id=${record.id}`}
                              title="Xem chi tiet"
                            >
                              <span aria-hidden="true" className="material-symbols-outlined text-[18px]">visibility</span>
                            </Link>
                            <button
                              aria-label={`Chinh sua ${record.fullName}`}
                              className="p-1.5 rounded-md text-outline hover:text-primary hover:bg-surface-container transition-colors"
                              onClick={() => editStaff(record)}
                              title="Chinh sua"
                              type="button"
                            >
                              <span aria-hidden="true" className="material-symbols-outlined text-[18px]">edit</span>
                            </button>
                            <button
                              aria-label={`Them tuy chon cho ${record.fullName}`}
                              className="p-1.5 rounded-md text-outline hover:text-on-surface hover:bg-surface-container transition-colors"
                              title="Them tuy chon"
                              type="button"
                            >
                              <span aria-hidden="true" className="material-symbols-outlined text-[18px]">more_vert</span>
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
            <p className="text-sm text-on-surface-variant">
              Hien thi <span className="font-medium text-on-surface">1</span> den <span className="font-medium text-on-surface">10</span> trong so <span className="font-medium text-on-surface">124</span> nhan vien
            </p>
            <div className="flex items-center gap-1">
              <button
                className="p-1.5 rounded-md text-outline-variant hover:bg-surface-container hover:text-on-surface disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                disabled
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">chevron_left</span>
              </button>
              <button className="w-8 h-8 rounded-md bg-primary text-on-primary font-label-md text-label-md flex items-center justify-center" type="button">1</button>
              <button className="w-8 h-8 rounded-md text-on-surface-variant hover:bg-surface-container font-label-md text-label-md flex items-center justify-center transition-colors" type="button">2</button>
              <button className="w-8 h-8 rounded-md text-on-surface-variant hover:bg-surface-container font-label-md text-label-md flex items-center justify-center transition-colors" type="button">3</button>
              <span className="px-1 text-outline-variant font-label-md text-label-md">...</span>
              <button className="w-8 h-8 rounded-md text-on-surface-variant hover:bg-surface-container font-label-md text-label-md flex items-center justify-center transition-colors" type="button">13</button>
              <button
                className="p-1.5 rounded-md text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">chevron_right</span>
              </button>
            </div>
          </div>
        </section>

        <aside>
          <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm sticky top-24">
            <div className="flex items-center gap-2 border-b border-outline-variant bg-surface-bright p-4 rounded-t-xl">
              <span aria-hidden="true" className="material-symbols-outlined text-primary">add_circle</span>
              <h3 className="text-[18px] font-semibold leading-[26px] text-on-surface">
                {editingId !== null ? "Sửa nhân sự" : "Thêm nhân sự"}
              </h3>
            </div>

            <form className="flex flex-col gap-4 p-5" onSubmit={submitStaff}>
              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-semibold text-on-surface">Username</span>
                <input
                  autoComplete="username"
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  name="username"
                  onChange={(e) => updateField("username", e.target.value)}
                  spellCheck={false}
                  value={form.username}
                />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-semibold text-on-surface">Họ tên</span>
                <input
                  autoComplete="name"
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  name="fullName"
                  onChange={(e) => updateField("fullName", e.target.value)}
                  value={form.fullName}
                />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-semibold text-on-surface">
                  {editingId !== null ? "Mật khẩu mới (để trống = không đổi)" : "Mật khẩu"}
                </span>
                <input
                  autoComplete={editingId !== null ? "new-password" : "new-password"}
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  name="password"
                  onChange={(e) => updateField("password", e.target.value)}
                  type="password"
                  value={form.password}
                />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-semibold text-on-surface">Email</span>
                <input
                  autoComplete="email"
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  name="email"
                  onChange={(e) => updateField("email", e.target.value)}
                  spellCheck={false}
                  type="email"
                  value={form.email}
                />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-semibold text-on-surface">Số điện thoại</span>
                <input
                  autoComplete="tel"
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  inputMode="tel"
                  name="phone"
                  onChange={(e) => updateField("phone", e.target.value)}
                  type="tel"
                  value={form.phone}
                />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-sm font-semibold text-on-surface">Max ca / tháng</span>
                <input
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  min={1}
                  name="maxShiftsPerMonth"
                  onChange={(e) => updateField("maxShiftsPerMonth", parseInt(e.target.value, 10) || 5)}
                  type="number"
                  value={form.maxShiftsPerMonth}
                />
              </label>

              <div className="mt-2 flex justify-end gap-3 border-t border-outline-variant pt-4">
                <button
                  className="rounded-lg px-4 py-2 text-sm font-semibold text-on-surface-variant transition-colors hover:bg-surface-container-low hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  onClick={() => {
                    setEditingId(null);
                    setForm(emptyForm);
                    setMessage("");
                  }}
                  type="button"
                >
                  Làm mới
                </button>
                <button
                  className="flex items-center gap-2 rounded-lg bg-primary px-5 py-2 text-sm font-semibold text-on-primary shadow-sm transition-colors hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
                  disabled={submitting}
                  type="submit"
                >
                  <span aria-hidden="true" className="material-symbols-outlined text-[18px]">save</span>
                  {submitting ? "Đang lưu…" : editingId !== null ? "Cập nhật" : "Lưu nhân sự"}
                </button>
              </div>
            </form>
          </section>
        </aside>
      </div>
    </div>
  );
}
