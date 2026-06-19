"use client";

import { useCallback, useEffect, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Skeleton } from "@/components/ui/Skeleton";
import { Button } from "@/components/ui";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/errors";
import { formatDate } from "@/lib/date";
import type { SchedulePeriod } from "@/types/api";

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
  const [formName, setFormName] = useState("");
  const [formStartDate, setFormStartDate] = useState("");
  const [formEndDate, setFormEndDate] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

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

  useEffect(() => { void loadPeriods(); }, [loadPeriods]);

  const openCreateModal = () => {
    setEditingPeriod(null);
    setFormName("");
    setFormStartDate("");
    setFormEndDate("");
    setFormError(null);
    setShowModal(true);
  };

  const openEditModal = (p: SchedulePeriod) => {
    if (p.status !== "DRAFT") return;
    setEditingPeriod(p);
    setFormName(p.periodName);
    setFormStartDate(p.startDate);
    setFormEndDate(p.endDate);
    setFormError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!formName.trim() || !formStartDate || !formEndDate) {
      setFormError("Vui lòng nhập đầy đủ thông tin bắt buộc.");
      return;
    }
    if (formStartDate > formEndDate) {
      setFormError("Ngày bắt đầu phải trước ngày kết thúc.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const payload = {
        periodName: formName.trim(),
        startDate: formStartDate,
        endDate: formEndDate,
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

  const handleConfirm = async () => {
    if (deleteTargetId == null) return;
    if (deleteAction === "archive") {
      await handleArchive(deleteTargetId);
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

  return (
    <>
      <DashboardShell
        activeSection="monthly-schedule"
        title="Quản lý kỳ lịch"
        description="Tạo, chỉnh sửa, công bố và lưu trữ các kỳ lịch công tác."
      >
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
              <div className="p-8 text-center text-on-surface-variant">
                <span className="material-symbols-outlined text-[48px] text-outline">calendar_month</span>
                <p className="mt-2 text-body-sm">Chưa có kỳ lịch nào. Hãy tạo kỳ lịch đầu tiên.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Tên kỳ lịch</th>
                      <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Ngày bắt đầu</th>
                      <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Ngày kết thúc</th>
                      <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Trạng thái</th>
                      <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {periods.map((p) => (
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

        {/* Create/Edit Modal */}
        <Modal
          open={showModal}
          onClose={() => setShowModal(false)}
          title={editingPeriod ? "Chỉnh sửa kỳ lịch" : "Tạo kỳ lịch mới"}
          size="lg"
        >
          <div className="space-y-4 py-2">
            <div>
              <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                Tên kỳ lịch <span className="text-error">*</span>
              </label>
              <input
                type="text"
                className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                placeholder="VD: Lịch tháng 6/2026"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                maxLength={50}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                  Ngày bắt đầu <span className="text-error">*</span>
                </label>
                <input
                  type="date"
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                  value={formStartDate}
                  onChange={(e) => setFormStartDate(e.target.value)}
                />
              </div>
              <div>
                <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                  Ngày kết thúc <span className="text-error">*</span>
                </label>
                <input
                  type="date"
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                  value={formEndDate}
                  onChange={(e) => setFormEndDate(e.target.value)}
                />
              </div>
            </div>
            {formError && (
              <div className="p-3 bg-error-container border border-error/20 rounded-lg text-on-error-container text-body-sm">
                {formError}
              </div>
            )}
          </div>
          <ModalFooter>
            <Button variant="ghost" onClick={() => setShowModal(false)}>Hủy</Button>
            <Button onClick={handleSave} loading={saving}>
              {editingPeriod ? "Cập nhật" : "Tạo kỳ lịch"}
            </Button>
          </ModalFooter>
        </Modal>

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
      </DashboardShell>
    </>
  );
}
