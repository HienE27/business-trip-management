"use client";

import { memo, useCallback, useEffect, useState } from "react";
import { ExportControls } from "@/components/reports/ExportControls";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ScheduleExportFilters } from "@/lib/api-client";
import type { SchedulePeriod, ShiftStatistics } from "@/types/api";

export type ExportReportPanelProps = {
  selectedPeriod: SchedulePeriod | null;
  selectedPeriodId: number | null;
  onClose: () => void;
};

function StatCard({ label, value, icon, accent }: {
  label: string;
  value: string | number;
  icon: string;
  accent: string;
}) {
  return (
    <div className="flex flex-col justify-between rounded-lg border border-outline-variant bg-surface p-4 min-h-[80px]">
      <div className="flex justify-between items-start">
        <p className="text-label-sm text-on-surface-variant">{label}</p>
        <span className={`material-symbols-outlined p-1 rounded-md text-[16px] ${accent}`}>{icon}</span>
      </div>
      <p className="text-headline-lg font-bold text-on-surface mt-2">{value}</p>
    </div>
  );
}

export const ExportReportPanel = memo(function ExportReportPanel({
  selectedPeriod,
  selectedPeriodId,
  onClose,
}: ExportReportPanelProps) {
  const toast = useToast();
  const [stats, setStats] = useState<ShiftStatistics | null>(null);
  const [loadingStats, setLoadingStats] = useState(false);

  useEffect(() => {
    if (!selectedPeriodId) return;
    setLoadingStats(true);
    api.get<ShiftStatistics>("/dashboard/statistics/shifts", { periodId: selectedPeriodId })
      .then((data) => setStats(data ?? null))
      .catch(() => { /* silent */ })
      .finally(() => setLoadingStats(() => { setLoadingStats(false); return false; }));
  }, [selectedPeriodId]);

  const handleSuccess = useCallback((msg: string) => {
    toast.success(msg);
  }, [toast]);

  const handleError = useCallback((msg: string) => {
    toast.error(msg);
  }, [toast]);

  if (!selectedPeriod) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-6">
        <p className="text-body-sm text-on-surface-variant text-center">
          Chưa chọn kỳ lịch nào.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden animate-scale-in">
      {/* Header */}
      <div className="flex items-center gap-3 px-5 py-4 border-b border-outline-variant bg-surface">
        <div className="w-10 h-10 rounded-lg bg-primary-fixed flex items-center justify-center shrink-0">
          <span className="material-symbols-outlined text-primary text-[20px]">download</span>
        </div>
        <div className="flex-1 min-w-0">
          <h2 className="text-title-md font-semibold text-on-surface">Xuất báo cáo</h2>
          <p className="text-label-sm text-on-surface-variant truncate">
            {selectedPeriod.periodName} &mdash;{" "}
            {new Date(selectedPeriod.startDate).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })}
            {" – "}
            {new Date(selectedPeriod.endDate).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="shrink-0 p-2 rounded-lg hover:bg-surface-container-low text-on-surface-variant transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          aria-label="Đóng"
        >
          <span className="material-symbols-outlined text-[20px]">close</span>
        </button>
      </div>

      <div className="p-5 space-y-5">
        {/* Stats overview */}
        {loadingStats ? (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="h-20 rounded-lg bg-surface animate-pulse" />
            ))}
          </div>
        ) : stats ? (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard label="Trực 24/24" value={stats.L01Count ?? 0} icon="emergency" accent="bg-primary-fixed text-primary" />
            <StatCard label="Thông tầm" value={stats.L02Count ?? 0} icon="schedule" accent="bg-secondary-container text-secondary" />
            <StatCard label="Phòng khám DV" value={stats.L03Count ?? 0} icon="medical_services" accent="bg-tertiary-fixed text-tertiary" />
            <StatCard label="Phòng khám CG" value={stats.L04Count ?? 0} icon="stethoscope" accent="bg-expert/20 text-expert" />
          </div>
        ) : null}

        {/* Export controls */}
        {selectedPeriodId && (
          <ExportControls
            periodId={selectedPeriodId}
            variant="block"
            showWorkload
            onSuccess={handleSuccess}
            onError={handleError}
          />
        )}

        {/* Quick export buttons */}
        {selectedPeriodId && (
          <div className="space-y-2">
            <p className="text-label-sm font-semibold text-on-surface-variant">Xuất nhanh</p>
            <div className="flex flex-wrap gap-2">
              <QuickExportButton
                label="Lịch trực (Excel)"
                icon="grid_on"
                periodId={selectedPeriodId}
                format="excel-schedule"
                onSuccess={handleSuccess}
                onError={handleError}
              />
              <QuickExportButton
                label="Lịch trực (PDF)"
                icon="picture_as_pdf"
                periodId={selectedPeriodId}
                format="pdf-schedule"
                onSuccess={handleSuccess}
                onError={handleError}
              />
              <QuickExportButton
                label="Thống kê tải (Excel)"
                icon="bar_chart"
                periodId={selectedPeriodId}
                format="excel-workload"
                onSuccess={handleSuccess}
                onError={handleError}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

// ─── Quick Export Button ────────────────────────────────────────────────────
import { Button } from "@/components/ui";
import type { ExportFormat } from "@/components/reports/ExportControls";

function QuickExportButton({
  label,
  icon,
  periodId,
  format,
  filters,
  onSuccess,
  onError,
}: {
  label: string;
  icon: string;
  periodId: number;
  format: ExportFormat;
  filters?: ScheduleExportFilters;
  onSuccess: (msg: string) => void;
  onError: (msg: string) => void;
}) {
  const [loading, setLoading] = useState(false);

  const handleExport = async () => {
    setLoading(true);
    try {
      let blob: Blob;
      const safeFilters = filters ?? {};
      switch (format) {
        case "excel-schedule":
          blob = await api.exportScheduleExcel(periodId, safeFilters);
          downloadFile(blob, `lich-cong-tac-${periodId}.xlsx`);
          break;
        case "pdf-schedule":
          blob = await api.exportSchedulePdf(periodId, safeFilters);
          downloadFile(blob, `lich-cong-tac-${periodId}.pdf`);
          break;
        case "excel-workload":
          blob = await api.exportWorkloadExcel(periodId, safeFilters);
          downloadFile(blob, `thong-ke-tai-nhan-su-${periodId}.xlsx`);
          break;
      }
      onSuccess(`Đã xuất ${label}.`);
    } catch (err) {
      onError(getErrorMessage(err, `Xuất ${label} thất bại.`));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button
      variant="secondary"
      size="sm"
      onClick={handleExport}
      loading={loading}
      icon={<span className="material-symbols-outlined text-[16px]">{icon}</span>}
    >
      {label}
    </Button>
  );
}

function downloadFile(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
