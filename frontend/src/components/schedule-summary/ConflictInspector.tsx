"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { useRole, canManage } from "@/hooks/useRole";
import type { ConflictDetail } from "@/types/api";

type ConflictInspectorProps = {
  conflicts: ConflictDetail[];
  emptyLabel: string;
  title: string;
  description: string;
  selectedConflict: ConflictDetail | null;
  onSelect: (conflict: ConflictDetail) => void;
  onClose: () => void;
  onResolve?: (conflict: ConflictDetail) => void;
};

export function ConflictInspector({
  conflicts,
  emptyLabel,
  title,
  description,
  selectedConflict,
  onSelect,
  onClose,
  onResolve,
}: ConflictInspectorProps) {
  const role = useRole();
  const canResolve = canManage(role) && !!onResolve;
  return (
    <>
      <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)]">
        <div className="border-b border-outline-variant bg-error/5 px-4 py-4">
          <h3 className="flex items-center gap-2 text-title-lg font-semibold text-on-surface">
            <span className="material-symbols-outlined text-error">warning</span>
            {title}
          </h3>
          <p className="mt-1 text-label-sm text-on-surface-variant">{description}</p>
        </div>

        <div className="divide-y divide-outline-variant/50">
          {conflicts.length === 0 ? (
            <div className="flex items-center gap-2 px-5 py-6 text-label-md text-secondary">
              <span className="material-symbols-outlined text-[20px]">check_circle</span>
              {emptyLabel}
            </div>
          ) : (
            conflicts.map((conflict) => (
              <button
                key={conflict.scheduleId}
                type="button"
                onClick={() => onSelect(conflict)}
                className="w-full px-5 py-4 text-left transition-colors hover:bg-surface-container-low"
              >
                <div className="flex items-start gap-3">
                  <span className="material-symbols-outlined mt-0.5 text-[18px] text-error">warning</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-label-md font-semibold text-on-surface">{conflict.staffName}</p>
                    <p className="mt-0.5 text-[12px] text-on-surface-variant">
                      {new Date(conflict.workDate).toLocaleDateString("vi-VN")} · {conflict.shiftTypeName}
                    </p>
                    <p className="mt-1 line-clamp-2 text-[12px] text-error">{conflict.conflictReasons.join(" • ")}</p>
                  </div>
                </div>
              </button>
            ))
          )}
        </div>
      </section>

      <Modal
        open={!!selectedConflict}
        onClose={onClose}
        title="Chi tiết xung đột"
        description={
          selectedConflict
            ? `${selectedConflict.staffName} — ${new Date(selectedConflict.workDate).toLocaleDateString("vi-VN")}`
            : ""
        }
        size="sm"
      >
        {selectedConflict ? (
          <div className="space-y-4">
            <div className="rounded-lg border border-red-200 bg-red-50 p-4">
              <p className="text-label-md font-semibold text-error">Lý do xung đột</p>
              <ul className="mt-2 space-y-2 text-label-sm text-on-surface-variant">
                {selectedConflict.conflictReasons.map((reason) => (
                  <li key={reason} className="flex items-start gap-2">
                    <span className="material-symbols-outlined mt-0.5 text-[16px] text-error">error</span>
                    <span>{reason}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="space-y-2 rounded-lg bg-surface-container-low p-4">
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Nhân sự</span>
                <span className="text-right text-label-sm font-medium text-on-surface">{selectedConflict.staffName}</span>
              </div>
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Loại lịch</span>
                <span className="text-right text-label-sm font-medium text-on-surface">{selectedConflict.shiftTypeName}</span>
              </div>
              <div className="flex justify-between gap-4">
                <span className="text-label-sm text-on-surface-variant">Ngày</span>
                <span className="text-right text-label-sm font-medium text-on-surface">{new Date(selectedConflict.workDate).toLocaleDateString("vi-VN")}</span>
              </div>
            </div>

            <ModalFooter>
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-outline-variant px-4 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Đóng
              </button>
              {canResolve && (
                <button
                  type="button"
                  onClick={() => { onResolve?.(selectedConflict); onClose(); }}
                  className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  Giải quyết
                </button>
              )}
            </ModalFooter>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
