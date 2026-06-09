"use client";

import { useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";

export type ConflictItem = {
  id: string;
  staffName: string;
  date: string;
  detail: string;
  shiftType: string;
};

type ConflictResolutionModalProps = {
  open: boolean;
  onClose: () => void;
  conflict: ConflictItem | null;
};

export function ConflictResolutionModal({
  open,
  onClose,
  conflict,
}: ConflictResolutionModalProps) {
  const [resolution, setResolution] = useState<string>("reassign");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async () => {
    if (!conflict) return;
    setSubmitting(true);
    // Simulate API call
    await new Promise((r) => setTimeout(r, 800));
    setSubmitting(false);
    setDone(true);
    setTimeout(() => {
      setDone(false);
      setReason("");
      setResolution("reassign");
      onClose();
    }, 1500);
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Giải quyết xung đột"
      description={conflict ? `${conflict.staffName} — ${conflict.date}` : ""}
      size="md"
    >
      {done ? (
        <div className="flex flex-col items-center gap-4 py-6">
          <span className="material-symbols-outlined text-[48px] text-secondary fill-icon">
            check_circle
          </span>
          <p className="text-title-lg text-on-surface font-semibold">
            Da giải quyết xung đột
          </p>
        </div>
      ) : (
        <>
          {/* Conflict Detail */}
          {conflict && (
            <div className="bg-error-container/30 border border-error/20 rounded-lg p-4 mb-6">
              <div className="flex items-start gap-3">
                <span className="material-symbols-outlined text-error mt-0.5">warning</span>
                <div>
                  <p className="text-label-md text-error font-semibold">Thông tin xung đột</p>
                  <p className="text-label-sm text-on-surface-variant mt-1 leading-relaxed">
                    {conflict.detail}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Resolution Options */}
          <div className="space-y-3">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant font-semibold">
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
                    : "border-outline-variant hover:bg-surface-container-low"
                }`}
              >
                <input
                  type="radio"
                  name="resolution"
                  value={opt.value}
                  checked={resolution === opt.value}
                  onChange={() => setResolution(opt.value)}
                  className="mt-0.5 accent-primary cursor-pointer"
                />
                <span className="material-symbols-outlined text-[20px] text-primary shrink-0 mt-0.5">
                  {opt.icon}
                </span>
                <div>
                  <p className="text-label-md text-on-surface font-medium">{opt.label}</p>
                  <p className="text-label-sm text-on-surface-variant mt-0.5 leading-relaxed">{opt.desc}</p>
                </div>
              </label>
            ))}
          </div>

          {/* Reason */}
          <div className="mt-4">
            <label className="text-label-sm uppercase tracking-wider text-on-surface-variant block mb-2" htmlFor="conflict-reason">
              Lý do / Ghi chú
            </label>
            <textarea
              id="conflict-reason"
              className="w-full h-20 resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
              placeholder="Nhập lý do giải quyết (nếu có)..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
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
                <div className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
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
