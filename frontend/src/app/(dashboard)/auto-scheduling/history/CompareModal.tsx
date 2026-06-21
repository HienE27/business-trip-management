"use client";

import { Modal, ModalFooter } from "@/components/ui/Modal";
import type { AlgorithmMetrics } from "@/types/api";

interface CompareModalProps {
  runA: AlgorithmMetrics;
  runB: AlgorithmMetrics;
  periodNameA?: string;
  periodNameB?: string;
  onClose: () => void;
}

const ALGO_LABELS: Record<string, string> = {
  GREEDY: "Tham lam",
  ROUND_ROBIN: "Luân phiên",
  BACKTRACKING: "Backtracking",
};

function formatDateTime(dt?: string) {
  if (!dt) return "—";
  return new Date(dt).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function CompareModal({ runA, runB, periodNameA, periodNameB, onClose }: CompareModalProps) {
  const rows: { label: string; a: string | number; b: string | number; aGood?: boolean; bGood?: boolean }[] = [
    {
      label: "Thuật toán",
      a: ALGO_LABELS[runA.algorithmType ?? ""] ?? runA.algorithmType ?? "—",
      b: ALGO_LABELS[runB.algorithmType ?? ""] ?? runB.algorithmType ?? "—",
    },
    {
      label: "Độ phủ (Coverage)",
      a: `${((runA.coverageRate ?? 0) * 100).toFixed(1)}%`,
      b: `${((runB.coverageRate ?? 0) * 100).toFixed(1)}%`,
      aGood: (runA.coverageRate ?? 0) >= (runB.coverageRate ?? 0),
      bGood: (runB.coverageRate ?? 0) >= (runA.coverageRate ?? 0),
    },
    {
      label: "Điểm cân bằng (Balance)",
      a: typeof runA.balanceScore === "number" ? runA.balanceScore.toFixed(2) : String(runA.balanceScore ?? 0),
      b: typeof runB.balanceScore === "number" ? runB.balanceScore.toFixed(2) : String(runB.balanceScore ?? 0),
      aGood: (runA.balanceScore ?? 0) >= (runB.balanceScore ?? 0),
      bGood: (runB.balanceScore ?? 0) >= (runA.balanceScore ?? 0),
    },
    {
      label: "Xung đột",
      a: runA.conflictCount ?? 0,
      b: runB.conflictCount ?? 0,
      aGood: (runA.conflictCount ?? 0) <= (runB.conflictCount ?? 0),
      bGood: (runB.conflictCount ?? 0) <= (runA.conflictCount ?? 0),
    },
    {
      label: "Thời gian chạy",
      a: (runA.executionTimeMs ?? 0) < 1000 ? `${runA.executionTimeMs}ms` : `${((runA.executionTimeMs ?? 0) / 1000).toFixed(1)}s`,
      b: (runB.executionTimeMs ?? 0) < 1000 ? `${runB.executionTimeMs}ms` : `${((runB.executionTimeMs ?? 0) / 1000).toFixed(1)}s`,
      aGood: (runA.executionTimeMs ?? 0) <= (runB.executionTimeMs ?? 0),
      bGood: (runB.executionTimeMs ?? 0) <= (runA.executionTimeMs ?? 0),
    },
    {
      label: "Thời gian tạo",
      a: formatDateTime(runA.createdAt),
      b: formatDateTime(runB.createdAt),
    },
  ];

  return (
    <Modal open onClose={onClose} title="So sánh 2 lần chạy thuật toán" size="lg">
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <div className="flex-1">
            <div className="text-label-sm text-on-surface-variant mb-1">Lần chạy A</div>
            <div className="font-label-md font-semibold text-on-surface">{formatDateTime(runA.createdAt)}</div>
            <div className="text-label-xs text-on-surface-variant">{periodNameA ?? "—"}</div>
          </div>
          <span className="material-symbols-outlined text-outline text-[20px]">compare_arrows</span>
          <div className="flex-1">
            <div className="text-label-sm text-on-surface-variant mb-1">Lần chạy B</div>
            <div className="font-label-md font-semibold text-on-surface">{formatDateTime(runB.createdAt)}</div>
            <div className="text-label-xs text-on-surface-variant">{periodNameB ?? "—"}</div>
          </div>
        </div>

        <div className="border border-outline-variant rounded-lg overflow-hidden">
          {rows.map((row, i) => (
            <div
              key={row.label}
              className={`flex items-center gap-3 px-4 py-3 ${i !== rows.length - 1 ? "border-b border-outline-variant" : ""} ${i % 2 === 0 ? "bg-surface-container-low" : "bg-surface-container-lowest"}`}
            >
              <div className="w-36 shrink-0 font-label-sm text-label-sm text-on-surface-variant">{row.label}</div>
              <div className={`flex-1 text-center font-label-md font-semibold ${row.aGood !== undefined ? (row.aGood ? "text-secondary" : "text-error") : "text-on-surface"}`}>
                {row.a}
              </div>
              <div className="w-4 shrink-0 flex justify-center">
                {row.aGood !== undefined && row.bGood !== undefined && (
                  <span className="material-symbols-outlined text-[16px] text-outline">
                    {row.aGood && row.bGood ? "equals" : row.aGood ? "arrow_back" : "arrow_forward"}
                  </span>
                )}
              </div>
              <div className={`flex-1 text-center font-label-md font-semibold ${row.bGood !== undefined ? (row.bGood ? "text-secondary" : "text-error") : "text-on-surface"}`}>
                {row.b}
              </div>
            </div>
          ))}
        </div>

        <p className="text-label-xs text-outline text-center">
          Xanh = tốt hơn giữa 2 lần chạy &nbsp;|&nbsp; Đỏ = kém hơn &nbsp;|&nbsp; = = ngang nhau
        </p>
      </div>
      <ModalFooter>
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md text-label-md hover:opacity-90 transition-opacity"
        >
          Đóng
        </button>
      </ModalFooter>
    </Modal>
  );
}
