"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
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
  const conflictCount = conflicts.length;
  
  return (
    <>
      <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm">
        {/* Header */}
        <div className="border-b border-outline-variant bg-error-container/20 px-4 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-error-container">
              <span className="material-symbols-outlined text-[22px] text-error" aria-hidden="true">warning</span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <h3 className="text-title-sm font-semibold text-on-surface">{title}</h3>
                {conflictCount > 0 && (
                  <Badge tone="error" size="sm">{conflictCount}</Badge>
                )}
              </div>
              <p className="mt-0.5 text-label-sm text-on-surface-variant">{description}</p>
            </div>
          </div>
        </div>

        {/* Content */}
        <div aria-live="polite" className="max-h-72 overflow-y-auto divide-y divide-outline-variant/50">
          {conflicts.length === 0 ? (
            <div className="py-8">
              <EmptyState
                icon="check_circle"
                title="Không có xung đột"
                description={emptyLabel}
                size="compact"
              />
            </div>
          ) : (
            conflicts.map((conflict) => (
              <button
                key={conflict.scheduleId}
                type="button"
                onClick={() => onSelect(conflict)}
                className="w-full px-4 py-4 text-left transition-colors hover:bg-surface-container-low cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-error-container">
                    <span className="material-symbols-outlined text-[18px] text-error" aria-hidden="true">warning</span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-label-md font-semibold text-on-surface">{conflict.staffName}</p>
                        <p className="mt-0.5 text-label-sm text-on-surface-variant">
                          {new Date(conflict.workDate).toLocaleDateString("vi-VN")} · {conflict.shiftTypeName}
                        </p>
                      </div>
                      <Badge tone="error" size="sm">
                        <span className="material-symbols-outlined text-[12px]">error</span>
                        Xung đột
                      </Badge>
                    </div>
                    <div className="mt-2 space-y-1">
                      {conflict.conflictReasons.map((reason) => (
                        <p key={reason} className="text-label-xs text-error flex items-center gap-1.5">
                          <span className="material-symbols-outlined text-[12px]" aria-hidden="true">error</span>
                          {reason}
                        </p>
                      ))}
                    </div>
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
            <div className="rounded-xl border border-error-container bg-error-container/20 p-4">
              <div className="flex items-center gap-2 mb-3">
                <span className="material-symbols-outlined text-error" aria-hidden="true">warning</span>
                <p className="text-label-md font-semibold text-on-surface">Lý do xung đột</p>
              </div>
              <ul className="space-y-2 text-label-sm text-on-surface">
                {selectedConflict.conflictReasons.map((reason) => (
                  <li key={reason} className="flex items-start gap-2">
                    <span className="material-symbols-outlined mt-0.5 text-[14px] text-error shrink-0" aria-hidden="true">error</span>
                    <span>{reason}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-xl bg-surface-container-low p-4 space-y-3">
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
                <span className="text-right text-label-sm font-medium text-on-surface">
                  {new Date(selectedConflict.workDate).toLocaleDateString("vi-VN")}
                </span>
              </div>
            </div>

            <ModalFooter>
              <Button variant="secondary" onClick={onClose}>Đóng</Button>
              {canResolve && (
                <Button
                  variant="primary"
                  onClick={() => { onResolve?.(selectedConflict); onClose(); }}
                  icon={<span className="material-symbols-outlined text-[16px]">check</span>}
                >
                  Giải quyết
                </Button>
              )}
            </ModalFooter>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
