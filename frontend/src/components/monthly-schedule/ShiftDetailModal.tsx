"use client";

import { memo } from "react";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
import { ShiftDetailInfo } from "@/components/shift-detail/ShiftDetailInfo";
import { ShiftDetailTable } from "@/components/shift-detail/ShiftDetailTable";
import { formatDate } from "@/lib/date";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { Schedule, Staff, ShiftType } from "@/types/api";
import { buildShiftDetailViewModel } from "./utils";

export type ShiftDetailModalProps = {
  scheduleId: number | null;
  schedule: Schedule | null;
  loading: boolean;
  canEdit?: boolean;
  onClose: () => void;
  onSave?: (updated: Schedule) => void;
  onDelete?: (deletedId: number) => void;
  onRefresh?: () => void;
};

export const ShiftDetailModal = memo(function ShiftDetailModal({
  scheduleId,
  schedule,
  loading,
  canEdit = true,
  onClose,
  onSave,
  onDelete,
  onRefresh,
}: ShiftDetailModalProps) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [staffList, setStaffList] = useState<Staff[]>([]);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [formStaffId, setFormStaffId] = useState<number | null>(null);
  const [formShiftTypeId, setFormShiftTypeId] = useState<string | null>(null);

  const loadMeta = useCallback(async () => {
    try {
      const [staffRes, shiftRes] = await Promise.allSettled([
        api.get<Staff[]>("/staff/active"),
        api.get<ShiftType[]>("/shift-types/active"),
      ]);
      if (staffRes.status === "fulfilled") setStaffList(staffRes.value ?? []);
      if (shiftRes.status === "fulfilled") setShiftTypes(shiftRes.value ?? []);
    } catch { /* silent */ }
  }, []);

  useEffect(() => {
    if (editing) void loadMeta();
  }, [editing, loadMeta]);

  useEffect(() => {
    if (schedule && editing) {
      setFormStaffId(schedule.staff.id);
      setFormShiftTypeId(schedule.shiftType.id);
    }
  }, [schedule, editing]);

  const handleSave = useCallback(async () => {
    if (!scheduleId || !formStaffId || !formShiftTypeId || !schedule) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateSchedule(scheduleId, {
        periodId: schedule.periodId,
        workDate: schedule.workDate,
        staffId: formStaffId,
        shiftTypeId: formShiftTypeId,
        requirementId: schedule.requirementId,
      });
      onSave?.(updated.data);
      setEditing(false);
    } catch (err) {
      setError(getErrorMessage(err, "Không thể lưu thay đổi."));
    } finally {
      setSaving(false);
    }
  }, [scheduleId, formStaffId, formShiftTypeId, schedule, onSave]);

  const handleDelete = useCallback(async () => {
    if (!scheduleId) return;
    setDeleting(true);
    setError(null);
    try {
      await api.deleteSchedule(scheduleId);
      onDelete?.(scheduleId);
      setShowDeleteConfirm(false);
      onClose();
      setTimeout(() => router.refresh(), 100);
    } catch (err) {
      setError(getErrorMessage(err, "Không thể xóa lịch trực."));
      setShowDeleteConfirm(false);
    } finally {
      setDeleting(false);
    }
  }, [scheduleId, onDelete, onClose, router]);

  const handleClose = useCallback(() => {
    setEditing(false);
    setError(null);
    onClose();
    const params = new URLSearchParams(window.location.search);
    if (params.has("scheduleId")) {
      params.delete("scheduleId");
      router.replace(`${window.location.pathname}?${params.toString()}`, { scroll: false });
    }
  }, [onClose, router]);

  const isDirty = schedule && (formStaffId !== schedule.staff.id || formShiftTypeId !== schedule.shiftType.id);
  const vm = schedule ? buildShiftDetailViewModel(schedule) : null;

  const renderContent = () => {
    if (loading) {
      return (
        <div className="flex h-48 items-center justify-center" aria-label="Đang tải chi tiết ca trực">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      );
    }

    if (!schedule) {
      return (
        <div className="flex h-32 items-center justify-center text-on-surface-variant">
          Không tìm thấy lịch trực.
        </div>
      );
    }

    return (
      <div className="space-y-6">
        {/* Header bar */}
        <div className="flex items-center gap-3 flex-wrap">
          <span className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-body-sm font-semibold text-[var(--color-on-primary)] ${vm?.shiftColor || ""}`}>
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">emergency</span>
            {vm?.shiftType}
          </span>
          {schedule.hasConflict && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-red-300 bg-red-100 text-red-800 px-3 py-1 text-xs font-semibold text-red-800">
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">warning</span>
              Có xung đột
            </span>
          )}
          
          {/* Action buttons */}
          {canEdit && !editing && !showDeleteConfirm && (
            <div className="ml-auto flex items-center gap-2">
              <Button
                variant="danger"
                size="sm"
                onClick={() => setShowDeleteConfirm(true)}
                icon={<span className="material-symbols-outlined text-[16px]">delete</span>}
              >
                Xóa
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setEditing(true)}
                icon={<span className="material-symbols-outlined text-[16px]">edit</span>}
              >
                Chỉnh sửa
              </Button>
            </div>
          )}

          {/* Inline delete confirmation */}
          {canEdit && showDeleteConfirm && (
            <div className="ml-auto flex items-center gap-2">
              <span className="text-label-sm text-red-800 font-medium">Xác nhận xóa?</span>
              <Button
                variant="danger"
                size="sm"
                onClick={handleDelete}
                disabled={deleting}
                loading={deleting}
                icon={!deleting ? <span className="material-symbols-outlined text-[16px]">delete</span> : undefined}
              >
                Xóa
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
              >
                Hủy
              </Button>
            </div>
          )}
        </div>

        {/* Error message */}
        {error && (
          <div className="rounded-lg border border-red-300 bg-red-100 text-red-800 px-4 py-3 text-sm text-red-800">
            {error}
          </div>
        )}

        {/* Edit form */}
        {editing && (
          <div className="rounded-lg border border-blue-300 bg-blue-100 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-[16px] font-semibold text-on-surface flex items-center gap-2">
                <span className="material-symbols-outlined text-blue-800 text-[20px]">edit</span>
                Chỉnh sửa ca trực
              </h3>
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="text-label-sm text-on-surface-variant hover:text-on-surface transition-colors"
              >
                Hủy
              </button>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-label-sm text-on-surface-variant font-medium">
                  Nhân sự
                </label>
                <div className="relative">
                  <select
                    className="w-full h-10 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface text-body-md text-on-surface appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 transition-all"
                    value={formStaffId ?? ""}
                    onChange={(e) => setFormStaffId(Number(e.target.value))}
                  >
                    <option value="">-- Chọn nhân sự --</option>
                    {staffList.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.fullName}
                        {s.specialty ? ` · ${s.specialty.name}` : ""}
                      </option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">
                    expand_more
                  </span>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-label-sm text-on-surface-variant font-medium">
                  Loại ca
                </label>
                <div className="relative">
                  <select
                    className="w-full h-10 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface text-body-md text-on-surface appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 transition-all"
                    value={formShiftTypeId ?? ""}
                    onChange={(e) => setFormShiftTypeId(e.target.value)}
                  >
                    <option value="">-- Chọn loại ca --</option>
                    {shiftTypes.map((st) => (
                      <option key={st.id} value={st.id}>
                        {st.name}
                      </option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[20px] pointer-events-none">
                    expand_more
                  </span>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                size="md"
                onClick={() => setEditing(false)}
                disabled={saving}
              >
                Hủy
              </Button>
              <Button
                variant="primary"
                size="md"
                onClick={handleSave}
                disabled={saving || !isDirty || !formStaffId || !formShiftTypeId}
                loading={saving}
                icon={!saving ? <span className="material-symbols-outlined text-[16px]">save</span> : undefined}
              >
                {saving ? "Đang lưu..." : "Lưu thay đổi"}
              </Button>
            </div>
          </div>
        )}

        {/* View mode */}
        {!editing && vm && (
          <>
            <ShiftDetailInfo shift={vm} />
            <ShiftDetailTable shift={vm} />
          </>
        )}
      </div>
    );
  };

  return (
    <Modal
      open={scheduleId !== null}
      onClose={handleClose}
      title={schedule ? `Chi tiết ca trực — ${schedule.shiftType.name}` : "Chi tiết ca trực"}
      description={schedule ? `${schedule.staff.fullName} · ${formatDate(schedule.workDate)}` : undefined}
      size="xl"
    >
      {renderContent()}
    </Modal>
  );
});
