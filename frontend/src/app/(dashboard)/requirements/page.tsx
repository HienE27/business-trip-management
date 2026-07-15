"use client";

import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { usePermissions } from "@/hooks/usePermissions";
import { Permission } from "@/lib/permissions";
import { useToast } from "@/hooks/useToast";
import { Button, ConfirmDialog, IconButton } from "@/components/ui";
import { BackButton } from "@/components/ui/BackButton";

type ShiftRequirement = {
  id: number;
  periodId: number;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName?: string;
  specialtyId: number | null;
  specialtyName?: string;
  requiredStaffCount: number;
  note: string | null;
  createdAt: string;
  updatedAt: string;
};

type ShiftType = {
  id: string;
  name: string;
  isActive: boolean;
};

type Specialty = {
  id: number;
  name: string;
  isActive: boolean;
};

import type { SchedulePeriod } from "@/types/api";

type RequirementForm = {
  workDate: string;
  shiftTypeId: string;
  specialtyId: string;
  requiredStaffCount: number;
  note: string;
};

const DEFAULT_FORM: RequirementForm = {
  workDate: "",
  shiftTypeId: "L01",
  specialtyId: "",
  requiredStaffCount: 1,
  note: "",
};

function RequirementsContent() {
  const { can } = usePermissions();
  const canUpdate = can(Permission.PERIOD_UPDATE);
  const canDelete = can(Permission.PERIOD_DELETE);
  const toast = useToast();

  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [requirements, setRequirements] = useState<ShiftRequirement[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingRequirements, setLoadingRequirements] = useState(false);

  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);

  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [bulkDeleteIds, setBulkDeleteIds] = useState<number[]>([]);
  const [showBulkDelete, setShowBulkDelete] = useState(false);

  const [form, setForm] = useState<RequirementForm>(DEFAULT_FORM);

  // Load periods
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.getAllPeriods();
        if (cancelled) return;
        const list = (res.data ?? res ?? []) as SchedulePeriod[];
        setPeriods(list);
        // Auto-select the first ACTIVE period
        const active = list.find((p: SchedulePeriod) => p.status === "DRAFT");
        if (active) setSelectedPeriodId(active.id);
        else if (list.length > 0) setSelectedPeriodId(list[0].id);
      } catch (err) {
        toast.error(getErrorMessage(err, "Không thể tải danh sách kỳ lịch"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [toast]);

  // Load shift types + specialties (once)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [stRes, spRes] = await Promise.all([
          api.getActiveShiftTypes(),
          api.getActiveSpecialties(),
        ]);
        if (cancelled) return;
        setShiftTypes((stRes.data ?? stRes ?? []) as ShiftType[]);
        setSpecialties((spRes.data ?? spRes ?? []) as Specialty[]);
      } catch {
        // non-critical — form will have empty selects
      }
    })();
    return () => { cancelled = true; };
  }, []);

  // Load requirements for selected period
  const fetchRequirements = useCallback(async () => {
    if (!selectedPeriodId) {
      setRequirements([]);
      return;
    }
    setLoadingRequirements(true);
    try {
      const res = await api.get(`/shift-requirements/period/${selectedPeriodId}`);
      setRequirements(Array.isArray(res) ? (res as ShiftRequirement[]) : []);
    } catch (err) {
      toast.error(getErrorMessage(err, "Không thể tải yêu cầu nhân sự"));
    } finally {
      setLoadingRequirements(false);
    }
  }, [selectedPeriodId, toast]);

  useEffect(() => {
    fetchRequirements();
  }, [fetchRequirements]);

  // Helpers
  const shiftTypeName = (id: string) =>
    shiftTypes.find((st) => st.id === id)?.name ?? id;
  const specialtyName = (id: number | null) =>
    id ? specialties.find((s) => s.id === id)?.name ?? `#${id}` : "—";

  const formatDateDisplay = (dateStr: string) => {
    try {
      const d = new Date(dateStr + "T00:00:00");
      return d.toLocaleDateString("vi-VN", {
        weekday: "short",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      });
    } catch {
      return dateStr;
    }
  };

  const isL03OrL04 = (shiftTypeId: string) =>
    shiftTypeId === "L03" || shiftTypeId === "L04";

  // Modal helpers
  const openAdd = () => {
    setEditingId(null);
    setForm({ ...DEFAULT_FORM });
    setShowModal(true);
  };

  const openEdit = (r: ShiftRequirement) => {
    setEditingId(r.id);
    setForm({
      workDate: r.workDate,
      shiftTypeId: r.shiftTypeId,
      specialtyId: r.specialtyId ? String(r.specialtyId) : "",
      requiredStaffCount: r.requiredStaffCount,
      note: r.note ?? "",
    });
    setShowModal(true);
  };

  // Submit (create or update)
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPeriodId) return;
    if (!form.workDate || !form.shiftTypeId || form.requiredStaffCount < 1) {
      toast.error("Vui lòng nhập đầy đủ thông tin bắt buộc");
      return;
    }
    if (isL03OrL04(form.shiftTypeId) && !form.specialtyId) {
      toast.error("Loại ca L03/L04 yêu cầu chọn chuyên khoa");
      return;
    }
    setSubmitting(true);
    try {
      const payload: Record<string, unknown> = {
        workDate: form.workDate,
        shiftTypeId: form.shiftTypeId,
        specialtyId: form.specialtyId ? Number(form.specialtyId) : null,
        requiredStaffCount: form.requiredStaffCount,
        note: form.note.trim() || null,
      };
      if (editingId) {
        await api.put(`/shift-requirements/${editingId}`, payload);
        toast.success("Cập nhật yêu cầu thành công");
      } else {
        await api.post(`/shift-requirements/period/${selectedPeriodId}`, [payload]);
        toast.success("Thêm yêu cầu thành công");
      }
      setShowModal(false);
      await fetchRequirements();
    } catch (err) {
      toast.error(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setSubmitting(false);
    }
  };

  // Delete single
  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await api.delete(`/shift-requirements/${deleteId}`);
      toast.success("Xóa yêu cầu thành công");
      setDeleteId(null);
      await fetchRequirements();
    } catch (err) {
      toast.error(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setDeleting(false);
    }
  };

  // Bulk delete
  const handleBulkDelete = async () => {
    if (!selectedPeriodId || bulkDeleteIds.length === 0) return;
    setDeleting(true);
    try {
      // Delete each selected requirement individually
      await Promise.all(
        bulkDeleteIds.map((id) => api.delete(`/shift-requirements/${id}`))
      );
      toast.success(`Đã xóa ${bulkDeleteIds.length} yêu cầu`);
      setBulkDeleteIds([]);
      setShowBulkDelete(false);
      await fetchRequirements();
    } catch (err) {
      toast.error(getErrorMessage(err, "Có lỗi xảy ra khi xóa"));
    } finally {
      setDeleting(false);
    }
  };

  const toggleBulkSelect = (id: number) => {
    setBulkDeleteIds((prev) =>
      prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]
    );
  };

  const toggleSelectAll = () => {
    if (bulkDeleteIds.length === requirements.length) {
      setBulkDeleteIds([]);
    } else {
      setBulkDeleteIds(requirements.map((r) => r.id));
    }
  };

  // Loading skeleton
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
          <h1 className="text-headline-lg font-semibold text-on-surface">Yêu cầu nhân sự</h1>
          <p className="text-body-sm text-on-surface-variant mt-1">
            Cấu hình số nhân sự cần thiết cho từng ngày và loại ca
          </p>
        </div>
        {canUpdate && selectedPeriodId && (
          <Button
            variant="primary"
            size="md"
            icon={<span className="material-symbols-outlined text-[18px]">add</span>}
            onClick={openAdd}
          >
            Thêm yêu cầu
          </Button>
        )}
      </div>

      {/* Period selector */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="flex items-center gap-2">
          <label className="text-label-sm text-on-surface-variant">Kỳ lịch:</label>
          <select
            value={selectedPeriodId ?? ""}
            onChange={(e) => {
              const val = e.target.value ? Number(e.target.value) : null;
              setSelectedPeriodId(val);
              setBulkDeleteIds([]);
            }}
            className="h-9 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-md text-on-surface focus:border-primary focus:ring-1 focus:ring-primary/20 focus:outline-none cursor-pointer"
          >
            {periods.length === 0 && <option value="">Chưa có kỳ lịch</option>}
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.periodName} ({p.startDate} — {p.endDate})
              </option>
            ))}
          </select>
        </div>
        {bulkDeleteIds.length > 0 && canDelete && (
          <Button
            variant="danger"
            size="sm"
            icon={<span className="material-symbols-outlined text-[16px]">delete</span>}
            onClick={() => setShowBulkDelete(true)}
          >
            Xóa ({bulkDeleteIds.length})
          </Button>
        )}
        <p className="text-label-sm text-on-surface-variant ml-auto">
          {requirements.length} yêu cầu
        </p>
      </div>

      {/* Table */}
      {!selectedPeriodId ? (
        <EmptyState
          icon="event_note"
          title="Chọn kỳ lịch"
          description="Hãy chọn một kỳ lịch để xem và quản lý yêu cầu nhân sự."
        />
      ) : loadingRequirements ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : requirements.length === 0 ? (
        <EmptyState
          icon="assignment"
          title="Chưa có yêu cầu nhân sự"
          description="Thêm yêu cầu nhân sự để hệ thống biết cần bao nhiêu người cho mỗi ca trực."
          action={
            canUpdate ? (
              <Button
                variant="primary"
                size="md"
                icon={<span className="material-symbols-outlined text-[18px]">add</span>}
                onClick={openAdd}
              >
                Thêm yêu cầu
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
                  {canDelete && (
                    <th className="px-4 py-3 w-10">
                      <input
                        type="checkbox"
                        checked={bulkDeleteIds.length === requirements.length && requirements.length > 0}
                        onChange={toggleSelectAll}
                        className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
                        aria-label="Chọn tất cả"
                      />
                    </th>
                  )}
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Ngày</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Loại ca</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Chuyên khoa</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold text-center">Số nhân sự</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold">Ghi chú</th>
                  {(canUpdate || canDelete) && (
                    <th className="px-5 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide font-semibold text-right">Thao tác</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {requirements.map((r) => (
                  <tr key={r.id} className="hover:bg-surface-container-low transition-colors">
                    {canDelete && (
                      <td className="px-4 py-3">
                        <input
                          type="checkbox"
                          checked={bulkDeleteIds.includes(r.id)}
                          onChange={() => toggleBulkSelect(r.id)}
                          className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
                          aria-label={`Chọn yêu cầu ngày ${r.workDate}`}
                        />
                      </td>
                    )}
                    <td className="px-5 py-3">
                      <p className="text-label-md text-on-surface font-medium">{formatDateDisplay(r.workDate)}</p>
                    </td>
                    <td className="px-5 py-3">
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold bg-primary-container text-on-primary-container border border-primary/20">
                        {shiftTypeName(r.shiftTypeId)}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <span className="text-label-sm text-on-surface-variant">
                        {specialtyName(r.specialtyId)}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-center">
                      <span className="inline-flex items-center justify-center min-w-[28px] h-7 px-2 rounded-lg bg-secondary-container text-on-secondary-container text-label-md font-bold">
                        {r.requiredStaffCount}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <p className="text-label-sm text-on-surface-variant max-w-xs truncate" title={r.note ?? ""}>
                        {r.note || "—"}
                      </p>
                    </td>
                    {(canUpdate || canDelete) && (
                      <td className="px-5 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          {canUpdate && (
                            <IconButton
                              label="Chỉnh sửa"
                              variant="ghost"
                              size="sm"
                              onClick={() => openEdit(r)}
                              className="text-on-surface-variant hover:text-primary"
                            >
                              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit</span>
                            </IconButton>
                          )}
                          {canDelete && (
                            <IconButton
                              label="Xóa"
                              variant="ghost"
                              size="sm"
                              onClick={() => setDeleteId(r.id)}
                              className="text-on-surface-variant hover:text-error"
                            >
                              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">delete</span>
                            </IconButton>
                          )}
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
                {editingId ? "Chỉnh sửa yêu cầu" : "Thêm yêu cầu mới"}
              </h2>
              <IconButton
                label="Đóng"
                variant="ghost"
                size="sm"
                onClick={() => setShowModal(false)}
                className="text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">
                  Ngày làm việc <span className="text-error">*</span>
                </label>
                <input
                  type="date"
                  value={form.workDate}
                  onChange={(e) => setForm((f) => ({ ...f, workDate: e.target.value }))}
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">
                  Loại ca <span className="text-error">*</span>
                </label>
                <select
                  value={form.shiftTypeId}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      shiftTypeId: e.target.value,
                      // Clear specialty if switching away from L03/L04
                      specialtyId: isL03OrL04(e.target.value) ? f.specialtyId : "",
                    }))
                  }
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg cursor-pointer"
                  required
                >
                  {shiftTypes.map((st) => (
                    <option key={st.id} value={st.id}>{st.name}</option>
                  ))}
                </select>
              </div>
              {isL03OrL04(form.shiftTypeId) && (
                <div>
                  <label className="block text-label-sm text-on-surface-variant mb-1.5">
                    Chuyên khoa <span className="text-error">*</span>
                  </label>
                  <select
                    value={form.specialtyId}
                    onChange={(e) => setForm((f) => ({ ...f, specialtyId: e.target.value }))}
                    className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg cursor-pointer"
                    required={isL03OrL04(form.shiftTypeId)}
                  >
                    <option value="">Chọn chuyên khoa...</option>
                    {specialties.map((sp) => (
                      <option key={sp.id} value={sp.id}>{sp.name}</option>
                    ))}
                  </select>
                </div>
              )}
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">
                  Số nhân sự cần thiết <span className="text-error">*</span>
                </label>
                <input
                  type="number"
                  min={1}
                  max={99}
                  value={form.requiredStaffCount}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      requiredStaffCount: Math.max(1, parseInt(e.target.value, 10) || 1),
                    }))
                  }
                  className="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-label-sm text-on-surface-variant mb-1.5">Ghi chú</label>
                <textarea
                  value={form.note}
                  onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
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
        title="Xác nhận xóa yêu cầu?"
        description="Hành động này sẽ xóa yêu cầu nhân sự này khỏi hệ thống. Dữ liệu không thể khôi phục."
        confirmLabel="Xóa"
        variant="danger"
        loading={deleting}
      />

      {/* Bulk Delete Confirm */}
      <ConfirmDialog
        open={showBulkDelete}
        onClose={() => setShowBulkDelete(false)}
        onConfirm={handleBulkDelete}
        title={`Xác nhận xóa ${bulkDeleteIds.length} yêu cầu?`}
        description="Hành động này sẽ xóa vĩnh viễn các yêu cầu đã chọn. Dữ liệu không thể khôi phục."
        confirmLabel="Xóa tất cả"
        variant="danger"
        loading={deleting}
      />
    </div>
  );
}

export default function RequirementsPage() {
  return <RequirementsContent />;
}
