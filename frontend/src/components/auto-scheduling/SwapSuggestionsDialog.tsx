"use client";

import { memo, useMemo } from "react";
import { Button } from "@/components/ui/Button";
import type { QualityReport, Staff } from "@/types/api";

type SwapSuggestion = {
  shiftTypeId: string;
  shiftTypeName: string;
  staffA: { id: number; name: string; count: number };
  staffB: { id: number; name: string; count: number };
  gain: number; // ca improvement for staffB, reduction for staffA
};

type SwapSuggestionsDialogProps = {
  open: boolean;
  onClose: () => void;
  qualityReport: QualityReport;
  activeStaff: Array<{ id: number; fullName: string }>;
  onAccept: (swaps: SwapSuggestion[]) => void;
};

const TYPE_LABELS: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK Dịch vụ",
  L04: "PK Chuyên gia",
};

const TYPE_COLORS: Record<string, string> = {
  L01: "bg-red-50 border-red-200",
  L02: "bg-blue-50 border-blue-200",
  L03: "bg-green-50 border-green-200",
  L04: "bg-purple-50 border-purple-200",
};

export const SwapSuggestionsDialog = memo(function SwapSuggestionsDialog({
  open,
  onClose,
  qualityReport,
  activeStaff,
  onAccept,
}: SwapSuggestionsDialogProps) {
  const suggestions = useMemo<SwapSuggestion[]>(() => {
    const { shiftsByStaffAndType, fairnessByType } = qualityReport;
    const result: SwapSuggestion[] = [];

    for (const [typeKey, perStaff] of Object.entries(shiftsByStaffAndType ?? {})) {
      const typeId = typeKey.includes(":") ? typeKey.split(":")[0] : typeKey;
      if (typeId !== "L04") continue; // Only suggest swaps for L01/L02/L03

      const staffIds = Object.keys(perStaff);
      if (staffIds.length < 2) continue;

      // Find most overloaded and most underloaded
      const sorted = staffIds
        .map((sid) => ({ sid: Number(sid), count: perStaff[sid] as number }))
        .sort((a, b) => b.count - a.count);

      const most = sorted[0];
      const least = sorted[sorted.length - 1];
      const detail = fairnessByType.find((f) => f.shiftType === typeId);
      if (!detail) continue;

      const staffA = activeStaff.find((s) => s.id === most.sid);
      const staffB = activeStaff.find((s) => s.id === least.sid);
      if (!staffA || !staffB) continue;

      result.push({
        shiftTypeId: typeId,
        shiftTypeName: TYPE_LABELS[typeId] ?? typeId,
        staffA: { id: most.sid, name: staffA.fullName, count: most.count },
        staffB: { id: least.sid, name: staffB.fullName, count: least.count },
        gain: most.count - least.count,
      });
    }

    // Sort by gain (largest improvement first)
    return result.sort((a, b) => b.gain - a.gain);
  }, [qualityReport, activeStaff]);

  if (!open) return null;

  const handleAcceptAll = () => {
    onAccept(suggestions);
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="swap-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center"
    >
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Dialog */}
      <div className="relative z-10 w-full max-w-lg mx-4 bg-surface-container-lowest border border-outline-variant rounded-2xl shadow-2xl max-h-[80vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant shrink-0">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-fixed">
              <span className="material-symbols-outlined text-[22px] text-primary" aria-hidden="true">swap_horiz</span>
            </div>
            <div>
              <h2 id="swap-dialog-title" className="text-title-md font-bold text-on-surface">
                Gợi ý cân bằng ca trực
              </h2>
              <p className="text-label-xs text-on-surface-variant">
                {suggestions.length} thao tác swap có thể cải thiện CV
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-on-surface-variant hover:bg-surface-container transition-colors"
            aria-label="Đóng"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-3">
          {suggestions.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-8 text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-secondary-container">
                <span className="material-symbols-outlined text-[28px] text-secondary" aria-hidden="true"
                  style={{ fontVariationSettings: "'FILL' 1" }}>verified</span>
              </div>
              <p className="text-label-md font-semibold text-on-surface">Phân bổ đã tốt</p>
              <p className="text-label-sm text-on-surface-variant">
                Không có cặp swap nào giúp cải thiện thêm.
              </p>
            </div>
          ) : (
            suggestions.map((s, idx) => (
              <div
                key={`${s.shiftTypeId}-${s.staffA.id}-${s.staffB.id}-${idx}`}
                className={`flex items-center gap-3 rounded-xl border p-3 ${TYPE_COLORS[s.shiftTypeId] ?? "bg-surface-container border-outline-variant"}`}
              >
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-surface-container">
                  <span className="material-symbols-outlined text-[16px] text-on-surface-variant" aria-hidden="true">
                    {s.shiftTypeId === "L01" ? "emergency" : s.shiftTypeId === "L02" ? "schedule" : s.shiftTypeId === "L03" ? "medical_services" : "stethoscope"}
                  </span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-label-xs text-on-surface-variant">{s.shiftTypeName}</p>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-label-sm font-semibold text-on-surface truncate">{s.staffA.name}</span>
                    <span className="text-label-xs text-on-surface-variant">←</span>
                    <span className="text-label-xs text-on-surface-variant">
                      <span className="font-mono tabular-nums">{s.staffA.count}</span> ca
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-label-sm font-semibold text-on-surface truncate">{s.staffB.name}</span>
                    <span className="text-label-xs text-on-surface-variant">→</span>
                    <span className="text-label-xs text-on-surface-variant">
                      <span className="font-mono tabular-nums">{s.staffB.count}</span> ca
                    </span>
                  </div>
                </div>
                <div className="shrink-0 text-right">
                  <div className="flex items-center gap-1 rounded-full px-2.5 py-1 bg-secondary-container text-on-secondary-container text-[11px] font-bold">
                    <span className="material-symbols-outlined text-[12px]" aria-hidden="true">swap_horiz</span>
                    Swap
                  </div>
                  <p className="text-[10px] text-on-surface-variant mt-0.5">
                    giảm CV
                  </p>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-outline-variant shrink-0">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Đóng
          </Button>
          {suggestions.length > 0 && (
            <Button
              variant="primary"
              size="sm"
              onClick={handleAcceptAll}
              icon={<span className="material-symbols-outlined text-[16px]">check</span>}
            >
              Chấp nhận tất cả ({suggestions.length})
            </Button>
          )}
        </div>
      </div>
    </div>
  );
});
