"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";

interface WorkloadEntry {
  staffId: number;
  staffName: string;
  specialty: string | null;
  totalShifts: number;
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  workloadPercentage: number;
}

interface WorkloadData {
  totalSchedules: number;
  totalStaff: number;
  averageWorkload: number;
  minWorkload: number;
  maxWorkload: number;
  shiftTypeId?: string;
  staffWorkloadData: WorkloadEntry[];
}

interface WorkloadSummaryProps {
  periodId: number;
  shiftTypeId: string;
  /** Group bars by specialty instead of showing individual staff */
  groupBySpecialty?: boolean;
}

export function WorkloadSummary({ periodId, shiftTypeId, groupBySpecialty }: WorkloadSummaryProps) {
  const [data, setData] = useState<WorkloadData | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await api.getWorkloadChartData(periodId, shiftTypeId);
      setData(result);
    } catch {
      // silent fail — workload is supplementary info
    } finally {
      setLoading(false);
    }
  }, [periodId, shiftTypeId]);

  useEffect(() => {
    void load();
  }, [load]);

  const shiftTypeLabel: Record<string, string> = {
    L01: "Trực 24/24",
    L02: "Thông tầm",
    L03: "Phòng khám dịch vụ",
    L04: "Phòng khám chuyên gia",
  };

  type StaffRow = { staffId: number; name: string; specialty: string | null; total: number };
  type SpecialtyRow = { _type: "specialty"; name: string; total: number; avg: number; staffCount: number };

  const rows = useMemo((): StaffRow[] | SpecialtyRow[] => {
    if (!data) return [];
    const entries = data.staffWorkloadData ?? [];

    if (groupBySpecialty) {
      const grouped: Record<string, { name: string; total: number; count: number }> = {};
      for (const e of entries) {
        const key = e.specialty ?? "Không rõ khoa";
        if (!grouped[key]) grouped[key] = { name: key, total: 0, count: 0 };
        grouped[key].total += e.totalShifts;
        grouped[key].count += 1;
      }
      return Object.values(grouped)
        .sort((a, b) => b.total - a.total)
        .map((g) => ({ _type: "specialty" as const, name: g.name, total: g.total, avg: g.count > 0 ? Math.round(g.total / g.count) : 0, staffCount: g.count }));
    }

    return entries
      .sort((a, b) => b.totalShifts - a.totalShifts)
      .slice(0, 20)
      .map((e) => ({
        staffId: e.staffId,
        name: e.staffName,
        specialty: e.specialty,
        total: e.totalShifts,
      }));
  }, [data, groupBySpecialty]);

  const maxTotal = useMemo(() => Math.max(...rows.map((r) => r.total), 1), [rows]);
  const avg = data?.averageWorkload ?? 0;
  const stdDev = useMemo(() => {
    if (!data || data.staffWorkloadData.length < 2) return 0;
    const mean = avg;
    const variance = data.staffWorkloadData.reduce((sum, e) => sum + Math.pow(e.totalShifts - mean, 2), 0) / data.staffWorkloadData.length;
    return Math.sqrt(variance);
  }, [data, avg]);

  const IMBALANCE_THRESHOLD_PCT = 40;
  const flaggedEntries = useMemo(() => {
    if (!data) return [];
    return data.staffWorkloadData.filter((e) => {
      if (avg === 0) return false;
      const pctAbove = ((e.totalShifts - avg) / avg) * 100;
      return pctAbove >= IMBALANCE_THRESHOLD_PCT || (stdDev > 0 && e.totalShifts > avg + 1.5 * stdDev);
    });
  }, [data, avg, stdDev]);

  if (loading) {
    return (
      <div className="space-y-2">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-8 bg-surface-container-low rounded animate-pulse" />
        ))}
      </div>
    );
  }

  if (!data || rows.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-8 text-center">
        <span className="material-symbols-outlined text-4xl text-outline mb-2">bar_chart</span>
        <p className="text-sm text-on-surface-variant">Chưa có dữ liệu phân bổ cho loại lịch này.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* Summary KPI row */}
      <div className="flex flex-wrap gap-3">
        <div className="flex-1 min-w-[120px] rounded-lg border border-outline-variant bg-surface-container-low p-3">
          <p className="text-label-sm text-on-surface-variant mb-0.5">Tổng ca {shiftTypeLabel[shiftTypeId] ?? shiftTypeId}</p>
          <p className="text-headline-md font-bold text-on-surface">{data.totalSchedules}</p>
        </div>
        <div className="flex-1 min-w-[120px] rounded-lg border border-outline-variant bg-surface-container-low p-3">
          <p className="text-label-sm text-on-surface-variant mb-0.5">Số nhân sự</p>
          <p className="text-headline-md font-bold text-on-surface">{data.totalStaff}</p>
        </div>
        <div className="flex-1 min-w-[120px] rounded-lg border border-outline-variant bg-surface-container-low p-3">
          <p className="text-label-sm text-on-surface-variant mb-0.5">TB / nhân sự</p>
          <p className="text-headline-md font-bold text-on-surface">{avg}</p>
        </div>
      </div>

      {/* Imbalance alert */}
      {flaggedEntries.length > 0 && (
        <div className="flex items-start gap-3 rounded-lg border border-error/30 bg-error-container px-4 py-3 shadow-sm">
          <span className="material-symbols-outlined text-error mt-0.5 shrink-0" aria-hidden="true">warning</span>
          <div className="flex-1 min-w-0">
            <p className="text-label-md font-semibold text-error">Phát hiện phân bổ lệch lớn</p>
            <p className="text-label-sm text-on-error-container mt-0.5">
              {flaggedEntries.length} nhân sự có số ca vượt quá {IMBALANCE_THRESHOLD_PCT}% so với trung bình (
              {flaggedEntries.slice(0, 3).map((e) => e.staffName).join(", ")}
              {flaggedEntries.length > 3 ? ` và ${flaggedEntries.length - 3} người khác` : ""}
              ).
            </p>
          </div>
        </div>
      )}

      {/* Per-staff bars */}
      <div className="space-y-2">
        {rows.map((r, idx) => {
          const pct = Math.min(100, Math.round((r.total / maxTotal) * 100));
          if ("_type" in r && r._type === "specialty") {
            // Specialty group
            const specRow = r as SpecialtyRow;
            return (
              <div key={specRow.name} className="flex items-center gap-3">
                <div className="w-40 shrink-0 text-label-md text-on-surface truncate" title={specRow.name}>
                  {specRow.name}
                </div>
                <div className="flex-1 bg-surface-variant rounded-full h-5 overflow-hidden">
                  <div
                    className="h-full bg-primary/80 rounded-full flex items-center justify-end pr-2 transition-all duration-300"
                    style={{ width: `${pct}%` }}
                  >
                    <span className="text-[11px] font-bold text-on-primary">{specRow.total}</span>
                  </div>
                </div>
                <div className="w-16 text-right text-label-sm text-on-surface-variant shrink-0">
                  {specRow.staffCount} NV
                </div>
              </div>
            );
          }
          // Per-staff row
          const staffRow = r as StaffRow;
          const overAvg = staffRow.total > avg * 1.3;
          const barColor = overAvg ? "bg-error/80" : staffRow.total > avg ? "bg-tertiary/80" : "bg-primary/80";
          return (
            <div key={staffRow.staffId} className="flex items-center gap-3">
              <div className="w-44 shrink-0 text-label-md text-on-surface truncate" title={staffRow.name}>
                {staffRow.name}
              </div>
              <div className="flex-1 bg-surface-variant rounded-full h-5 overflow-hidden">
                <div
                  className={`h-full rounded-full flex items-center justify-end pr-2 transition-all duration-300 ${barColor}`}
                  style={{ width: `${pct}%` }}
                >
                  <span className="text-[11px] font-bold text-on-primary">{staffRow.total}</span>
                </div>
              </div>
              <div className="w-32 text-right text-label-sm text-on-surface-variant shrink-0 truncate" title={staffRow.specialty ?? ""}>
                {staffRow.specialty ?? "—"}
              </div>
            </div>
          );
        })}
      </div>

      <p className="text-[11px] text-on-surface-variant">
        TB = {avg} ca/nhân sự · Thanh đỏ = cao hơn 30% trên TB
      </p>
    </div>
  );
}
