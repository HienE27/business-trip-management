"use client";

import { Button } from "@/components/ui";
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
  const totalCandidates = suggestionsData?.totalCandidates ?? 0;
  const availableCount = suggestionsData?.availableCount ?? 0;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Đề xuất người thay thế"
      description={suggestionsData
        ? `${suggestionsData.shiftTypeName} · ${formatDate(suggestionsData.workDate)} · ${suggestionsData.originalStaffName}`
        : undefined}
    >
      {loading ? (
        <div className="py-12 text-center">
          <div className="inline-flex items-center gap-2 text-label-sm text-on-surface-variant">
            <div className="size-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            Đang tải đề xuất...
          </div>
        </div>
      ) : suggestionsData ? (
        <div className="space-y-3">
          {/* Summary */}
          <div className="flex items-center gap-3 p-3 rounded-xl border border-outline-variant bg-surface-container-low">
            <div className="flex items-center gap-2 flex-1">
              <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${availableCount > 0 ? "bg-emerald-100 text-emerald-800 border border-emerald-300" : "bg-surface-container-high text-outline"}`}>
                <span className="material-symbols-outlined text-[18px]">group</span>
              </div>
              <div>
                <p className="text-label-md font-semibold text-on-surface">{availableCount} / {totalCandidates} khả dụng</p>
                <p className="text-label-xs text-on-surface-variant">Có thể thay thế</p>
              </div>
            </div>
            <div className="flex gap-2">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-300 text-label-sm font-semibold border border-secondary/20">
                <span className="material-symbols-outlined text-[12px]">check_circle</span>
                {availableCount}
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container-high text-outline text-label-sm font-semibold border border-outline">
                <span className="material-symbols-outlined text-[12px]">block</span>
                {totalCandidates - availableCount}
              </span>
            </div>
          </div>

          {/* Suggestions list */}
          <div className="max-h-72 overflow-y-auto space-y-2">
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
                  className={`flex items-center gap-3 p-3.5 rounded-xl border transition-colors ${
                    s.isAvailable
                      ? "bg-surface-container-lowest border-outline-variant hover:border-secondary/30"
                      : "bg-surface-container-low border-outline opacity-70"
                  }`}
                >
                  {/* Avatar */}
                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full font-bold text-sm ${
                    s.isAvailable ? "bg-emerald-100 text-emerald-800 border border-emerald-300" : "bg-surface-container-high text-outline"
                  }`}>
                    {s.staffName.split(" ").slice(0, 2).map(w => w[0]).join("").toUpperCase()}
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="text-label-md font-semibold text-on-surface truncate">{s.staffName}</p>
                      {s.isAvailable && (
                        <span className="shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-300 text-[11px] font-bold border border-secondary/20">
                          <span className="material-symbols-outlined text-[10px]">check_circle</span>
                          Có thể thay
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-label-xs text-on-surface-variant">
                        {s.specialty ?? "—"}
                      </span>
                      <span className="text-label-xs text-outline">·</span>
                      <span className="text-label-xs text-on-surface-variant">
                        <strong className="font-semibold">{s.currentWorkload}</strong> ca trong kỳ
                      </span>
                      {s.conflicts.length > 0 && (
                        <>
                          <span className="text-label-xs text-outline">·</span>
                          <span className="text-label-xs text-red-800 font-semibold flex items-center gap-0.5">
                            <span className="material-symbols-outlined text-[12px]">warning</span>
                            {s.conflicts.length} xung đột
                          </span>
                        </>
                      )}
                    </div>
                    {!s.isAvailable && s.reason && (
                      <p className="text-label-xs text-red-800 mt-1 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[12px]">info</span>
                        {s.reason}
                      </p>
                    )}
                  </div>

                  {/* Status badge */}
                  <span className={`shrink-0 text-label-sm font-semibold ${
                    s.isAvailable ? "text-emerald-800" : "text-outline"
                  }`}>
                    {s.isAvailable ? "Khả dụng" : "Không khả dụng"}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      ) : (
        <div className="py-8 text-center">
          <span className="material-symbols-outlined text-outline text-[32px]">help</span>
          <p className="mt-2 text-label-md text-on-surface-variant">Không có dữ liệu đề xuất.</p>
        </div>
      )}
      <ModalFooter>
        <Button
          variant="secondary"
          size="md"
          onClick={onClose}
        >
          Đóng
        </Button>
      </ModalFooter>
    </Modal>
  );
}
