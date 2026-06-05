"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { api } from "@/lib/api";

// ── Types matching backend StaffResponse / StaffRequest ──────
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
  roles: string[];
};

const emptyForm: StaffFormData = {
  username: "",
  fullName: "",
  password: "",
  phone: "",
  email: "",
  specialtyId: null,
  maxShiftsPerMonth: 5,
  roles: [],
};

export function StaffCrudPanel() {
  const [records, setRecords] = useState<StaffResponse[]>([]);
  const [form, setForm] = useState<StaffFormData>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState<"success" | "error" | "info">("info");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [specialties, setSpecialties] = useState<SpecialtyInfo[]>([]);

  // ── Fetch staff list ──────────────────────────────────────
  const fetchStaff = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<StaffResponse[]>("/staff");
      setRecords(data ?? []);
    } catch {
      showMessage("Không thể tải danh sách nhân sự. Backend có thể chưa chạy.", "error");
      setRecords([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchSpecialties = useCallback(async () => {
    try {
      const data = await api.get<SpecialtyInfo[]>("/specialties");
      setSpecialties(data ?? []);
    } catch (err) {
      console.error("Không thể tải danh sách chuyên khoa:", err);
    }
  }, []);

  useEffect(() => {
    fetchStaff();
    fetchSpecialties();
  }, [fetchStaff, fetchSpecialties]);

  // ── Summary cards ─────────────────────────────────────────
  const summary = useMemo(
    () => [
      ["Tổng nhân sự", String(records.length).padStart(2, "0")],
      ["Đang làm", String(records.filter((r) => r.isActive).length).padStart(2, "0")],
      ["Ngừng HĐ", String(records.filter((r) => !r.isActive).length).padStart(2, "0")],
      [
        "Chuyên khoa",
        String(
          new Set(records.map((r) => r.specialty?.name).filter(Boolean)).size,
        ).padStart(2, "0"),
      ],
    ],
    [records],
  );

  // ── Filtered records (client-side search) ─────────────────
  const filteredRecords = useMemo(() => {
    if (!searchKeyword.trim()) return records;
    const kw = searchKeyword.toLowerCase();
    return records.filter(
      (r) =>
        r.fullName.toLowerCase().includes(kw) ||
        r.username.toLowerCase().includes(kw) ||
        (r.specialty?.name ?? "").toLowerCase().includes(kw) ||
        r.email.toLowerCase().includes(kw),
    );
  }, [records, searchKeyword]);

  // ── Helpers ───────────────────────────────────────────────
  function showMessage(msg: string, type: "success" | "error" | "info" = "info") {
    setMessage(msg);
    setMessageType(type);
    if (type === "success") {
      setTimeout(() => setMessage(""), 4000);
    }
  }

  function updateField<K extends keyof StaffFormData>(field: K, value: StaffFormData[K]) {
    setForm((current) => ({ ...current, [field]: value }));
    if (fieldErrors[field]) {
      setFieldErrors((current) => {
        const next = { ...current };
        delete next[field];
        return next;
      });
    }
  }

  // ── Create / Update ───────────────────────────────────────
  async function submitStaff(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFieldErrors({});

    // Client-side validations
    const errors: Record<string, string> = {};
    if (!form.username.trim()) {
      errors.username = "Username không được để trống";
    }
    if (!form.fullName.trim()) {
      errors.fullName = "Họ tên không được để trống";
    }
    if (editingId === null && !form.password.trim()) {
      errors.password = "Mật khẩu không được để trống";
    } else if (form.password.trim() && form.password.length < 6) {
      errors.password = "Mật khẩu phải từ 6 ký tự trở lên";
    }
    if (form.phone.trim() && !/^[0-9]{10,11}$/.test(form.phone.trim())) {
      errors.phone = "Số điện thoại phải từ 10 đến 11 chữ số";
    }
    if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      errors.email = "Email không đúng định dạng";
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      if (errors.username === "Username không được để trống" || 
          errors.fullName === "Họ tên không được để trống" || 
          errors.password === "Mật khẩu không được để trống") {
        showMessage("Vui lòng nhập đầy đủ thông tin", "error");
      } else {
        showMessage("Vui lòng kiểm tra và sửa các trường lỗi màu đỏ.", "error");
      }
      return;
    }

    try {
      setSubmitting(true);
      if (editingId !== null) {
        // Build body — omit password if blank
        const body: Record<string, unknown> = { ...form };
        if (!form.password.trim()) {
          delete body.password;
        }
        await api.put(`/staff/${editingId}`, body);
        showMessage(`Đã cập nhật ${form.fullName}.`, "success");
      } else {
        await api.post("/staff", form);
        showMessage(`Đã thêm ${form.fullName}.`, "success");
      }
      setForm(emptyForm);
      setEditingId(null);
      setFieldErrors({});
      await fetchStaff();
    } catch (err) {
      if (err instanceof Error) {
        if ("fieldErrors" in err && err.fieldErrors) {
          setFieldErrors(err.fieldErrors as Record<string, string>);
        }
        showMessage(err.message, "error");
      } else {
        showMessage("Lỗi lưu nhân sự", "error");
      }
    } finally {
      setSubmitting(false);
    }
  }

  // ── Edit ──────────────────────────────────────────────────
  function editStaff(record: StaffResponse) {
    setForm({
      username: record.username,
      fullName: record.fullName,
      password: "",
      phone: record.phone ?? "",
      email: record.email ?? "",
      specialtyId: record.specialty?.id ?? null,
      maxShiftsPerMonth: record.maxShiftsPerMonth ?? 5,
      roles: record.roles ?? [],
    });
    setEditingId(record.id);
    setFieldErrors({});
    showMessage(`Đang sửa ${record.fullName}.`, "info");
  }

  // ── Delete (soft-delete) ──────────────────────────────────
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

  // ── Render ────────────────────────────────────────────────
  return (
    <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_380px]">
      <div className="space-y-4">
        {/* Summary Cards */}
        <section className="grid gap-4 md:grid-cols-4">
          {summary.map(([label, value]) => (
            <div
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
              key={label}
            >
              <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
              <p className="mt-3 text-2xl font-semibold">{value}</p>
            </div>
          ))}
        </section>

        {/* Staff Table */}
        <SectionCard
          description="Dữ liệu được tải từ API backend — thêm, sửa, xóa cập nhật realtime"
          title="Danh sách nhân sự"
          action={
            <input
              className="h-8 w-56 rounded-md border border-slate-200 bg-slate-50 px-3 text-xs outline-none focus:border-slate-400 max-sm:w-full"
              id="staff-search"
              onChange={(e) => setSearchKeyword(e.target.value)}
              placeholder="Tìm kiếm theo tên, username, chuyên khoa…"
              value={searchKeyword}
            />
          }
        >
          <div className="overflow-x-auto">
            {loading ? (
              <div className="flex items-center justify-center py-12">
                <svg className="size-6 animate-spin text-slate-400" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
                  <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
                </svg>
              </div>
            ) : (
              <table className="min-w-full border-collapse text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    {["ID", "Username", "Họ tên", "Email", "SĐT", "Chuyên khoa", "Max ca/tháng", "Vai trò", "Trạng thái", "Thao tác"].map(
                      (header) => (
                        <th className="border-b border-slate-200 px-4 py-3 font-semibold" key={header}>
                          {header}
                        </th>
                      ),
                    )}
                  </tr>
                </thead>
                <tbody>
                  {filteredRecords.length === 0 ? (
                    <tr>
                      <td className="px-4 py-8 text-center text-sm text-slate-400" colSpan={10}>
                        {searchKeyword ? "Không tìm thấy nhân sự phù hợp" : "Chưa có nhân sự nào"}
                      </td>
                    </tr>
                  ) : (
                    filteredRecords.map((record) => (
                      <tr
                        className="border-b border-slate-100 transition-colors hover:bg-slate-50/50"
                        key={record.id}
                      >
                        <td className="px-4 py-3 font-medium text-slate-500">{record.id}</td>
                        <td className="px-4 py-3 font-medium">{record.username}</td>
                        <td className="px-4 py-3">{record.fullName}</td>
                        <td className="px-4 py-3 text-slate-500">{record.email}</td>
                        <td className="px-4 py-3">{record.phone ?? "-"}</td>
                        <td className="px-4 py-3">{record.specialty?.name ?? "-"}</td>
                        <td className="px-4 py-3 text-center">{record.maxShiftsPerMonth}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {record.roles?.length > 0
                              ? record.roles.map((role) => (
                                  <span
                                    className="inline-flex h-6 items-center rounded bg-indigo-50 px-2 text-xs font-medium text-indigo-700"
                                    key={role}
                                  >
                                    {role}
                                  </span>
                                ))
                              : "-"}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={record.isActive ? "success" : "warning"}>
                            {record.isActive ? "Đang làm" : "Ngừng HĐ"}
                          </StatusBadge>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex gap-2">
                            <button
                              className="h-8 rounded-md border border-slate-200 px-3 text-xs font-medium transition-colors hover:bg-slate-50"
                              id={`edit-staff-${record.id}`}
                              onClick={() => editStaff(record)}
                              type="button"
                            >
                              Sửa
                            </button>
                            <button
                              className="h-8 rounded-md border border-rose-200 px-3 text-xs font-medium text-rose-700 transition-colors hover:bg-rose-50"
                              id={`delete-staff-${record.id}`}
                              onClick={() => deleteStaff(record.id, record.fullName)}
                              type="button"
                            >
                              Xóa
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
        </SectionCard>
      </div>

      {/* ── Form sidebar ────────────────────────────────────── */}
      <aside className="space-y-4">
        <SectionCard
          description={
            editingId !== null
              ? "Cập nhật thông tin — password trống = giữ nguyên"
              : "Điền đầy đủ thông tin để thêm nhân sự mới"
          }
          title={editingId !== null ? "Sửa nhân sự" : "Thêm nhân sự"}
        >
          <form className="space-y-3 p-4" onSubmit={submitStaff}>
            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">Username</span>
                {fieldErrors.username && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.username}</span>
                )}
              </div>
              <input
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.username 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-username"
                onChange={(e) => updateField("username", e.target.value)}
                value={form.username}
              />
            </label>

            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">Họ tên</span>
                {fieldErrors.fullName && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.fullName}</span>
                )}
              </div>
              <input
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.fullName 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-fullName"
                onChange={(e) => updateField("fullName", e.target.value)}
                value={form.fullName}
              />
            </label>

            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">
                  {editingId !== null ? "Mật khẩu mới (để trống = không đổi)" : "Mật khẩu"}
                </span>
                {fieldErrors.password && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.password}</span>
                )}
              </div>
              <input
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.password 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-password"
                onChange={(e) => updateField("password", e.target.value)}
                type="password"
                value={form.password}
              />
            </label>

            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">Email</span>
                {fieldErrors.email && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.email}</span>
                )}
              </div>
              <input
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.email 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-email"
                onChange={(e) => updateField("email", e.target.value)}
                type="email"
                value={form.email}
              />
            </label>

            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">Số điện thoại (10 - 11 chữ số)</span>
                {fieldErrors.phone && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.phone}</span>
                )}
              </div>
              <input
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.phone 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-phone"
                onChange={(e) => updateField("phone", e.target.value)}
                placeholder="Ví dụ: 0901234567"
                value={form.phone}
              />
            </label>

            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">Chuyên khoa</span>
                {fieldErrors.specialtyId && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.specialtyId}</span>
                )}
              </div>
              <select
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.specialtyId 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-specialty"
                onChange={(e) => {
                  const val = e.target.value;
                  updateField("specialtyId", val ? parseInt(val) : null);
                }}
                value={form.specialtyId ?? ""}
              >
                <option value="">-- Chọn chuyên khoa --</option>
                {specialties.map((spec) => (
                  <option key={spec.id} value={spec.id}>
                    {spec.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="block">
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-slate-500">Max ca / tháng</span>
                {fieldErrors.maxShiftsPerMonth && (
                  <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.maxShiftsPerMonth}</span>
                )}
              </div>
              <input
                className={`mt-1 h-9 w-full rounded-md border bg-white px-3 text-sm outline-none transition-colors ${
                  fieldErrors.maxShiftsPerMonth 
                    ? "border-rose-400 focus:border-rose-500 bg-rose-50/10" 
                    : "border-slate-200 focus:border-slate-400"
                }`}
                id="staff-field-maxShifts"
                min={1}
                onChange={(e) => updateField("maxShiftsPerMonth", parseInt(e.target.value) || 5)}
                type="number"
                value={form.maxShiftsPerMonth}
              />
            </label>

            <div className="block">
              <span className="text-xs font-medium text-slate-500">Vai trò</span>
              <div className="mt-2 flex flex-wrap gap-4">
                {["ADMIN", "MANAGER", "STAFF"].map((role) => (
                  <label className="inline-flex items-center gap-2 text-sm font-normal text-slate-600 cursor-pointer" key={role}>
                    <input
                      checked={form.roles?.includes(role) ?? false}
                      onChange={(e) => {
                        const checked = e.target.checked;
                        let updatedRoles: string[];
                        if (role === "STAFF") {
                          updatedRoles = checked ? ["STAFF"] : [];
                        } else {
                          updatedRoles = checked
                            ? [...(form.roles ?? []).filter((r) => r !== "STAFF"), role]
                            : (form.roles ?? []).filter((r) => r !== role);
                        }
                        updateField("roles", updatedRoles);
                      }}
                      type="checkbox"
                      className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 size-4 cursor-pointer"
                    />
                    <span>{role}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* Message */}
            {message ? (
              <p
                className={`rounded-md border px-3 py-2 text-sm whitespace-pre-line ${
                  messageType === "error"
                    ? "border-rose-200 bg-rose-50 text-rose-700"
                    : messageType === "success"
                      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                      : "border-slate-200 bg-slate-50 text-slate-600"
                }`}
              >
                {message}
              </p>
            ) : null}

            {/* Buttons */}
            <div className="grid grid-cols-2 gap-2">
              <button
                className="h-9 rounded-md bg-slate-950 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={submitting}
                id="staff-submit"
                type="submit"
              >
                {submitting ? "Đang lưu…" : editingId !== null ? "Cập nhật" : "Thêm mới"}
              </button>
              <button
                className="h-9 rounded-md border border-slate-200 text-sm font-medium transition-colors hover:bg-slate-50"
                id="staff-reset"
                onClick={() => {
                  setEditingId(null);
                  setForm(emptyForm);
                  setMessage("");
                  setFieldErrors({});
                }}
                type="button"
              >
                Làm mới
              </button>
            </div>
          </form>
        </SectionCard>
      </aside>
    </div>
  );
}
