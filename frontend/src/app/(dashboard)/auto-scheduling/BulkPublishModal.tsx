"use client";

import React, { useEffect, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { EmptyState } from "@/components/ui/EmptyState";
import { formatDate } from "@/lib/date";
import type { SchedulePeriod } from "@/types/api";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";

interface BulkResult {
  id: number;
  periodName: string;
  success: boolean;
  message: string;
}

interface BulkResults {
  success: number;
  failure: number;
  results: BulkResult[];
}

interface Props {
  open: boolean;
  periods: SchedulePeriod[];
  onClose: () => void;
  onRefresh: () => void;
}

export function BulkPublishModal({ open, periods, onClose, onRefresh }: Props) {
  const [operation, setOperation] = useState<"publish" | "archive">("publish");
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [results, setResults] = useState<BulkResults | null>(null);

  useEffect(() => {
    if (!open) {
      setResults(null);
      setSelectedIds(new Set());
    }
  }, [open]);

  const filtered = periods.filter((p) =>
    operation === "publish" ? p.status === "DRAFT" : p.status === "PUBLISHED"
  );

  const handleSubmit = async () => {
    const ids = [...selectedIds];
    setSubmitting(true);
    try {
      const res = operation === "publish"
        ? await api.bulkPublishPeriods(ids)
        : await api.bulkArchivePeriods(ids);
      if (res.success && res.data) {
        setResults({
          success: res.data.successCount,
          failure: res.data.failureCount,
          results: res.data.results.map((r) => ({
            id: r.id,
            periodName: r.periodName ?? `Kỳ #${r.id}`,
            success: r.success,
            message: r.message,
          })),
        });
        if (res.data.successCount > 0) onRefresh();
      }
    } catch (err) {
      setResults({
        success: 0,
        failure: ids.length,
        results: ids.map((id) => ({ id, periodName: `Kỳ #${id}`, success: false, message: getErrorMessage(err, "Lỗi không xác định") })),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    setResults(null);
    setSelectedIds(new Set());
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={operation === "publish" ? "Công bố hàng loạt kỳ lịch" : "Lưu trữ hàng loạt kỳ lịch"}
      size="lg"
    >
      {!results ? (
        <>
          <div className="space-y-4">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => { setOperation("publish"); setSelectedIds(new Set()); }}
                className={`flex-1 py-2 rounded-lg text-label-md font-medium transition-colors border ${
                  operation === "publish"
                    ? "border-primary bg-primary-fixed/20 text-primary"
                    : "border-outline-variant text-on-surface-variant hover:border-primary/40"
                }`}
              >
                <span className="material-symbols-outlined text-[16px] align-middle mr-1">publish</span>
                Công bố hàng loạt
              </button>
              <button
                type="button"
                onClick={() => { setOperation("archive"); setSelectedIds(new Set()); }}
                className={`flex-1 py-2 rounded-lg text-label-md font-medium transition-colors border ${
                  operation === "archive"
                    ? "border-secondary bg-secondary-container/20 text-on-secondary-container"
                    : "border-outline-variant text-on-surface-variant hover:border-secondary/40"
                }`}
              >
                <span className="material-symbols-outlined text-[16px] align-middle mr-1">archive</span>
                Lưu trữ hàng loạt
              </button>
            </div>

            <div>
              <p className="text-label-sm text-on-surface-variant mb-2">
                {operation === "publish"
                  ? "Chọn các kỳ lịch ở trạng thái Nháp để công bố:"
                  : "Chọn các kỳ lịch ở trạng thái Đã công bố để lưu trữ:"}
              </p>
              <div className="border border-outline-variant rounded-lg overflow-hidden max-h-64 overflow-y-auto">
                {filtered.length === 0 ? (
                  <EmptyState
                    size="compact"
                    icon={operation === "publish" ? "publish" : "archive"}
                    title="Không có kỳ lịch phù hợp"
                    description={
                      operation === "publish"
                        ? "Tất cả kỳ lịch đã được công bố hoặc lưu trữ."
                        : "Chưa có kỳ lịch nào ở trạng thái đã công bố."
                    }
                  />
                ) : (
                  filtered.map((p) => (
                    <label
                      key={p.id}
                      className="flex items-center gap-3 px-4 py-3 hover:bg-surface-container-low cursor-pointer border-b border-outline-variant last:border-b-0"
                    >
                      <input
                        type="checkbox"
                        checked={selectedIds.has(p.id)}
                        onChange={(e) => {
                          setSelectedIds((prev) => {
                            const next = new Set(prev);
                            if (e.target.checked) next.add(p.id);
                            else next.delete(p.id);
                            return next;
                          });
                        }}
                        className="w-4 h-4 accent-primary"
                      />
                      <div className="flex-1">
                        <p className="text-label-md text-on-surface font-medium">{p.periodName}</p>
                        <p className="text-[11px] text-on-surface-variant">
                          {formatDate(p.startDate)} – {formatDate(p.endDate)}
                        </p>
                      </div>
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                        p.status === "DRAFT" ? "bg-primary-fixed text-primary" : "bg-secondary-container text-on-secondary-container"
                      }`}>
                        {p.status === "DRAFT" ? "Nháp" : "Đã công bố"}
                      </span>
                    </label>
                  ))
                )}
              </div>
              {selectedIds.size > 0 && (
                <p className="text-label-sm text-on-surface-variant mt-2">
                  Đã chọn <strong>{selectedIds.size}</strong> kỳ lịch.
                </p>
              )}
            </div>
          </div>
          <ModalFooter>
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
            >
              Đóng
            </button>
            <button
              type="button"
              disabled={selectedIds.size === 0 || submitting}
              onClick={handleSubmit}
              className={`inline-flex items-center gap-2 px-5 py-2 rounded-lg text-label-md font-semibold transition-colors disabled:opacity-50 ${
                operation === "publish"
                  ? "bg-primary text-on-primary hover:bg-primary/90"
                  : "bg-secondary text-on-secondary hover:bg-secondary/90"
              }`}
            >
              {submitting ? (
                <><div className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" /><span>Đang xử lý...</span></>
              ) : (
                <><span className="material-symbols-outlined text-[16px]">check</span>
                  {operation === "publish" ? `Công bố ${selectedIds.size} kỳ lịch` : `Lưu trữ ${selectedIds.size} kỳ lịch`}
                </>
              )}
            </button>
          </ModalFooter>
        </>
      ) : (
        <>
          <div className="space-y-3">
            <div className="flex gap-4">
              <div className="flex-1 rounded-lg border border-secondary-container bg-secondary-container/10 p-3 text-center">
                <p className="text-display-lg text-secondary font-bold">{results.success}</p>
                <p className="text-label-sm text-on-secondary-container">Thành công</p>
              </div>
              <div className="flex-1 rounded-lg border border-error-container bg-error-container/10 p-3 text-center">
                <p className="text-display-lg text-error font-bold">{results.failure}</p>
                <p className="text-label-sm text-on-error-container">Thất bại</p>
              </div>
            </div>
            <div className="border border-outline-variant rounded-lg overflow-hidden max-h-48 overflow-y-auto">
              {results.results.map((r) => (
                <div key={r.id} className="flex items-center gap-3 px-4 py-2.5 border-b border-outline-variant last:border-b-0">
                  <span className={`material-symbols-outlined text-[18px] ${r.success ? "text-secondary" : "text-error"}`}>
                    {r.success ? "check_circle" : "error"}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-label-md text-on-surface truncate">{r.periodName}</p>
                    {!r.success && (
                      <p className="text-[11px] text-error" title={r.message}>{r.message}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
          <ModalFooter>
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md font-semibold hover:bg-primary/90 transition-colors"
            >
              Đóng
            </button>
          </ModalFooter>
        </>
      )}
    </Modal>
  );
}
