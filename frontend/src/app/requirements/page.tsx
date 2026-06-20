"use client";

import { useCallback, useEffect, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/errors";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import { formatDate } from "@/lib/date";
import { SHIFT_COLORS, type ShiftColorSet } from "@/lib/shift-colors";
import type { ShiftRequirement, SchedulePeriod, Specialty } from "@/types/api";

const SHIFT_TYPES = [
  { id: "L01", name: "Lịch trực 24/24" },
  { id: "L02", name: "Lịch thông tầm" },
  { id: "L03", name: "Phòng khám dịch vụ" },
  { id: "L04", name: "Phòng khám chuyên gia" },
];

export default function RequirementsPage() {
  const [requirements, setRequirements] = useState<ShiftRequirement[]>([]);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingReq, setEditingReq] = useState<ShiftRequirement | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const [filterPeriodId, setFilterPeriodId] = useState<number | "">("");
  const [filterShiftType, setFilterShiftType] = useState<string>("");

  // Form state
  const [formPeriodId, setFormPeriodId] = useState<number | "">("");
  const [formWorkDate, setFormWorkDate] = useState("");
  const [formShiftTypeId, setFormShiftTypeId] = useState("L01");
  const [formSpecialtyId, setFormSpecialtyId] = useState<number | "">("");
  const [formRequiredCount, setFormRequiredCount] = useState(1);
  const [formNote, setFormNote] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [reqData, periodData, specialtyData] = await Promise.all([
        api.get<ShiftRequirement[]>("/shift-requirements"),
        api.get<SchedulePeriod[]>("/schedule-periods"),
        api.get<Specialty[]>("/specialties/active"),
      ]);
      setRequirements(reqData ?? []);
      setPeriods(periodData ?? []);
      setSpecialties(specialtyData ?? []);
    } catch (err) {
      setError(getErrorMessage(err, "Không thể tải dữ liệu."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadData(); }, [loadData]);

  useAutoDismiss(error, () => setError(null));

  const openCreateModal = () => {
    setEditingReq(null);
    setFormPeriodId(filterPeriodId || "");
    setFormWorkDate("");
    setFormShiftTypeId("L01");
    setFormSpecialtyId("");
    setFormRequiredCount(1);
    setFormNote("");
    setFormError(null);
    setShowModal(true);
  };

  const openEditModal = (r: ShiftRequirement) => {
    setEditingReq(r);
    setFormPeriodId(r.periodId);
    setFormWorkDate(r.workDate);
    setFormShiftTypeId(r.shiftType.id);
    setFormSpecialtyId(r.specialty.id);
    setFormRequiredCount(r.requiredStaffCount);
    setFormNote(r.note ?? "");
    setFormError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!formPeriodId || !formWorkDate || !formShiftTypeId || !formSpecialtyId || formRequiredCount < 1) {
      setFormError("Vui lòng nhập đầy đủ thông tin bắt buộc.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const payload = {
        periodId: Number(formPeriodId),
        workDate: formWorkDate,
        shiftTypeId: formShiftTypeId,
        specialtyId: Number(formSpecialtyId),
        requiredStaffCount: formRequiredCount,
        note: formNote.trim() || undefined,
      };
      if (editingReq) {
        await api.updateRequirement(editingReq.id, payload);
        setMessage("Cập nhật yêu cầu nhân sự thành công.");
      } else {
        await api.createRequirement(payload);
        setMessage("Tạo yêu cầu nhân sự thành công.");
      }
      setShowModal(false);
      void loadData();
    } catch (err) {
      setFormError(getErrorMessage(err, "Lưu thất bại."));
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = (id: number) => {
    setDeleteTargetId(id);
    setConfirmOpen(true);
  };

  const handleDelete = async () => {
    if (deleteTargetId == null) return;
    try {
      await api.deleteRequirement(deleteTargetId);
      setMessage("Xóa yêu cầu nhân sự thành công.");
      setConfirmOpen(false);
      setDeleteTargetId(null);
      void loadData();
    } catch (err) {
      setError(getErrorMessage(err, "Xóa thất bại."));
    }
  };

  const filtered = requirements.filter((r) => {
    if (filterPeriodId && r.periodId !== Number(filterPeriodId)) return false;
    if (filterShiftType && r.shiftType.id !== filterShiftType) return false;
    return true;
  });

  const shiftColor = (typeId: string): ShiftColorSet => SHIFT_COLORS[typeId as keyof typeof SHIFT_COLORS] ?? { bg: "bg-surface-container", text: "text-on-surface", dot: "bg-surface", label: "text-on-surface" };

  return (
    <>
      <DashboardShell
        activeSection="requirements"
        title="Yêu cầu nhân sự"
        description="Cấu hình số nhân sự cần thiết cho từng ngày và loại ca trong kỳ lịch. Dùng cho M07 tự động xếp lịch."
      >
      <div className="p-margin-desktop">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="font-display-lg text-display-lg text-on-surface">Yêu cầu nhân sự</h1>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
              Cấu hình số nhân sự cần thiết cho từng ngày và loại ca trong kỳ lịch. Dùng cho M07 tự động xếp lịch.
            </p>
          </div>
          <Button onClick={openCreateModal}>
            <span className="material-symbols-outlined text-[20px]">add</span>
            Thêm yêu cầu
          </Button>
        </div>

        {/* Filters */}
        <div className="bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm p-4 mb-4 flex gap-4 flex-wrap">
          <div className="flex flex-col gap-1 min-w-48">
            <label className="font-label-sm text-label-sm text-on-surface-variant">Kỳ lịch</label>
            <select
              className="h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={filterPeriodId}
              onChange={(e) => setFilterPeriodId(e.target.value ? Number(e.target.value) : "")}
            >
              <option value="">Tất cả kỳ lịch</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>{p.periodName} ({p.status})</option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1 min-w-48">
            <label className="font-label-sm text-label-sm text-on-surface-variant">Loại ca</label>
            <select
              className="h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={filterShiftType}
              onChange={(e) => setFilterShiftType(e.target.value)}
            >
              <option value="">Tất cả loại ca</option>
              {SHIFT_TYPES.map((t) => (
                <option key={t.id} value={t.id}>{t.name}</option>
              ))}
            </select>
          </div>
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
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full rounded" />
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <EmptyState
              icon={requirements.length === 0 ? "inbox" : "search_off"}
              title={requirements.length === 0 ? "Chưa có yêu cầu nhân sự nào" : "Không có kết quả phù hợp"}
              description={
                requirements.length === 0
                  ? "Thêm yêu cầu nhân sự để M07 tự động xếp lịch có đủ dữ liệu phân công."
                  : "Thử đổi kỳ lịch hoặc loại ca khác để xem các yêu cầu khác."
              }
              action={
                requirements.length === 0 ? (
                  <Button onClick={openCreateModal}>
                    <span className="material-symbols-outlined text-[20px]">add</span>
                    Thêm yêu cầu
                  </Button>
                ) : (
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setFilterPeriodId("");
                      setFilterShiftType("");
                    }}
                  >
                    Đặt lại bộ lọc
                  </Button>
                )
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-surface-container-low border-b border-outline-variant">
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Kỳ lịch</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Ngày</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Loại ca</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Chuyên khoa</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">Cần</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">Đã gán</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">Ghi chú</th>
                    <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {filtered.map((r) => {
                    const color = shiftColor(r.shiftType.id);
                    return (
                      <tr key={r.id} className="hover:bg-surface-container-lowest transition-colors h-12">
                        <td className="py-2 px-4 text-on-surface font-label-md">
                          {periods.find((p) => p.id === r.periodId)?.periodName ?? r.periodId}
                        </td>
                        <td className="py-2 px-4 text-on-surface font-label-md">{formatDate(r.workDate)}</td>
                        <td className="py-2 px-4">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[12px] font-semibold border ${color.bg} ${color.text}`}>
                            {r.shiftType.name}
                          </span>
                        </td>
                        <td className="py-2 px-4 text-on-surface font-label-md">{r.specialty.name}</td>
                        <td className="py-2 px-4 text-center font-label-md text-on-surface font-bold">{r.requiredStaffCount}</td>
                        <td className="py-2 px-4 text-center font-label-md">
                          <span className={`inline-flex items-center justify-center w-8 h-8 rounded-full text-[12px] font-bold ${
                            r.assignedStaffCount >= r.requiredStaffCount
                              ? "bg-secondary-container text-on-secondary-container"
                              : "bg-error-container text-on-error-container"
                          }`}>
                            {r.assignedStaffCount}
                          </span>
                        </td>
                        <td className="py-2 px-4 text-on-surface-variant font-label-sm">{r.note ?? "—"}</td>
                        <td className="py-2 px-4 text-right">
                          <div className="flex items-center justify-end gap-1">
                            <button
                              className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-primary"
                              title="Chỉnh sửa"
                              onClick={() => openEditModal(r)}
                            >
                              <span className="material-symbols-outlined text-[20px]">edit</span>
                            </button>
                            <button
                              className="w-8 h-8 flex items-center justify-center rounded hover:bg-surface-container transition-colors text-error"
                              title="Xóa"
                              onClick={() => confirmDelete(r.id)}
                            >
                              <span className="material-symbols-outlined text-[20px]">delete</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
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
        title={editingReq ? "Chỉnh sửa yêu cầu nhân sự" : "Thêm yêu cầu nhân sự"}
        size="lg"
      >
        <div className="space-y-4 py-2">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                Kỳ lịch <span className="text-error">*</span>
              </label>
              <select
                className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
                value={formPeriodId}
                onChange={(e) => setFormPeriodId(e.target.value ? Number(e.target.value) : "")}
              >
                <option value="">Chọn kỳ lịch...</option>
                {periods.filter((p) => p.status === "DRAFT").map((p) => (
                  <option key={p.id} value={p.id}>{p.periodName}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                Ngày <span className="text-error">*</span>
              </label>
              <input
                type="date"
                className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                value={formWorkDate}
                onChange={(e) => setFormWorkDate(e.target.value)}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                Loại ca <span className="text-error">*</span>
              </label>
              <select
                className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
                value={formShiftTypeId}
                onChange={(e) => setFormShiftTypeId(e.target.value)}
              >
                {SHIFT_TYPES.map((t) => (
                  <option key={t.id} value={t.id}>{t.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block font-label-md text-label-md text-on-surface mb-1.5">
                Chuyên khoa <span className="text-error">*</span>
              </label>
              <select
                className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
                value={formSpecialtyId}
                onChange={(e) => setFormSpecialtyId(e.target.value ? Number(e.target.value) : "")}
              >
                <option value="">Chọn chuyên khoa...</option>
                {specialties.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">
              Số nhân sự yêu cầu <span className="text-error">*</span>
            </label>
            <input
              type="number"
              min={1}
              className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
              value={formRequiredCount}
              onChange={(e) => setFormRequiredCount(Math.max(1, Number(e.target.value)))}
            />
          </div>
          <div>
            <label className="block font-label-md text-label-md text-on-surface mb-1.5">Ghi chú</label>
            <textarea
              className="w-full p-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all resize-none rounded-lg"
              rows={2}
              placeholder="Ghi chú (tùy chọn)"
              value={formNote}
              onChange={(e) => setFormNote(e.target.value)}
            />
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
            {editingReq ? "Cập nhật" : "Tạo mới"}
          </Button>
        </ModalFooter>
      </Modal>

      {/* Delete Confirm */}
      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleDelete}
        title="Xác nhận xóa?"
        description="Hành động này không thể hoàn tác."
        confirmLabel="Xóa"
        variant="danger"
      />
    </DashboardShell>
    </>
  );
}
