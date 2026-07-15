"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { FormInput, FormTextarea, Button, ConfirmDialog, Pagination } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import type { ApiResponse, Specialty } from "@/types/api";

const DEFAULT_PAGE_SIZE = 10;

type SpecialtyCrudPanelProps = {
  onBack?: () => void;
};

type SpecialtyFormData = {
  name: string;
  description: string;
};

const emptyForm: SpecialtyFormData = {
  name: "",
  description: "",
};

function getInitials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function SpecialtyCrudPanel({ onBack }: SpecialtyCrudPanelProps) {
  const [records, setRecords] = useState<Specialty[]>([]);
  const [form, setForm] = useState<SpecialtyFormData>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | "active" | "inactive">("all");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string | undefined>>({});
  const [confirmDelete, setConfirmDelete] = useState<{ id: number; name: string } | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusCounts, setStatusCounts] = useState({ total: 0, ACTIVE: 0, INACTIVE: 0 });

  const fetchSpecialties = useCallback(async () => {
    try {
      setLoading(true);
      const pageResult = await api.getPage<Specialty>("/specialties/page", { page, size: pageSize });
      setRecords(pageResult.content ?? []);
      setTotalPages(pageResult.totalPages ?? 0);
      setTotalElements(pageResult.totalElements ?? 0);
    } catch {
      toast.error("Không thể tải danh sách chuyên khoa. Vui lòng kiểm tra kết nối backend.");
      setRecords([]);
    } finally {
      setLoading(false);
    }
  }, [toast, page, pageSize]);

  const fetchStatusCounts = useCallback(async () => {
    try {
      const res = await api.get<ApiResponse<Record<string, number>>>("/specialties/status-counts");
      const data = (res?.data ?? {}) as Record<string, number>;
      setStatusCounts({
        total: data.total ?? 0,
        ACTIVE: data.ACTIVE ?? 0,
        INACTIVE: data.INACTIVE ?? 0,
      });
    } catch {
      // Fall back to zeros — UI gracefully degrades.
    }
  }, []);

  useEffect(() => {
    fetchSpecialties();
    fetchStatusCounts();
  }, [fetchSpecialties, fetchStatusCounts]);

  // Safety net: ensure form is closed on initial mount
  useEffect(() => {
    setFormOpen(false);
  }, []);

  const filteredRecords = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase();
    return records.filter((record) => {
      const matchesKeyword = !keyword || record.name.toLowerCase().includes(keyword);
      const matchesStatus =
        statusFilter === "all" ||
        (statusFilter === "active" && record.isActive) ||
        (statusFilter === "inactive" && !record.isActive);
      return matchesKeyword && matchesStatus;
    });
  }, [records, searchKeyword, statusFilter]);

  function updateField(field: keyof SpecialtyFormData, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function openAddForm() {
    setForm(emptyForm);
    setEditingId(null);
    setFormOpen(true);
    setFieldErrors({});
  }

  function editSpecialty(record: Specialty) {
    setForm({
      name: record.name,
      description: record.description ?? "",
    });
    setEditingId(record.id);
    setFormOpen(true);
    setFieldErrors({});
  }

  function closeForm() {
    setFormOpen(false);
    setEditingId(null);
    setForm(emptyForm);
    setFieldErrors({});
  }

  function validate(): boolean {
    const errors: Record<string, string | undefined> = {};
    if (!form.name.trim()) {
      errors.name = "Tên chuyên khoa không được để trống.";
    } else if (form.name.trim().length > 50) {
      errors.name = "Tên chuyên khoa không quá 50 ký tự.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function submitSpecialty(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!validate()) return;

    try {
      setSubmitting(true);
      if (editingId !== null) {
        await api.updateSpecialty(editingId, {
          name: form.name.trim(),
          description: form.description.trim() || undefined,
        });
        toast.success(`Đã cập nhật chuyên khoa "${form.name}".`);
      } else {
        await api.createSpecialty({
          name: form.name.trim(),
          description: form.description.trim() || undefined,
        });
        toast.success(`Đã thêm chuyên khoa "${form.name}".`);
      }
      closeForm();
      await fetchSpecialties();
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi lưu chuyên khoa"));
    } finally {
      setSubmitting(false);
    }
  }

  function requestDelete(id: number, name: string) {
    setConfirmDelete({ id, name });
  }

  async function confirmDeleteSpecialty() {
    if (!confirmDelete) return;
    const { id, name } = confirmDelete;
    setConfirmDelete(null);
    try {
      await api.deleteSpecialty(id);
      toast.success(`Đã xóa chuyên khoa "${name}".`);
      await fetchSpecialties();
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi xóa chuyên khoa"));
    }
  }

  const activeCount = statusCounts.ACTIVE;
  const inactiveCount = statusCounts.INACTIVE;

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <nav aria-label="Đường dẫn" className="flex items-center gap-2 text-label-md text-on-surface-variant">
        <button
          type="button"
          onClick={onBack}
          className="flex items-center gap-1 hover:text-primary transition-colors cursor-pointer"
        >
          <span className="material-symbols-outlined text-[18px]">groups</span>
          Nhân sự
        </button>
        <span className="material-symbols-outlined text-[16px]">chevron_right</span>
        <span className="text-on-surface font-medium">Chuyên khoa</span>
      </nav>

      {/* Slide-in Drawer - only render when open */}
      {formOpen && (
      <div
        aria-label="Form chuyên khoa"
        aria-modal="true"
        className="fixed inset-0 z-50 flex justify-end pointer-events-auto"
        role="dialog"
      >
        <div
          className="absolute inset-0 bg-black/40 transition-opacity duration-300 opacity-100"
          onClick={closeForm}
        />
        <div
          className="relative flex flex-col w-full max-w-[420px] h-full bg-surface-container-lowest shadow-sm transition-transform duration-300 ease-out translate-x-0"
        >
          <div className="flex items-center justify-between px-6 py-5 border-b border-outline-variant shrink-0">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-container">
                <span className="material-symbols-outlined text-primary text-[20px]">
                  {editingId !== null ? "edit" : "add"}
                </span>
              </div>
              <div>
                <h2 className="text-headline-lg font-semibold text-on-surface">
                  {editingId !== null ? "Sửa chuyên khoa" : "Thêm chuyên khoa"}
                </h2>
                <p className="text-label-md text-on-surface-variant">
                  {editingId !== null ? "Cập nhật thông tin chuyên khoa" : "Nhập thông tin chuyên khoa mới"}
                </p>
              </div>
            </div>
            <button
              aria-label="Đóng biểu mẫu"
              className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors"
              onClick={closeForm}
              type="button"
            >
              <span className="material-symbols-outlined text-[20px]">close</span>
            </button>
          </div>

          <div className="flex-1 overflow-y-auto px-6 py-5">
            <form className="flex flex-col gap-5" id="specialty-drawer-form" onSubmit={submitSpecialty} noValidate>
              <FormInput
                label="Tên chuyên khoa"
                name="name"
                placeholder="VD: Ngoại, Nội, Sản, Nhi..."
                value={form.name}
                onChange={(e) => {
                  updateField("name", e.target.value);
                  if (fieldErrors.name) setFieldErrors((f) => ({ ...f, name: undefined }));
                }}
                error={fieldErrors.name}
                required
                disabled={submitting}
                maxLength={50}
              />
              <FormTextarea
                label="Mô tả"
                name="description"
                placeholder="Mô tả chi tiết về chuyên khoa (tùy chọn)"
                value={form.description}
                onChange={(e) => updateField("description", e.target.value)}
                rows={3}
                disabled={submitting}
              />
            </form>
          </div>

          <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant shrink-0">
            <Button variant="secondary" onClick={closeForm} disabled={submitting}>
              Hủy bỏ
            </Button>
            <Button
              variant="primary"
              form="specialty-drawer-form"
              type="submit"
              loading={submitting}
              icon={<span className="material-symbols-outlined" aria-hidden="true">save</span>}
              fullWidth
            >
              {editingId !== null ? "Cập nhật" : "Lưu chuyên khoa"}
            </Button>
          </div>
        </div>
      </div>
      )}

      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 md:p-5 shadow-sm sm:flex-row sm:items-center">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-fixed">
            <span className="material-symbols-outlined text-[20px] text-primary">stethoscope</span>
          </div>
          <div>
            <p className="text-label-sm text-on-surface-variant">Chuyên khoa</p>
            <p className="mt-0.5 text-body-sm text-on-surface-variant leading-relaxed max-w-lg">
              Quản lý danh sách chuyên khoa trong bệnh viện. Các chuyên khoa được dùng để phân loại nhân sự và lịch trực.
            </p>
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-3">
          <button
            className="flex items-center gap-2 rounded-lg bg-primary px-4 h-10 text-label-md font-medium text-on-primary shadow-sm transition-all duration-200 hover:bg-primary/90 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            onClick={openAddForm}
            type="button"
          >
            <span aria-hidden="true" className="material-symbols-outlined text-[18px]">add</span>
            Thêm chuyên khoa
          </button>
        </div>
      </section>

      {/* Stats */}
      <section className="grid grid-cols-3 gap-3">
        <div className="group relative flex items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm transition-all duration-200 hover:bg-surface-container-low">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-fixed transition-transform duration-200 group-hover:scale-105">
            <span className="material-symbols-outlined text-[20px] text-primary">stethoscope</span>
          </div>
          <div className="min-w-0">
            <p className="text-label-sm text-on-surface-variant">Tổng chuyên khoa</p>
            <p className="mt-0.5 text-headline-lg font-bold leading-none text-on-surface">{statusCounts.total}</p>
          </div>
        </div>
        <div className="group relative flex items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm transition-all duration-200 hover:bg-surface-container-low">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-secondary-container transition-transform duration-200 group-hover:scale-105">
            <span className="material-symbols-outlined text-[20px] text-secondary">check_circle</span>
          </div>
          <div className="min-w-0">
            <p className="text-label-sm text-on-surface-variant">Đang hoạt động</p>
            <p className="mt-0.5 text-headline-lg font-bold leading-none text-secondary">{activeCount}</p>
          </div>
        </div>
        <div className="group relative flex items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm transition-all duration-200 hover:bg-surface-container-low">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-container-high transition-transform duration-200 group-hover:scale-105">
            <span className="material-symbols-outlined text-[20px] text-outline">block</span>
          </div>
          <div className="min-w-0">
            <p className="text-label-sm text-on-surface-variant">Không hoạt động</p>
            <p className="mt-0.5 text-headline-lg font-bold leading-none text-outline">{inactiveCount}</p>
          </div>
        </div>
      </section>

      {/* Filter */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm flex flex-wrap lg:flex-nowrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px]">
          <span aria-hidden="true" className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">
            search
          </span>
          <input
            aria-label="Tìm kiếm chuyên khoa"
            autoComplete="off"
            className="w-full rounded-lg border border-transparent bg-surface-container-low py-2.5 pl-9 pr-3 text-body-sm text-on-surface transition-all placeholder:text-outline focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            name="specialtySearch"
            onChange={(e) => { setSearchKeyword(e.target.value); setPage(0); }}
            placeholder="Tìm kiếm chuyên khoa..."
            value={searchKeyword}
          />
        </div>

        <div className="flex items-center gap-1 p-1 bg-surface-container-low rounded-lg">
          {(["all", "active", "inactive"] as const).map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => { setStatusFilter(status); setPage(0); }}
              className={`px-4 py-2 rounded-md text-label-md font-medium transition-all ${
                statusFilter === status
                  ? "bg-primary text-on-primary shadow-sm"
                  : "text-on-surface-variant hover:bg-surface-container-high"
              }`}
            >
              {status === "all" ? "Tất cả" : status === "active" ? "Hoạt động" : "Không hoạt động"}
            </button>
          ))}
        </div>
      </section>

      {/* Table */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden flex flex-col">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="flex items-center justify-center py-16">
              <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : (
            <table className="w-full border-collapse text-left" aria-label="Specialty Table">
              <thead className="bg-surface-container-low border-b border-outline-variant">
                <tr>
                  <th scope="col" className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Chuyên khoa</th>
                  <th scope="col" className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Mô tả</th>
                  <th scope="col" className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Ngày tạo</th>
                  <th scope="col" className="px-4 py-3 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Trạng thái</th>
                  <th scope="col" className="px-4 py-3 text-right text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {filteredRecords.length === 0 ? (
                  <tr>
                    <td className="px-4 py-12 text-center text-body-sm text-on-surface-variant" colSpan={5}>
                      {searchKeyword || statusFilter !== "all"
                        ? "Không tìm thấy chuyên khoa phù hợp"
                        : "Chưa có chuyên khoa nào. Nhấn \"Thêm chuyên khoa\" để bắt đầu."}
                    </td>
                  </tr>
                ) : (
                  filteredRecords.map((record) => (
                    <tr className="group transition-colors hover:bg-surface-container-lowest h-14" key={record.id}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-fixed font-bold text-label-sm text-primary">
                            {getInitials(record.name)}
                          </div>
                          <p className="text-label-md font-semibold text-on-surface">{record.name}</p>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-label-md text-on-surface-variant max-w-xs truncate">
                          {record.description || "—"}
                        </p>
                      </td>
                      <td className="px-4 py-3 text-label-md text-on-surface-variant">
                        {new Date(record.createdAt).toLocaleDateString("vi-VN")}
                      </td>
                      <td className="px-4 py-3">
                        {record.isActive ? (
                          <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary-container px-3 py-1 text-label-sm font-medium text-secondary border border-secondary/20">
                            <span className="h-1.5 w-1.5 rounded-full bg-secondary" />
                            Hoạt động
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-container-highest px-3 py-1 text-label-sm font-medium text-outline border border-outline-variant">
                            Không hoạt động
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1 opacity-80 group-hover:opacity-100 transition-opacity">
                          <button
                            aria-label={`Sửa ${record.name}`}
                            className="flex h-8 w-8 items-center justify-center rounded-lg text-outline hover:text-primary hover:bg-surface-container transition-colors"
                            onClick={() => editSpecialty(record)}
                            title="Sửa"
                            type="button"
                          >
                            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">edit</span>
                          </button>
                          <button
                            aria-label={`Xóa ${record.name}`}
                            className="flex h-8 w-8 items-center justify-center rounded-lg text-outline hover:text-error hover:bg-error-container transition-colors"
                            onClick={() => requestDelete(record.id, record.name)}
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
        {!loading && records.length > 0 && (
          <Pagination
            currentPage={page + 1}
            totalPages={totalPages}
            totalItems={totalElements}
            pageSize={pageSize}
            onPageChange={(p) => setPage(p - 1)}
            onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
          />
        )}
      </section>

      <ConfirmDialog
        open={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={confirmDeleteSpecialty}
        title="Xóa chuyên khoa?"
        description={
          confirmDelete
            ? `Bạn có chắc muốn xóa chuyên khoa "${confirmDelete.name}"? Hành động này có thể ảnh hưởng đến nhân sự thuộc chuyên khoa này.`
            : ""
        }
        confirmLabel="Xóa"
        cancelLabel="Hủy"
        variant="danger"
      />
    </div>
  );
}
