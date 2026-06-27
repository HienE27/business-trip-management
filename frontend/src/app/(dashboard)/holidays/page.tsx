"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAuth } from "@/components/auth/AuthProvider";
import { useToast } from "@/hooks/useToast";
import { Button, ConfirmDialog } from "@/components/ui";
import { BackButton } from "@/components/ui/BackButton";

type Holiday = {
  id: number;
  name: string;
  holidayDate: string;
  year: number;
  isNationalHoliday: boolean;
  description: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

type HolidayForm = {
  name: string;
  holidayDate: string;
  isNationalHoliday: boolean;
  description: string;
};

const STATUS_CLASS: Record<string, string> = {
  true: "bg-secondary-container text-on-secondary-container border border-secondary/20",
  false: "bg-surface-container-high text-on-surface-variant border border-outline-variant",
};

function getYearOptions() {
  const current = new Date().getFullYear();
  return [current - 1, current, current + 1, current + 2];
}

function HolidaysContent() {
  const { user } = useAuth();
  const isAdmin = user?.roles?.some((r) => r === "ADMIN") ?? false;
  const toast = useToast();
  const ignoreRef = useRef(false);

  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [loading, setLoading] = useState(true);
  const [yearFilter, setYearFilter] = useState(String(new Date().getFullYear()));
  const [typeFilter, setTypeFilter] = useState<string>("");
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [form, setForm] = useState<HolidayForm>({
    name: "",
    holidayDate: "",
    isNationalHoliday: false,
    description: "",
  });

  const fetchHolidays = useCallback(async () => {
    try {
      const res = await api.get<Holiday[]>("/holidays");
      setHolidays(res);
    } catch (err) {
      toast.error(getErrorMessage(err, "Không thể tải danh sách ngày lễ"));
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchHolidays();
  }, [fetchHolidays]);

  const filtered = holidays.filter((h) => {
    if (String(h.year) !== yearFilter && yearFilter !== "all") return false;
    if (typeFilter === "national" && !h.isNationalHoliday) return false;
    if (typeFilter === "special" && h.isNationalHoliday) return false;
    return h.isActive;
  });

  const openAdd = () => {
    setEditingId(null);
    setForm({ name: "", holidayDate: "", isNationalHoliday: false, description: "" });
    setShowModal(true);
  };

  const openEdit = (h: Holiday) => {
    setEditingId(h.id);
    setForm({
      name: h.name,
      holidayDate: h.holidayDate,
      isNationalHoliday: h.isNationalHoliday ?? false,
      description: h.description ?? "",
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.holidayDate) {
      toast.error("Vui lòng nhập đầy đủ thông tin bắt buộc");
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        name: form.name.trim(),
        holidayDate: form.holidayDate,
        isNationalHoliday: form.isNationalHoliday,
        description: form.description.trim() || null,
      };
      if (editingId) {
        await api.put(`/holidays/${editingId}`, payload);
        toast.success("Cập nhật ngày lễ thành công");
      } else {
        await api.post("/holidays", payload);
        toast.success("Thêm ngày lễ thành công");
      }
      setShowModal(false);
      await fetchHolidays();
    } catch (err) {
      toast.error(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await api.delete(`/holidays/${deleteId}`);
      toast.success("Xóa ngày lễ thành công");
      setDeleteId(null);
      await fetchHolidays();
    } catch (err) {
      toast.error(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setDeleting(false);
    }
  };

  const formatDateDisplay = (dateStr: string) => {
    try {
      const d = new Date(dateStr + "T00:00:00");
      return d.toLocaleDateString("vi-VN", { weekday: "long", year: "numeric", month: "long", day: "numeric" });
    } catch {
      return dateStr;
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-10 w-36" />
        </div>
        <Skeleton className="h-12 w-full" />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />

      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-headline-lg font-semibold text-on-surface">Ngày lễ</h1>
          <p className="text-body-sm text-on-surface-variant mt-1">
            Quản lý ngày lễ quốc gia và ngày nghỉ đặc biệt
          </p>
        </div>
        {isAdmin && (
          <Button
            variant="primary"
            size="md"
            icon={<span className="material-symbols-outlined text-[18px]">add</span>}
            onClick={openAdd}
          >
            Thêm ngày lễ
          </Button>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="flex items-center gap-2">
          <label className="text-label-sm text-on-surface-variant">Năm:</label>
          <select
            value={yearFilter}
            onChange={(e) => setYearFilter(e.target.value)}
            className="h-9 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-md text-on-surface focus:border-primary focus:ring-1 focus:ring-primary/20 focus:outline-none cursor-pointer"
          >
            <option value="all">Tất cả năm</option>
            {getYearOptions().map((y) => (
              <option key={y} value={String(y)}>{y}</option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-label-sm text-on-surface-variant">Loại:</label>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="h-9 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-md text-on-surface focus:border-primary focus:ring-1 focus:ring-primary/20 focus:outline-none cursor-pointer"
          >
            <option value="">Tất cả</option>
            <option value="national">Ngày lễ quốc gia</option>
            <option value="special">Ngày nghỉ đặc biệt</option>
          </select>
        </div>
        <p className="text-label-sm text-on-surface-variant ml-auto">
          {filtered.length} ngày lễ
        </p>
      </div>

      {/* Table */}
      {filtered.length === 0 ? (
        <EmptyState
          icon="celebration"
          title="Không có ngày lễ"
          description="Thêm ngày lễ mới để quản lý lịch trực chính xác hơn."
          action={
            isAdmin ? (
              <Button variant="primary" size="md" icon={<span className="material-symbols-outlined text-[18px]">add</span>} onClick={openAdd}>
                Thêm ngày lễ
              </Button>
            ) : undefined
          }
        />
      ) : (
        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-surface-container-low border-b border-outline-variant">
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Tên ngày lễ</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Ngày</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Năm</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Loại</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Mô tả</th>
                  {isAdmin && <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold text-right">Thao tác</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {filtered.map((h) => (
                  <tr key={h.id} className="hover:bg-surface-container-low transition-colors">
                    <td className="px-5 py-3">
                      <p className="text-label-md text-on-surface font-medium">{h.name}</p>
                    </td>
                    <td className="px-5 py-3">
                      <p className="text-label-sm text-on-surface">{formatDateDisplay(h.holidayDate)}</p>
                    </td>
                    <td className="px-5 py-3">
                      <span className="text-label-sm text-on-surface-variant">{h.year}</span>
                    </td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold ${STATUS_CLASS[String(h.isNationalHoliday)]}`}>
                        <span className="material-symbols-outlined text-[12px] mr-1" aria-hidden="true">
                          {h.isNationalHoliday ? "flag" : "stars"}
                        </span>
                        {h.isNationalHoliday ? "Quốc gia" : "Đặc biệt"}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <p className="text-label-sm text-on-surface-variant max-w-xs truncate" title={h.description ?? ""}>
                        {h.description || "—"}
                      </p>
                    </td>
                    {isAdmin && (
                      <td className="px-5 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => openEdit(h)}
                            className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant hover:text-primary transition-colors"
                            title="Chỉnh sửa"
                          >
                            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit</span>
                          </button>
                          <button
                            onClick={() => setDeleteId(h.id)}
                            className="p-1.5 rounded-lg hover:bg-error-container text-on-surface-variant hover:text-error transition-colors"
                            title="Xóa"
                          >
                            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">delete</span>
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Add/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
              <h2 className="text-title-lg font-semibold text-on-surface">
                {editingId ? "Chỉnh sửa ngày lễ" : "Thêm ngày lễ mới"}
              </h2>
              <button
                onClick={() => setShowModal(false)}
                className="p-1 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>
            <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">
                  Tên ngày lễ <span className="text-error">*</span>
                </label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder="VD: Giải phóng miền Nam"
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">
                  Ngày nghỉ <span className="text-error">*</span>
                </label>
                <input
                  type="date"
                  value={form.holidayDate}
                  onChange={(e) => setForm((f) => ({ ...f, holidayDate: e.target.value }))}
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.isNationalHoliday}
                    onChange={(e) => setForm((f) => ({ ...f, isNationalHoliday: e.target.checked }))}
                    className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
                  />
                  <span className="text-label-sm text-on-surface">Ngày lễ quốc gia</span>
                </label>
              </div>
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">Mô tả</label>
                <textarea
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="Ghi chú thêm (tùy chọn)"
                  rows={2}
                  className="w-full p-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all resize-none rounded-lg"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="secondary" onClick={() => setShowModal(false)}>
                  Hủy
                </Button>
                <Button type="submit" variant="primary" loading={submitting}>
                  {editingId ? "Lưu thay đổi" : "Thêm mới"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirm */}
      <ConfirmDialog
        open={deleteId !== null}
        onClose={() => setDeleteId(null)}
        onConfirm={handleDelete}
        title="Xác nhận xóa ngày lễ?"
        description="Hành động này sẽ ẩn ngày lễ này khỏi hệ thống. Dữ liệu không bị xóa vĩnh viễn."
        confirmLabel="Xóa"
        variant="danger"
        loading={deleting}
      />
    </div>
  );
}

export default function HolidaysPage() {
  return (
      <HolidaysContent />
  );
}
