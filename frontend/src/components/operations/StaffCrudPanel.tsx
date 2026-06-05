"use client";

import { FormEvent, DragEvent, useCallback, useEffect, useMemo, useState, forwardRef, useImperativeHandle, useRef } from "react";
import { useRouter } from "next/navigation";
import { SectionCard } from "@/components/ui/SectionCard";
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
  status: string;
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
  status: string;
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
  status: "ACTIVE",
};

export type StaffCrudPanelRef = {
  exportExcel: () => void;
  resetForm: () => void;
};

type StaffCrudPanelProps = {
  isReadOnlyView?: boolean;
  showOnlyForm?: boolean;
  editingIdFromUrl?: number | null;
};

export const StaffCrudPanel = forwardRef<StaffCrudPanelRef, StaffCrudPanelProps>(
  ({ isReadOnlyView = false, showOnlyForm = false, editingIdFromUrl = null }, ref) => {
    const [importFile, setImportFile] = useState<File | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [isDragging, setIsDragging] = useState(false);

    const handleDragOver = (e: DragEvent) => {
      e.preventDefault();
      setIsDragging(true);
    };

    const handleDragLeave = () => {
      setIsDragging(false);
    };

    const handleDrop = (e: DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      
      if (editingId !== null) return;
      
      const files = e.dataTransfer.files;
      if (files && files.length > 0) {
        const file = files[0];
        const extension = file.name.split('.').pop()?.toLowerCase();
        if (['xlsx', 'xls', 'csv'].includes(extension || '')) {
          setImportFile(file);
          setFieldErrors({});
          showMessage(`Đã chọn tệp: ${file.name}. Nhấn nút "Import từ tệp" ở dưới để thực hiện.`, "info");
        } else {
          showMessage("Chỉ hỗ trợ định dạng tệp Excel (.xlsx, .xls) hoặc CSV (.csv).", "error");
        }
      }
    };
    const router = useRouter();
    const usernameInputRef = useRef<HTMLInputElement>(null);
    const [records, setRecords] = useState<StaffResponse[]>([]);
    const [form, setForm] = useState<StaffFormData>(emptyForm);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [message, setMessage] = useState("");
    const [messageType, setMessageType] = useState<"success" | "error" | "info">("info");
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    
    // Filters state (matching Image 2)
    const [searchKeyword, setSearchKeyword] = useState("");
    const [filterRole, setFilterRole] = useState("");
    const [filterSpecialty, setFilterSpecialty] = useState("");
    const [filterStatus, setFilterStatus] = useState("");
    
    // Pagination state
    const [currentPage, setCurrentPage] = useState(1);
    const [itemsPerPage, setItemsPerPage] = useState(10);

    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [specialties, setSpecialties] = useState<SpecialtyInfo[]>([]);

    const getInitials = (name: string) => {
      if (!name) return "";
      const parts = name.trim().split(/\s+/);
      if (parts.length >= 2) {
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
      }
      return name.slice(0, 2).toUpperCase();
    };

    const exportExcel = useCallback(() => {
      const headers = ["ID", "Username", "Họ tên", "Email", "Số điện thoại", "Chuyên khoa", "Max ca/tháng", "Vai trò", "Trạng thái"];
      const rows = records.map(r => [
        r.id,
        r.username,
        r.fullName,
        r.email,
        r.phone ? `="${r.phone}"` : "",
        r.specialty?.name || "",
        r.maxShiftsPerMonth,
        r.roles.join(", "),
        r.status === "ACTIVE" ? "Đang làm việc" : r.status === "ON_LEAVE" ? "Nghỉ phép" : "Đã nghỉ"
      ]);

      const csvContent = "\uFEFF" + [
        headers.join(","),
        ...rows.map(row => row.map(val => `"${String(val).replace(/"/g, '""')}"`).join(","))
      ].join("\n");

      const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.setAttribute("href", url);
      link.setAttribute("download", `danh_sach_nhan_su_${new Date().toISOString().split('T')[0]}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }, [records]);

    const downloadTemplate = useCallback(() => {
      const headers = ["ID", "Username", "Họ tên", "Email", "Số điện thoại", "Chuyên khoa", "Max ca/tháng", "Vai trò", "Trạng thái"];
      const rows = [
        ["", "bacsi_quang", "Nguyễn Văn Quang", "quang.nv@hospital.com", "0901234567", "Bác sĩ", "5", "STAFF, MANAGER", "Đang làm việc"],
        ["", "dieuduong_linh", "Trần Thị Linh", "linh.tt@hospital.com", "0901234568", "Điều dưỡng", "5", "STAFF", "Nghỉ phép"]
      ];

      const csvContent = "\uFEFF" + [
        headers.join(","),
        ...rows.map(row => row.map(val => `"${String(val).replace(/"/g, '""')}"`).join(","))
      ].join("\n");

      const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.setAttribute("href", url);
      link.setAttribute("download", "danh_sach_nhan_su_mau.csv");
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }, []);

    const resetForm = useCallback(() => {
      setEditingId(null);
      setForm(emptyForm);
      setMessage("");
      setFieldErrors({});
      setTimeout(() => {
        usernameInputRef.current?.focus();
      }, 50);
    }, []);

    useImperativeHandle(ref, () => ({
      exportExcel,
      resetForm
    }));

    // ── Load editing record if provided from URL ──────────────
    useEffect(() => {
      if (editingIdFromUrl !== null && records.length > 0) {
        const record = records.find((r) => r.id === editingIdFromUrl);
        if (record) {
          setForm({
            username: record.username,
            fullName: record.fullName,
            password: "",
            phone: record.phone ?? "",
            email: record.email ?? "",
            specialtyId: record.specialty?.id ?? null,
            maxShiftsPerMonth: record.maxShiftsPerMonth ?? 5,
            roles: record.roles ?? [],
            status: record.status ?? "ACTIVE",
          });
          setEditingId(record.id);
        }
      }
    }, [editingIdFromUrl, records]);

    // Reset pagination when filter changes
    useEffect(() => {
      setCurrentPage(1);
    }, [searchKeyword, filterRole, filterSpecialty, filterStatus]);

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
        ["Đang làm việc", String(records.filter((r) => r.status === "ACTIVE").length).padStart(2, "0")],
        ["Nghỉ phép", String(records.filter((r) => r.status === "ON_LEAVE").length).padStart(2, "0")],
        ["Đã nghỉ", String(records.filter((r) => r.status === "INACTIVE").length).padStart(2, "0")],
        [
          "Chuyên khoa",
          String(
            new Set(records.map((r) => r.specialty?.name).filter(Boolean)).size,
          ).padStart(2, "0"),
        ],
      ],
      [records],
    );

    // ── Filtered records (client-side search & dropdowns) ─────
    const filteredRecords = useMemo(() => {
      let result = records;

      if (searchKeyword.trim()) {
        const kw = searchKeyword.toLowerCase();
        result = result.filter(
          (r) =>
            r.fullName.toLowerCase().includes(kw) ||
            r.username.toLowerCase().includes(kw) ||
            (r.specialty?.name ?? "").toLowerCase().includes(kw) ||
            r.email.toLowerCase().includes(kw),
        );
      }

      if (filterRole) {
        result = result.filter((r) => r.roles?.includes(filterRole));
      }

      if (filterSpecialty) {
        result = result.filter((r) => r.specialty?.name === filterSpecialty);
      }

      if (filterStatus) {
        result = result.filter((r) => r.status === filterStatus);
      }

      return result;
    }, [records, searchKeyword, filterRole, filterSpecialty, filterStatus]);

    // ── Paginated records ─────────────────────────────────────
    const paginatedRecords = useMemo(() => {
      return filteredRecords.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);
    }, [filteredRecords, currentPage, itemsPerPage]);

    const totalPages = Math.ceil(filteredRecords.length / itemsPerPage) || 1;

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

      if (importFile) {
        try {
          setSubmitting(true);
          const formData = new FormData();
          formData.append("file", importFile);
          await api.post("/staff/import", formData);
          showMessage("Nhập danh sách nhân sự từ tệp thành công!", "success");
          setImportFile(null);
          if (fileInputRef.current) {
            fileInputRef.current.value = "";
          }
          if (showOnlyForm) {
            setTimeout(() => {
              router.push("/staff");
            }, 1500);
          } else {
            await fetchStaff();
          }
        } catch (err) {
          showMessage(err instanceof Error ? err.message : "Lỗi import tệp nhân sự", "error");
        } finally {
          setSubmitting(false);
        }
        return;
      }

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
        
        if (showOnlyForm) {
          setTimeout(() => {
            router.push("/staff");
          }, 1500);
        } else {
          await fetchStaff();
        }
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
        status: record.status ?? "ACTIVE",
      });
      setEditingId(record.id);
      setFieldErrors({});
      showMessage(`Đang sửa ${record.fullName}.`, "info");
      usernameInputRef.current?.focus();
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

    // ── Shared Form Fields ────────────────────────────────────
    const renderFormFields = (isWideLayout: boolean) => (
      <div className={isWideLayout ? "grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4" : "space-y-3"}>
        <label className="block">
          <div className="flex justify-between items-center">
            <span className="text-xs font-medium text-slate-500">Username</span>
            {fieldErrors.username && (
              <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.username}</span>
            )}
          </div>
          <input
            ref={usernameInputRef}
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

        {/* Status Form Dropdown */}
        <label className="block">
          <div className="flex justify-between items-center">
            <span className="text-xs font-medium text-slate-500">Trạng thái</span>
            {fieldErrors.status && (
              <span className="text-[10px] font-semibold text-rose-600">{fieldErrors.status}</span>
            )}
          </div>
          <select
            className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:border-slate-400 cursor-pointer text-slate-700"
            id="staff-field-status"
            onChange={(e) => updateField("status", e.target.value)}
            value={form.status}
          >
            <option value="ACTIVE">Đang làm việc</option>
            <option value="ON_LEAVE">Nghỉ phép</option>
            <option value="INACTIVE">Đã nghỉ</option>
          </select>
        </label>

        <div className={`block ${isWideLayout ? "md:col-span-2" : ""}`}>
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
      </div>
    );

    // ── Render ────────────────────────────────────────────────
    if (showOnlyForm) {
      return (
        <div className="w-full p-5">
          <SectionCard
            description={
              editingId !== null
                ? "Cập nhật thông tin và trạng thái hoạt động"
                : "Điền đầy đủ thông tin để thêm nhân sự mới"
            }
            title={editingId !== null ? "Sửa nhân sự" : "Thêm nhân sự"}
          >
            <form className="space-y-6 p-6" onSubmit={submitStaff}>
              {editingId === null && (
                <div className="space-y-4 pb-4 border-b border-slate-100">
                  <div
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                    className={`border border-dashed rounded-lg p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 transition-colors ${
                      isDragging
                        ? "border-indigo-400 bg-indigo-50/30"
                        : "border-slate-200 bg-slate-50/50 hover:bg-slate-50"
                    }`}
                  >
                    <div className="flex-1">
                      <h3 className="text-sm font-semibold text-slate-800 flex items-center gap-2">
                        <svg className="size-4 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                        </svg>
                        Nhập từ Excel hoặc CSV
                      </h3>
                      <p className="text-xs text-slate-500 mt-1">
                        Tải lên tệp danh sách nhân viên (`.xlsx`, `.xls`, `.csv`). Hệ thống sẽ tự động vô hiệu hóa form nhập tay bên dưới khi có tệp được chọn.
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 shrink-0">
                      <button
                        type="button"
                        onClick={downloadTemplate}
                        className="h-9 inline-flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50 cursor-pointer shadow-sm"
                      >
                        <svg className="size-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        Tải file mẫu (CSV)
                      </button>

                      <div className="relative">
                        <input
                          ref={fileInputRef}
                          type="file"
                          accept=".xlsx,.xls,.csv"
                          className="hidden"
                          onChange={(e) => {
                            const files = e.target.files;
                            if (files && files.length > 0) {
                              setImportFile(files[0]);
                              setFieldErrors({});
                              showMessage(`Đã chọn tệp: ${files[0].name}. Nhấn nút "Import từ tệp" ở dưới để thực hiện.`, "info");
                            }
                          }}
                        />
                        {importFile ? (
                          <div className="flex items-center gap-2">
                            <span className="inline-flex items-center gap-1 px-3 h-9 rounded-md bg-indigo-50 border border-indigo-200 text-xs font-semibold text-indigo-700 max-w-[200px] truncate">
                              <svg className="size-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                              </svg>
                              {importFile.name}
                            </span>
                            <button
                              type="button"
                              onClick={() => {
                                setImportFile(null);
                                if (fileInputRef.current) {
                                  fileInputRef.current.value = "";
                                }
                                showMessage("Đã hủy chọn tệp. Bạn có thể nhập tay.", "info");
                              }}
                              className="h-9 w-9 flex items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-rose-600 transition-colors cursor-pointer shadow-sm"
                              title="Hủy chọn tệp"
                            >
                              <svg className="size-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                              </svg>
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => fileInputRef.current?.click()}
                            className="h-9 inline-flex items-center gap-1.5 rounded-md bg-indigo-600 px-4 text-xs font-semibold text-white transition-colors hover:bg-indigo-700 cursor-pointer shadow-sm shadow-indigo-100"
                          >
                            <svg className="size-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                            </svg>
                            Chọn tệp tin
                          </button>
                        )}
                      </div>
                    </div>
                  </div>

                  {importFile && (
                    <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800 flex items-center gap-2">
                      <svg className="size-4 text-amber-600 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                      </svg>
                      <span>
                        Đang chọn tệp import. Các trường nhập liệu thủ công đã bị khóa. Nhấn <strong>"Import từ tệp"</strong> bên dưới để hoàn tất, hoặc click nút hủy (X) bên cạnh tên file để mở khóa nhập tay.
                      </span>
                    </div>
                  )}
                </div>
              )}
              {renderFormFields(true)}

              {/* Message */}
              {message ? (
                messageType === "error" && message.includes("\n") ? (
                  <div className="rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
                    <p className="font-semibold">{message.split("\n")[0]}</p>
                    <div className="mt-2 max-h-48 overflow-y-auto space-y-1 pl-2 text-xs font-mono">
                      {message.split("\n").slice(1).map((line, idx) => (
                        <div key={idx} className="py-0.5 border-b border-rose-100/50 last:border-0">
                          {line}
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
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
                )
              ) : null}

              {/* Buttons */}
              <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button
                  className="h-9 rounded-md border border-slate-200 px-6 text-sm font-medium transition-colors hover:bg-slate-50 cursor-pointer"
                  id="staff-reset"
                  onClick={() => router.push("/staff")}
                  type="button"
                >
                  Hủy bỏ
                </button>
                <button
                  className="h-9 rounded-md bg-slate-950 px-6 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
                  disabled={submitting}
                  id="staff-submit"
                  type="submit"
                >
                  {submitting ? "Đang lưu…" : editingId !== null ? "Cập nhật" : importFile ? "Import từ tệp" : "Thêm mới"}
                </button>
              </div>
            </form>
          </SectionCard>
        </div>
      );
    }

    const mainListContent = () => (
      <div className="space-y-4">
        {/* Summary Cards */}
        <section className="grid gap-4 grid-cols-2 lg:grid-cols-5">
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

        {/* Staff Table Card */}
        <SectionCard
          description="Dữ liệu được tải từ API backend — thêm, sửa, xóa cập nhật realtime"
          title="Danh sách nhân sự"
        >
          {/* Top Filters Bar (matching Image 2) */}
          <div className="grid gap-3 p-4 border-b border-slate-200/60 bg-slate-50/50 sm:grid-cols-2 md:grid-cols-4">
            {/* Search Bar */}
            <div className="relative">
              <span className="absolute inset-y-0 left-3 flex items-center text-slate-400">
                <svg className="size-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              </span>
              <input
                className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-xs outline-none focus:border-slate-400 focus:ring-1 focus:ring-slate-400 transition-all placeholder:text-slate-400"
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder="Tìm kiếm theo tên hoặc mã NV..."
                value={searchKeyword}
              />
            </div>

            {/* Filter Chức vụ (Vai trò) */}
            <select
              className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-xs outline-none focus:border-slate-400 cursor-pointer text-slate-600"
              onChange={(e) => setFilterRole(e.target.value)}
              value={filterRole}
            >
              <option value="">Tất cả Vai trò</option>
              <option value="ADMIN">ADMIN</option>
              <option value="MANAGER">MANAGER</option>
              <option value="STAFF">STAFF</option>
            </select>

            {/* Filter Khoa/Phòng (Chuyên khoa) */}
            <select
              className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-xs outline-none focus:border-slate-400 cursor-pointer text-slate-600"
              onChange={(e) => setFilterSpecialty(e.target.value)}
              value={filterSpecialty}
            >
              <option value="">Tất cả Chuyên khoa</option>
              {specialties.map(spec => (
                <option key={spec.id} value={spec.name}>{spec.name}</option>
              ))}
            </select>

            {/* Filter Trạng thái */}
            <select
              className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-xs outline-none focus:border-slate-400 cursor-pointer text-slate-600"
              onChange={(e) => setFilterStatus(e.target.value)}
              value={filterStatus}
            >
              <option value="">Tất cả Trạng thái</option>
              <option value="ACTIVE">Đang làm việc</option>
              <option value="ON_LEAVE">Nghỉ phép</option>
              <option value="INACTIVE">Đã nghỉ</option>
            </select>
          </div>

          {/* Table List */}
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
                <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  <tr>
                    {["Nhân viên", "Mã NV", "Vai trò", "Chuyên khoa", "SĐT", "Trạng thái", "Thao tác"].map(
                      (header) => (
                        <th className="border-b border-slate-200 px-6 py-4 font-semibold" key={header}>
                          {header}
                        </th>
                      ),
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {paginatedRecords.length === 0 ? (
                    <tr>
                      <td className="px-6 py-12 text-center text-sm text-slate-400" colSpan={7}>
                        {searchKeyword || filterRole || filterSpecialty || filterStatus 
                          ? "Không tìm thấy nhân sự phù hợp với bộ lọc" 
                          : "Chưa có nhân sự nào"}
                      </td>
                    </tr>
                  ) : (
                    paginatedRecords.map((record) => (
                      <tr
                        className="transition-colors hover:bg-slate-50/40"
                        key={record.id}
                      >
                        {/* Nhân viên Column (Initials Avatar + Name & Email) */}
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-3">
                            <div className="size-9 rounded-full flex items-center justify-center font-bold text-xs text-indigo-700 bg-indigo-50 border border-indigo-100 shrink-0">
                              {getInitials(record.fullName)}
                            </div>
                            <div>
                              <div className="font-semibold text-slate-800 text-sm leading-tight">{record.fullName}</div>
                              <div className="text-xs text-slate-400 mt-0.5">{record.email}</div>
                            </div>
                          </div>
                        </td>

                        {/* Mã NV (Username) */}
                        <td className="px-6 py-4 font-medium text-slate-600">{record.username}</td>

                        {/* Vai trò */}
                        <td className="px-6 py-4">
                          <div className="flex flex-wrap gap-1">
                            {record.roles?.length > 0
                              ? record.roles.map((role) => (
                                  <span
                                    className="inline-flex items-center rounded bg-indigo-50/50 px-2 py-0.5 text-xs font-semibold text-indigo-700 border border-indigo-100/50"
                                    key={role}
                                  >
                                    {role}
                                  </span>
                                ))
                              : "-"}
                          </div>
                        </td>

                        {/* Chuyên khoa */}
                        <td className="px-6 py-4 text-slate-600">{record.specialty?.name ?? "-"}</td>

                        {/* Số điện thoại */}
                        <td className="px-6 py-4 font-medium text-slate-600">{record.phone ?? "-"}</td>

                        {/* Trạng thái (Pill with dot matching Image 2) */}
                        <td className="px-6 py-4">
                          {record.status === "ACTIVE" ? (
                            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 border border-emerald-200">
                              <span className="size-1.5 rounded-full bg-emerald-500" />
                              Đang làm việc
                            </span>
                          ) : record.status === "ON_LEAVE" ? (
                            <span className="inline-flex items-center gap-1.5 rounded-full bg-orange-50 px-2.5 py-1 text-xs font-semibold text-orange-700 border border-orange-200">
                              <span className="size-1.5 rounded-full bg-orange-500" />
                              Nghỉ phép
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600 border border-slate-200">
                              <span className="size-1.5 rounded-full bg-slate-400" />
                              Đã nghỉ
                            </span>
                          )}
                        </td>

                        {/* Thao tác */}
                        <td className="px-6 py-4">
                          <div className="flex gap-2">
                            <button
                              className="h-8 rounded-md border border-slate-200 px-3 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 cursor-pointer"
                              id={`edit-staff-${record.id}`}
                              onClick={() => {
                                if (isReadOnlyView) {
                                  router.push(`/staff/create?id=${record.id}`);
                                } else {
                                  editStaff(record);
                                }
                              }}
                              type="button"
                            >
                              Sửa
                            </button>
                            <button
                              className="h-8 rounded-md border border-rose-100 bg-rose-50/10 px-3 text-xs font-medium text-rose-600 transition-colors hover:bg-rose-50 hover:text-rose-700 cursor-pointer"
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

          {/* Pagination Footer (matching Image 2) */}
          <div className="flex items-center justify-between border-t border-slate-200 px-6 py-4 bg-white rounded-b-lg flex-wrap gap-4">
            <div className="flex items-center gap-4 flex-wrap">
              <p className="text-xs text-slate-500 font-medium">
                Hiển thị <span className="font-semibold text-slate-700">{filteredRecords.length === 0 ? 0 : (currentPage - 1) * itemsPerPage + 1}</span> đến <span className="font-semibold text-slate-700">{Math.min(currentPage * itemsPerPage, filteredRecords.length)}</span> trong số <span className="font-semibold text-slate-700">{filteredRecords.length}</span> nhân viên
              </p>
              
              <div className="flex items-center gap-1.5 text-xs text-slate-500 font-medium border-l border-slate-200 pl-4 max-sm:border-l-0 max-sm:pl-0">
                <span>Số dòng:</span>
                <select
                  value={itemsPerPage}
                  onChange={(e) => {
                    setItemsPerPage(Number(e.target.value));
                    setCurrentPage(1);
                  }}
                  className="h-7 rounded-md border border-slate-200 bg-white px-2 text-xs outline-none focus:border-slate-400 cursor-pointer text-slate-700 font-semibold"
                >
                  <option value={10}>10 / trang</option>
                  <option value={20}>20 / trang</option>
                  <option value={50}>50 / trang</option>
                  <option value={100}>100 / trang</option>
                </select>
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                className="h-8 w-8 flex items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-all cursor-pointer"
                disabled={currentPage === 1}
                onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
                type="button"
              >
                &lt;
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                <button
                  className={`h-8 w-8 flex items-center justify-center rounded-md text-xs font-semibold transition-all cursor-pointer ${
                    page === currentPage
                      ? "bg-indigo-600 text-white shadow-sm shadow-indigo-100"
                      : "border border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                  }`}
                  key={page}
                  onClick={() => setCurrentPage(page)}
                  type="button"
                >
                  {page}
                </button>
              ))}
              <button
                className="h-8 w-8 flex items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-all cursor-pointer"
                disabled={currentPage === totalPages}
                onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
                type="button"
              >
                &gt;
              </button>
            </div>
          </div>
        </SectionCard>
      </div>
    );

    if (isReadOnlyView) {
      return (
        <div className="p-5 max-sm:p-3">
          {mainListContent()}
        </div>
      );
    }

    // Default split view fallback
    return (
      <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_380px]">
        {mainListContent()}
        <aside className="space-y-4">
          <SectionCard
            description={
              editingId !== null
                ? "Cập nhật thông tin — mật khẩu trống = giữ nguyên"
                : "Điền đầy đủ thông tin để thêm nhân sự mới"
            }
            title={editingId !== null ? "Sửa nhân sự" : "Thêm nhân sự"}
          >
            <form className="space-y-3 p-4" onSubmit={submitStaff}>
              {renderFormFields(false)}

              {/* Message */}
              {message ? (
                messageType === "error" && message.includes("\n") ? (
                  <div className="rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
                    <p className="font-semibold">{message.split("\n")[0]}</p>
                    <div className="mt-2 max-h-48 overflow-y-auto space-y-1 pl-2 text-xs font-mono">
                      {message.split("\n").slice(1).map((line, idx) => (
                        <div key={idx} className="py-0.5 border-b border-rose-100/50 last:border-0">
                          {line}
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
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
                )
              ) : null}

              {/* Buttons */}
              <div className="grid grid-cols-2 gap-2">
                <button
                  className="h-9 rounded-md bg-slate-950 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
                  disabled={submitting}
                  id="staff-submit"
                  type="submit"
                >
                  {submitting ? "Đang lưu…" : editingId !== null ? "Cập nhật" : importFile ? "Import từ tệp" : "Thêm mới"}
                </button>
                <button
                  className="h-9 rounded-md border border-slate-200 text-sm font-medium transition-colors hover:bg-slate-50 cursor-pointer"
                  id="staff-reset"
                  onClick={resetForm}
                  type="button"
                >
                  Hủy bỏ
                </button>
              </div>
            </form>
          </SectionCard>
        </aside>
      </div>
    );
  }
);

StaffCrudPanel.displayName = "StaffCrudPanel";
