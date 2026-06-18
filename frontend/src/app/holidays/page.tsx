"use client";

import { useCallback, useEffect, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Skeleton } from "@/components/ui/Skeleton";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { formatDate } from "@/lib/date";
import type { Holiday } from "@/types/api";

export default function HolidaysPage() {
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingHoliday, setEditingHoliday] = useState<Holiday | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

  // Form state
  const [name, setName] = useState("");
  const [holidayDate, setHolidayDate] = useState("");
  const [isNational, setIsNational] = useState(false);
  const [description, setDescription] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const loadHolidays = useCallback(async () => {
    try {
      setLoading(true);
      setMessage(null);
      const data = await api.get<Holiday[]>("/holidays/active");
      setHolidays(data ?? []);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể tải danh sách ngày lễ."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadHolidays(); }, [loadHolidays]);

  const openCreateModal = () => {
    setEditingHoliday(null);
    setName("");
    setHolidayDate("");
    setIsNational(false);
    setDescription("");
    setFormError(null);
    setShowModal(true);
  };

  const openEditModal = (h: Holiday) => {
    setEditingHoliday(h);
    setName(h.name);
    setHolidayDate(h.holidayDate);
    setIsNational(h.isNationalHoliday ?? false);
    setDescription(h.description ?? "");
    setFormError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!name.trim() || !holidayDate) {
      setFormError("Vui lòng nhập đầy đủ thông tin bắt buộc.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (editingHoliday) {
        await api.updateHoliday(editingHoliday.id, {
          name: name.trim(),
          holidayDate,
          isNationalHoliday: isNational,
          description: description.trim(),
        });
        setMessage("Cập nhật ngày lễ thành công.");
      } else {
        await api.createHoliday({
          name: name.trim(),
          holidayDate,
          isNationalHoliday: isNational,
          description: description.trim(),
        });
        setMessage("Thêm ngày lễ thành công.");
      }
      setShowModal(false);
      void loadHolidays();
    } catch (err) {
      setFormError(getErrorMessage(err, "Lỗi khi lưu ngày lễ."));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    setDeleteTargetId(id);
    setConfirmOpen(true);
  };

  const confirmDelete = async () => {
    if (deleteTargetId === null) return;
    try {
      await api.deleteHoliday(deleteTargetId);
      setMessage("Đã xóa ngày lễ thành công.");
      setHolidays((prev) => prev.filter((h) => h.id !== deleteTargetId));
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể xóa ngày lễ."));
    } finally {
      setConfirmOpen(false);
      setDeleteTargetId(null);
    }
  };

  const activeHolidays = holidays.filter((h) => h.isActive);
  const sorted = [...activeHolidays].sort((a, b) => a.holidayDate.localeCompare(b.holidayDate));

  return (
    <>
      <DashboardShell
        activeSection="holidays"
        title="Quản lý ngày lễ"
        description="Thêm, sửa, xóa ngày nghỉ lễ và ngày nghỉ bù để hệ thống tự động tính ngày nghỉ bù chính xác."
      >
      {message && (
        <div className={`rounded-lg border px-4 py-3 text-sm ${
          message.includes("thành công") || message.includes("Đã xóa")
            ? "border-secondary/20 bg-secondary-container text-on-secondary-container"
            : "border-error/20 bg-error-container text-error"
        }`}>
          {message}
        </div>
      )}

      {/* Toolbar */}
      <section className="flex items-center justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div>
          <p className="text-label-sm text-on-surface-variant">
            {activeHolidays.length} ngày lễ đang active
          </p>
        </div>
        <button
          type="button"
          onClick={openCreateModal}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-semibold text-on-primary hover:bg-primary/90 transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]">add</span>
          Thêm ngày lễ
        </button>
      </section>

      {/* Table */}
      <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm">
        <div className="overflow-x-auto">
          {loading ? (
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Ngày lễ</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Tên ngày lễ</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Loại</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Mô tả</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="hover:bg-surface-container-low/50">
                    <td className="px-5 py-3"><Skeleton className="h-3 w-20 rounded" /></td>
                    <td className="px-5 py-3"><Skeleton className="h-3 w-32 rounded" /></td>
                    <td className="px-5 py-3"><Skeleton className="h-5 w-16 rounded-full" /></td>
                    <td className="px-5 py-3"><Skeleton className="h-3 w-full rounded" /></td>
                    <td className="px-5 py-3"><Skeleton className="h-7 w-24 rounded-lg ml-auto" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : sorted.length === 0 ? (
            <div className="py-20 text-center">
              <span className="material-symbols-outlined text-5xl text-outline">event_busy</span>
              <p className="mt-4 text-on-surface-variant">Chưa có ngày lễ nào.</p>
            </div>
          ) : (
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Ngày lễ</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Tên ngày lễ</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Loại</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Mô tả</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {sorted.map((h) => (
                  <tr key={h.id} className="transition-colors hover:bg-surface-container-low/50 group">
                    <td className="px-5 py-3 text-label-md text-on-surface font-medium">
                      {formatDate(h.holidayDate)}
                    </td>
                    <td className="px-5 py-3">
                      <p className="text-label-md font-semibold text-on-surface">{h.name}</p>
                    </td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-label-sm font-semibold ${
                        h.isNationalHoliday
                          ? "bg-secondary-container text-on-secondary-container"
                          : "bg-tertiary-fixed text-on-tertiary"
                      }`}>
                        {h.isNationalHoliday ? "Quốc khánh" : "Ngày lễ"}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-label-sm text-on-surface-variant max-w-xs truncate">
                      {h.description ?? "—"}
                    </td>
                    <td className="px-5 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => openEditModal(h)}
                          className="inline-flex items-center gap-1 rounded-lg border border-outline-variant bg-surface px-3 py-1.5 text-label-sm text-on-surface hover:bg-surface-container-low transition-colors"
                        >
                          <span className="material-symbols-outlined text-[14px]">edit</span>
                          Sửa
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(h.id)}
                          className="inline-flex items-center gap-1 rounded-lg border border-error/30 bg-error-container px-3 py-1.5 text-label-sm text-error hover:bg-red-100 transition-colors"
                        >
                          <span className="material-symbols-outlined text-[14px]">delete</span>
                          Xóa
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {/* Create / Edit Modal */}
      <Modal
        open={showModal}
        onClose={() => setShowModal(false)}
        title={editingHoliday ? "Sửa ngày lễ" : "Thêm ngày lễ mới"}
        description={editingHoliday ? "Cập nhật thông tin ngày lễ." : "Điền thông tin ngày lễ cần thêm."}
      >
        <div className="space-y-4">
          {formError && (
            <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
              {formError}
            </div>
          )}
          <div>
            <label className="mb-1.5 block text-label-sm text-on-surface-variant">
              Tên ngày lễ <span className="text-error">*</span>
            </label>
            <input
              type="text"
              className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
              placeholder="VD: Quốc khánh 2/9"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-1.5 block text-label-sm text-on-surface-variant">
              Ngày lễ <span className="text-error">*</span>
            </label>
            <input
              type="date"
              className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
              value={holidayDate}
              onChange={(e) => setHolidayDate(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-1.5 block text-label-sm text-on-surface-variant">Loại</label>
            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  className="accent-primary size-4"
                  checked={isNational}
                  onChange={(e) => setIsNational(e.target.checked)}
                />
                <span className="text-label-md text-on-surface">Ngày Quốc khánh / Nghỉ lễ</span>
              </label>
            </div>
            <p className="mt-1 text-label-xs text-on-surface-variant">
              {isNational ? "Ngày nghỉ lễ toàn quốc." : "Ngày lễ kỷ niệm hoặc nghỉ bù."}
            </p>
          </div>
          <div>
            <label className="mb-1.5 block text-label-sm text-on-surface-variant">Mô tả</label>
            <textarea
              className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
              rows={2}
              placeholder="Ghi chú thêm..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
        </div>
        <ModalFooter>
          <button
            type="button"
            onClick={() => setShowModal(false)}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
          >
            <span className="material-symbols-outlined text-[16px]">check</span>
            {saving ? "Đang lưu..." : editingHoliday ? "Cập nhật" : "Thêm mới"}
          </button>
        </ModalFooter>
      </Modal>
    </DashboardShell>

    <ConfirmDialog
      open={confirmOpen}
      onClose={() => {
        setConfirmOpen(false);
        setDeleteTargetId(null);
      }}
      onConfirm={confirmDelete}
      title="Xóa ngày lễ?"
      description="Hành động này không thể hoàn tác."
      confirmLabel="Xóa"
      variant="danger"
    />
    </>
  );
}
