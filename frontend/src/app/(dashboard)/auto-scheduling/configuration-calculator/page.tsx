"use client";

import { useState, useEffect } from "react";
import { BackButton } from "@/components/ui/BackButton";
import { api } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { getErrorMessage } from "@/lib/errors";
import type { SchedulePeriod } from "@/types/api";
import { Mode1Panel } from "./Mode1Panel";

export default function ConfigurationCalculatorPage() {
  const { success, error: toastError } = useToast();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);

  useEffect(() => {
    api.getAllPeriods().then((res) => {
      if (res?.data) setPeriods(Array.isArray(res.data) ? res.data : [res.data]);
    }).catch(() => {});
  }, []);

  const period = periods.find((p) => p.id === selectedPeriodId);

  return (
    <div className="space-y-5">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-1" />

      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
        <div className="px-5 py-3.5 border-b border-outline-variant bg-surface-container-low flex items-center gap-2.5">
          <span className="material-symbols-outlined text-primary text-[18px]">calculate</span>
          <h1 className="text-title-sm font-semibold text-on-surface">Configuration Calculator</h1>
          <span className="text-[11px] text-on-surface-variant ml-auto">Phân tích capacity dựa trên thuật toán thật</span>
        </div>

        {/* Selectors bar */}
        <div className="px-5 py-3 flex items-center gap-4 border-b border-outline-variant bg-surface-container-low/50 flex-wrap">
          <div className="flex items-center gap-2">
            <label className="text-[12px] font-medium text-on-surface-variant whitespace-nowrap">Kỳ lịch:</label>
            <select
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
              className="h-9 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest text-[13px] focus:outline-none focus:ring-2 focus:ring-primary/20"
            >
              <option value="">Chọn kỳ lịch...</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.periodName || `Kỳ #${p.id}`} ({p.startDate} – {p.endDate})
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Content */}
        <div className="p-5">
          {!selectedPeriodId ? (
            <div className="text-center py-12 text-on-surface-variant">
              <span className="material-symbols-outlined text-[48px] block mb-3">calendar_month</span>
              <p className="text-[14px]">Chọn kỳ lịch để bắt đầu phân tích</p>
            </div>
          ) : (
            <Mode1Panel periodId={selectedPeriodId} period={period} />
          )}
        </div>
      </div>
    </div>
  );
}
