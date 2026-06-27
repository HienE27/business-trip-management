"use client";

import { useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictDetail, Staff } from "@/types/api";
import type { ConflictItem } from "@/types/schedule";

/** Unified conflict shape — accepts ConflictDetail (API) or ConflictItem (legacy calendar) */
type ConflictForResolution = ConflictDetail | ConflictItem;

type ConflictResolutionModalProps = {
  open: boolean;
  onClose: () => void;
  conflict: ConflictForResolution | null;
  onRefresh?: () => void;
};

export function ConflictResolutionModal({
  open,
  onClose,
  conflict,
  onRefresh,
}: ConflictResolutionModalProps) {
  const [resolution, setResolution] = useState<string>("reassign");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reassign state
  const [replacements, setReplacements] = useState<Staff[]>([]);
  const [selectedReplacementId, setSelectedReplacementId] = useState<number | null>(null);
  const [loadingReplacements, setLoadingReplacements] = useState(false);

  async function loadReplacements() {
    if (!conflict?.periodId || !conflict?.workDate || !conflict?.shiftTypeId) return;
    setLoadingReplacements(true);
    try {
      const data = await api.findReplacements(
        conflict.periodId,
        conflict.workDate,
        conflict.shiftTypeId,
        conflict.originalStaffId ?? 0,
        5,
      );
      setReplacements(data ?? []);
    } catch {
      setReplacements([]);
    } finally {
      setLoadingReplacements(false);
    }
  }

  const handleResolutionChange = (value: string) => {
    setResolution(value);
    if (value === "reassign") {
      setSelectedReplacementId(null);
      void loadReplacements();
    } else {
      setReplacements([]);
      setSelectedReplacementId(null);
    }
  };

  const handleSubmit = async () => {
    if (!conflict) return;
    setSubmitting(true);
    setError(null);
    try {
      const scheduleId = Number(conflict.id);
      if (resolution === "remove") {
        await api.deleteSchedule(scheduleId);
      } else if (resolution === "override") {
        await api.overrideScheduleConflict(scheduleId, reason);
      } else if (resolution === "reassign") {
        if (!selectedReplacementId) {
          setError("Vui lòng chọn nhân sự thay thế.");
          setSubmitting(false);
          return;
        }
        // Fetch the existing schedule to preserve its other fields
        const existing = await api.get<import("@/types/api").Schedule>(`/schedules/${scheduleId}`);
        await api.updateSchedule(scheduleId, {
          periodId: existing.periodId,
          workDate: existing.workDate,
          shiftTypeId: existing.shiftType.id,
          staffId: selectedReplacementId,
        });
      }
      setDone(true);
      onRefresh?.();
      setTimeout(() => {
        setDone(false);
        setReason("");
        setResolution("reassign");
        setReplacements([]);
        setSelectedReplacementId(null);
        onClose();
      }, 1500);
    } catch (err) {
      setError(getErrorMessage(err, "Không thể giải quyết xung đột."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
          title="Giải quyết xung đột"
          description={conflict ? `${conflict.staffName} — ${"date" in conflict ? conflict.date : conflict.workDate}` : ""}
      size="md"
    >
      {done ? (
        <div className="flex flex-col items-center gap-4 py-6">
          <span aria-hidden="true" className="material-symbols-outlined text-[48px] text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>
            check_circle
          </span>
          <p className="text-title-lg text-on-surface font-semibold">
            Đã giải quyết xung đột
          </p>
        </div>
      ) : (
        <>
          {/* Conflict Detail */}
          {conflict && (
            <div className="bg-error-container border border-error/20 rounded-lg p-4 mb-6">
              <div className="flex items-start gap-3">
                <span className="material-symbols-outlined text-error mt-0.5">warning</span>
                <div>
                  <p className="text-label-md text-error font-semibold">Thông tin xung đột</p>
                  <p className="text-label-sm text-on-surface-variant mt-1 leading-relaxed">
                    {"detail" in conflict
                      ? conflict.detail
                      : (conflict as ConflictDetail).conflictReasons?.join("; ") ?? "Xung đột lịch trực"}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Reassign: replacement picker - outside the radio label for better UX */}
          {resolution === "reassign" && (
            <div className="mt-3 mb-4 p-3 bg-surface-container-low rounded-lg border border-primary/30">
              <label className="text-label-sm text-on-surface font-medium block mb-2" htmlFor="replacement-staff">
                Chọn nhân sự thay thế
              </label>
              {loadingReplacements ? (
                <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
                  <div className="size-3.5 animate-spin rounded-full border border-primary border-t-transparent" />
                  Đang tìm nhân sự thay thế...
                </div>
              ) : replacements.length > 0 ? (
                <select
                  id="replacement-staff"
                  className="w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-label-md text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
                  value={selectedReplacementId ?? ""}
                  onChange={(e) => setSelectedReplacementId(Number(e.target.value) || null)}
                >
                  <option value="">-- Chọn nhân sự thay thế --</option>
                  {replacements.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.fullName}{r.specialty?.name ? ` (${r.specialty.name})` : ""}
                    </option>
                  ))}
                </select>
              ) : (
                <p className="text-label-sm text-outline italic">Không có nhân sự khả dụng.</p>
              )}
            </div>
          )}

          {/* Resolution Options */}
          <div className="space-y-3">
            <p className="text-label-sm text-on-surface-variant font-semibold">
              Chọn cách giải quyết
            </p>
            {[
              { value: "reassign", icon: "person_swap", label: "Đổi nhân sự", desc: "Thay đổi nhân sự bị xung đột sang ca trực khác" },
              { value: "remove", icon: "delete", label: "Xóa ca trực", desc: "Xóa một trong hai ca trực xung đột" },
              { value: "override", icon: "verified", label: "Cho phép xung đột", desc: "Ghi chú lý do và cho phép xung đột (cần quản lý duyệt)" },
            ].map((opt) => (
              <label
                key={opt.value}
                className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-all ${
                  resolution === opt.value
                    ? "border-primary bg-primary/5"
                    : "border-outline-variant hover:bg-surface-container"
                }`}
              >
                <input
                  type="radio"
                  name="resolution"
                  value={opt.value}
                  checked={resolution === opt.value}
                  onChange={() => handleResolutionChange(opt.value)}
                  className="mt-0.5 accent-primary cursor-pointer"
                />
                <span className="material-symbols-outlined text-[20px] text-primary shrink-0 mt-0.5">
                  {opt.icon}
                </span>
                <div className="flex-1">
                  <p className="text-label-md text-on-surface font-medium">{opt.label}</p>
                  <p className="text-label-sm text-on-surface-variant mt-0.5 leading-relaxed">{opt.desc}</p>
                </div>
              </label>
            ))}
          </div>

          {/* Reason */}
          <div className="mt-4">
            <label className="text-label-sm text-on-surface-variant block mb-2" htmlFor="conflict-reason">
              Lý do / Ghi chú
            </label>
            <textarea
              id="conflict-reason"
              className="w-full h-20 resize-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
              placeholder="Nhập lý do giải quyết (nếu có)..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          {/* Error */}
          {error && (
            <div className="mt-4 rounded-lg border border-error/20 bg-error-container px-4 py-3 text-body-sm text-error">
              {error}
            </div>
          )}
        </>
      )}

      <ModalFooter>
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          Hủy
        </button>
        {!done && (
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md hover:bg-primary/90 transition-colors disabled:opacity-60 flex items-center gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {submitting ? (
              <>
                <div className="size-4 animate-spin rounded-full border-2 border-[var(--color-on-primary)] border-t-transparent" />
                Đang xử lý...
              </>
            ) : (
              "Xác nhận giải quyết"
            )}
          </button>
        )}
      </ModalFooter>
    </Modal>
  );
}
