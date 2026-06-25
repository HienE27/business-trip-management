"use client";

import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/errors";
import { formatDate } from "@/lib/date";
import type { SchedulePeriod } from "@/types/api";
import type { PeriodFormValues } from "./PeriodFormModal";

// Lazy-load the modal + confirm dialog. They only need to be in the
// bundle after the user clicks "Tạo" or "Lưu trữ/Xóa", so deferring
// them shaves ~5 KB off the initial /periods payload. The form modal
// pulls in Modal + ModalFooter + form inputs; ConfirmDialog brings
// its own dialog plumbing.
const PeriodFormModal = dynamic(
  () => import("./PeriodFormModal").then((m) => m.PeriodFormModal),
  { ssr: false },
);
const ConfirmDialog = dynamic(
  () => import("@/components/ui/ConfirmDialog").then((m) => m.ConfirmDialog),
  { ssr: false },
);

const STATUS_LABELS: Record<string, { label: string; color: string; bg: string; dot: string }> = {
  DRAFT: { label: "Bản nháp", color: "text-on-surface", bg: "bg-surface-container text-on-surface", dot: "bg-outline" },
  PUBLISHED: { label: "Đã công bố", color: "text-secondary", bg: "bg-secondary-container text-on-secondary-container", dot: "bg-secondary" },
  ARCHIVED: { label: "Đã lưu trữ", color: "text-outline", bg: "bg-surface-container-high text-outline", dot: "bg-outline-variant" },
};

export default function PeriodsPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingPeriod, setEditingPeriod] = useState<SchedulePeriod | null>(null);
  const [saving, setSaving] = useState(false);
  const [publishingId, setPublishingId] = useState<number | null>(null);
  const [archivingId, setArchivingId] = useState<number | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const [deleteAction, setDeleteAction] = useState<"archive" | "delete">("archive");

  // Form state
  const [formValues, setFormValues] = useState<PeriodFormValues>({
    name: "",
    startDate: "",
    endDate: "",
  });
  const [formError, setFormError] = useState<string | null>(null);

  // Filter state
  const [statusFilter, setStatusFilter] = useState<"ALL" | "DRAFT" | "PUBLISHED" | "ARCHIVED">("ALL");
  const [searchQuery, setSearchQuery] = useState("");

  const loadPeriods = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await api.getAllPeriods();
      setPeriods(data?.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, "Không thể tải danh sách kỳ lịch."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPeriods();
  }, [loadPeriods]);

  useAutoDismiss(message, () => setMessage(null));
  useAutoDismiss(error, () => setError(null));

  const openCreateModal = () => {
    setEditingPeriod(null);
    setFormValues({ name: "", startDate: "", endDate: "" });
    setFormError(null);
    setShowModal(true);
  };

  const openEditModal = (p: SchedulePeriod) => {
    if (p.status !== "DRAFT") return;
    setEditingPeriod(p);
    setFormValues({
      name: p.periodName,
      startDate: p.startDate,
      endDate: p.endDate,
    });
    setFormError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!formValues.name.trim() || !formValues.startDate || !formValues.endDate) {
      setFormError("Vui lòng nhập đầy đủ thông tin bắt buộc.");
      return;
    }
    if (formValues.startDate > formValues.endDate) {
      setFormError("Ngày bắt đầu phải trước ngày kết thúc.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const payload = {
        periodName: formValues.name.trim(),
        startDate: formValues.startDate,
        endDate: formValues.endDate,
      };
      if (editingPeriod) {
        await api.updatePeriod(editingPeriod.id, payload);
        setMessage("Cập nhật kỳ lịch thành công.");
      } else {
        await api.createPeriod(payload);
        setMessage("Tạo kỳ lịch thành công.");
      }
      setShowModal(false);
      void loadPeriods();
    } catch (err) {
      setFormError(getErrorMessage(err, "Lưu thất bại."));
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async (id: number) => {
    setPublishingId(id);
    setError(null);
    setMessage(null);
    try {
      await api.publishPeriod(id);
      setMessage("Công bố kỳ lịch thành công.");
      void loadPeriods();
    } catch (err) {
      setError(getErrorMessage(err, "Công bố thất bại."));
    } finally {
      setPublishingId(null);
    }
  };

  const handleArchive = async (id: number) => {
    setArchivingId(id);
    setError(null);
    setMessage(null);
    try {
      await api.archivePeriod(id);
      setMessage("Lưu trữ kỳ lịch thành công.");
      void loadPeriods();
    } catch (err) {
      setError(getErrorMessage(err, "Lưu trữ thất bại."));
    } finally {
      setArchivingId(null);
    }
  };

  const confirmDeleteOrArchive = (id: number, action: "archive" | "delete") => {
    setDeleteTargetId(id);
    setDeleteAction(action);
    setConfirmOpen(true);
  };

  const handleDelete = async (id: number) => {
    setError(null);
    setMessage(null);
    try {
      await api.deletePeriod(id);
      setMessage("Xóa kỳ lịch thành công.");
      void loadPeriods();
    } catch (err) {
      setError(getErrorMessage(err, "Xóa thất bại."));
    }
  };

  const handleConfirm = async () => {
    if (deleteTargetId == null) return;
    if (deleteAction === "archive") {
      await handleArchive(deleteTargetId);
    } else if (deleteAction === "delete") {
      await handleDelete(deleteTargetId);
    }
    setConfirmOpen(false);
    setDeleteTargetId(null);
  };

  const statusBadge = (status: string) => {
    const s = STATUS_LABELS[status] ?? STATUS_LABELS.DRAFT;
    return (
      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[12px] font-semibold ${s.bg}`}>
        <span className={`w-1.5 h-1.5 rounded-full ${s.dot}`} />
        {s.label}
      </span>
    );
  };

  const filteredPeriods = periods.filter((p) => {
    const matchesStatus = statusFilter === "ALL" || p.status === statusFilter;
    const matchesSearch = !searchQuery.trim() || p.periodName.toLowerCase().includes(searchQuery.trim().toLowerCase());
    return matchesStatus && matchesSearch;
  });

  return (
    <>
      <div className="p-margin-desktop">
          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="font-display-lg text-display-lg text-on-surface">Kỳ lịch công tác</h1>
              <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                Tạo và quản lý các kỳ lịch theo tháng. Mỗi kỳ lịch cần được công bố trước khi nhân sự có thể xem.
              </p>
            </div>
            <Button onClick={openCreateModal}>
              <span className="material-symbols-outlined text-[20px]">add</span>
              Tạo kỳ lịch
            </Button>
          </div>

          {/* Filter bar */}
          <div className="flex flex-wrap items-center gap-3 mb-4">
            <div className="relative flex-1 min-w-[200px]">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">search</span>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Tìm theo tên kỳ lịch…"
                aria-label="Tìm kiếm kỳ lịch"
                className="w-full pl-10 pr-4 py-2.5 bg-surface-container-lowest rounded-lg border border-outline-variant focus:border-primary focus:ring-1 focus:ring-primary/20 focus:outline-none font-body-sm text-body-sm text-on-surface transition-all"
              />
            </div>
            <div className="relative">
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
                aria-label="Lọc theo trạng thái"
                className="appearance-none pl-3 pr-9 py-2.5 bg-surface-container-lowest rounded-lg border border-outline-variant focus:border-primary focus:ring-1 focus:ring-primary/20 focus:outline-none font-body-sm text-body-sm text-on-surface cursor-pointer"
              >
                <option value="ALL">Tất cả trạng thái</option>
                <option value="DRAFT">Bản nháp</option>
                <option value="PUBLISHED">Đã công bố</option>
                <option value="ARCHIVED">Đã lưu trữ</option>
              </select>
              <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">expand_more</span>
            </div>
            {(statusFilter !== "ALL" || searchQuery) && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setStatusFilter("ALL");
                  setSearchQuery("");
                }}
              >
                Đặt lại
              </Button>
            )}
          </div>

          {/* Messages */}
          {error && (
            <div className="mb-4 p-4 bg-error-container border border-error/20 rounded-lg text-on-error-container text-body-sm font-body-sm flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px]">error</span>
              {error}
            </div>
          )}
          {message && (
            <div className="mb-4 p-4 bg-secondary-container border border-secondary/20 rounded-lg text-on-secondary-container text-body-sm font-body-sm flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px]">check_circle</span>
              {message}
            </div>
          )}

          {/* Table */}
          <div className="bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm overflow-hidden">
            {loading ? (
              <div className="p-4 space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full rounded" />
                ))}
              </div>
            ) : periods.length === 0 ? (
              <EmptyState
                icon="calendar_month"
                title="Chưa có kỳ lịch nào"
                description="Mỗi kỳ lịch cần được tạo và công bố trước khi nhân sự có thể xem lịch của mình."
                action={
                  <Button onClick={openCreateModal}>
                    <span className="material-symbols-outlined text-[20px]">add</span>
                    Tạo kỳ lịch đầu tiên
                  </Button>
                }
              />
            ) : filteredPeriods.length === 0 ? (
              <EmptyState
                icon="filter_list_off"
                title="Không có kỳ lịch nào khớp với bộ lọc"
                description="Thử đổi trạng thái hoặc từ khóa tìm kiếm khác."
                action={
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setStatusFilter("ALL");
                      setSearchQuery("");
                    }}
                  >
                    Đặt lại bộ lọc
                  </Button>
                }
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse" aria-label="Page Table">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Tên kỳ lịch</th>
                      <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Ngày bắt đầu</th>
                      <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Ngày kết thúc</th>
                      <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Trạng thái</th>
                      <th scope="col" className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {filteredPeriods.map((p) => (
                      <tr key={p.id} className="hover:bg-surface-container-lowest transition-colors h-12">
                        <td className="py-2 px-4 text-on-surface font-label-md">{p.periodName}</td>
                        <td className="py-2 px-4 text-on-surface font-label-md">{formatDate(p.startDate)}</td>
                        <td className="py-2 px-4 text-on-surface font-label-md">{formatDate(p.endDate)}</td>
                        <td className="py-2 px-4">{statusBadge(p.status)}</td>
                        <td className="py-2 px-4">
                          <div className="flex items-center gap-1">
                            {p.status === "DRAFT" && (
                              <>
                                <button
                                  className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-primary"
                                  title="Chỉnh sửa"
                                  onClick={() => openEditModal(p)}
                                >
                                  <span className="material-symbols-outlined text-[20px]">edit</span>
                                </button>
                                <button
                                  className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-secondary"
                                  title="Công bố"
                                  onClick={() => handlePublish(p.id)}
                                  disabled={publishingId === p.id}
                                >
                                  {publishingId === p.id ? (
                                    <span className="material-symbols-outlined text-[20px] animate-spin">progress_activity</span>
                                  ) : (
                                    <span className="material-symbols-outlined text-[20px]">publish</span>
                                  )}
                                </button>
                                <button
                                  className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-outline"
                                  title="Lưu trữ"
                                  onClick={() => confirmDeleteOrArchive(p.id, "archive")}
                                  disabled={archivingId === p.id}
                                >
                                  <span className="material-symbols-outlined text-[20px]">archive</span>
                                </button>
                                <button
                                  className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-error"
                                  title="Xóa"
                                  onClick={() => confirmDeleteOrArchive(p.id, "delete")}
                                >
                                  <span className="material-symbols-outlined text-[20px]">delete</span>
                                </button>
                              </>
                            )}
                            {p.status === "PUBLISHED" && (
                              <button
                                className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-outline"
                                title="Lưu trữ"
                                onClick={() => confirmDeleteOrArchive(p.id, "archive")}
                                disabled={archivingId === p.id}
                              >
                                <span className="material-symbols-outlined text-[20px]">archive</span>
                              </button>
                            )}
                            {p.status === "ARCHIVED" && (
                              <span className="text-outline text-label-sm px-2">Không có thao tác</span>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* Create/Edit Modal — lazy-loaded via next/dynamic */}
        <PeriodFormModal
          open={showModal}
          editing={Boolean(editingPeriod)}
          values={formValues}
          formError={formError}
          saving={saving}
          onChange={setFormValues}
          onSave={handleSave}
          onCancel={() => setShowModal(false)}
        />

        {/* Archive Confirm */}
        <ConfirmDialog
          open={confirmOpen}
          onClose={() => setConfirmOpen(false)}
          onConfirm={handleConfirm}
          title={deleteAction === "archive" ? "Xác nhận lưu trữ?" : "Xác nhận xóa?"}
          description={
            deleteAction === "archive"
              ? "Kỳ lịch sẽ được lưu trữ. Bạn vẫn có thể xem lịch nhưng không thể chỉnh sửa."
              : "Hành động này không thể hoàn tác."
          }
          confirmLabel={deleteAction === "archive" ? "Lưu trữ" : "Xóa"}
          variant={deleteAction === "archive" ? "primary" : "danger"}
        />
    </>
  );
}
