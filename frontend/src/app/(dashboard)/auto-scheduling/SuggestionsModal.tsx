"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import { EmptyState } from "@/components/ui/EmptyState";
import { formatDate } from "@/lib/date";
import type { ReplacementSuggestion } from "@/types/api";

interface Props {
  open: boolean;
  onClose: () => void;
  suggestionsData: ReplacementSuggestion | null;
  loading: boolean;
}

export function SuggestionsModal({ open, onClose, suggestionsData, loading }: Props) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Đề xuất người thay thế"
      description={suggestionsData
        ? `Lịch ${suggestionsData.shiftTypeName} ngày ${formatDate(suggestionsData.workDate)} — ${suggestionsData.originalStaffName}`
        : undefined}
    >
      {loading ? (
        <div className="py-8 text-center text-label-sm text-on-surface-variant">Đang tải...</div>
      ) : suggestionsData ? (
        <div className="space-y-2 max-h-64 overflow-y-auto">
          {suggestionsData.suggestions.length === 0 ? (
            <EmptyState
              size="compact"
              icon="person_off"
              title="Không có người thay thế phù hợp"
              description="Hệ thống không tìm thấy nhân sự khả dụng với cùng chuyên môn và rảnh trong ngày."
            />
          ) : (
            suggestionsData.suggestions.map((s) => (
              <div
                key={s.staffId}
                className={`flex items-center gap-3 p-3 rounded-lg border transition-colors ${
                  s.isAvailable
                    ? "bg-surface-container-lowest border-outline-variant"
                    : "bg-surface-container-low border-outline opacity-60"
                }`}
              >
                <span className="material-symbols-outlined text-[18px] text-primary shrink-0">
                  {s.isAvailable ? "person" : "person_off"}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-label-md font-semibold text-on-surface truncate">{s.staffName}</p>
                  <p className="text-label-sm text-on-surface-variant">
                    {s.specialty ?? "—"} · <strong>{s.currentWorkload}</strong> ca trong kỳ
                    {s.conflicts.length > 0 && (
                      <span className="text-error"> · {s.conflicts.join(", ")}</span>
                    )}
                  </p>
                  {!s.isAvailable && s.reason && (
                    <p className="text-label-sm text-error mt-0.5">{s.reason}</p>
                  )}
                </div>
                <span className={`text-label-sm font-semibold shrink-0 ${
                  s.isAvailable ? "text-secondary" : "text-outline"
                }`}>
                  {s.isAvailable ? "Có thể thay" : "Không khả dụng"}
                </span>
              </div>
            ))
          )}
        </div>
      ) : (
        <p className="text-label-sm text-on-surface-variant text-center py-4">Không có dữ liệu.</p>
      )}
      <ModalFooter>
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          Đóng
        </button>
      </ModalFooter>
    </Modal>
  );
}
