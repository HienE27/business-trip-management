"use client";

/**
 * L04EvalTable — báo cáo đánh giá L04 theo chuyên khoa ngay trên màn hình
 * auto-scheduling, chứng minh "cross OFF" + leak = 0 sau khi chạy thuật toán.
 *
 * Nguồn dữ liệu: GET /api/v1/auto-schedule/l04-eval/{periodId}
 *
 * Hiển thị:
 *   - Bảng theo chuyên khoa: required / assigned / missing / fill-rate
 *   - Thanh KPI tổng: totalRequired, totalAssigned, crossLeak
 *   - Badge "cross OFF ✓" khi crossLeak == 0
 *
 * Lưu ý: endpoint đọc từ lịch đã lưu (schedules trong DB). Trước khi áp dụng
 * (apply) bản preview, bảng sẽ trống — hiển thị EmptyState với gợi ý.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { L04EvalReport, L04EvalSpecialtyRow } from "@/lib/api/autoScheduleApi";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";

export interface L04EvalTableProps {
  periodId: number | null;
  /**
   * Bật khi vừa chạy xong preview/apply để refetch ngay lập tức.
   * Component tự refetch khi periodId đổi hoặc giá trị này thay đổi.
   */
  refreshKey?: number;
}

export function L04EvalTable({ periodId, refreshKey = 0 }: L04EvalTableProps) {
  const [report, setReport] = useState<L04EvalReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ignoreRef = useRef(false);

  const load = useCallback(() => {
    if (!periodId) return;
    ignoreRef.current = false;
    setLoading(true);
    setError(null);
    api.getL04EvalReport(periodId)
      .then((data) => {
        if (!ignoreRef.current && data) setReport(data);
      })
      .catch(() => {
        if (!ignoreRef.current) {
          setReport(null);
          setError("Không thể tải báo cáo L04. Vui lòng thử lại.");
        }
      })
      .finally(() => {
        if (!ignoreRef.current) setLoading(false);
      });
  }, [periodId]);

  useEffect(() => {
    void load();
    return () => { ignoreRef.current = true; };
  }, [load, refreshKey]);

  if (!periodId) return null;

  if (loading && !report) {
    return (
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <Skeleton className="h-6 w-48 mb-3" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (error && !report) {
    return (
      <div
        role="alert"
        className="rounded-xl border border-error-container bg-error-container/10 p-5 flex items-center gap-3"
      >
        <span className="material-symbols-outlined text-error text-[22px]" aria-hidden="true">error</span>
        <p className="text-body-sm text-error">{error}</p>
      </div>
    );
  }

  // Chưa có lịch L04 đã lưu (preview chưa apply) → gợi ý
  if (report && report.totalL04Schedules === 0 && report.totalRequiredL04 === 0) {
    return (
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <Header periodName={report.periodName} />
        <div className="p-4">
          <EmptyState
            icon="science"
            title="Chưa có lịch L04 để đánh giá"
            description="Bấm 'Áp dụng' (apply) bản preview trước — bảng sẽ hiển thị required / assigned / cross-leak theo chuyên khoa ngay sau khi lịch được lưu."
            size="compact"
          />
        </div>
      </div>
    );
  }

  if (!report) return null;

  const crossOff = report.crossLeak === 0;
  const fillPct = Math.round(report.fillRate * 100);

  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden hover:shadow-md transition-shadow">
      <Header periodName={report.periodName} crossOff={crossOff} />

      {/* KPI strip */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-px bg-outline-variant/30 border-b border-outline-variant">
        <KpiCell label="Yêu cầu L04" value={report.totalRequiredL04} icon="inventory_2" />
        <KpiCell label="Đã gán L04" value={report.totalAssignedL04} icon="check_circle" />
        <KpiCell label="Fill rate" value={`${fillPct}%`} icon="trending_up" />
        <KpiCell
          label="Cross-leak"
          value={report.crossLeak}
          icon={crossOff ? "verified" : "warning"}
          tone={crossOff ? "ok" : "warn"}
        />
      </div>

      {/* Per-specialty table */}
      <div className="p-4 overflow-x-auto">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="text-left text-on-surface-variant border-b border-outline-variant">
              <Th>Chuyên khoa</Th>
              <Th align="right">NS tổng</Th>
              <Th align="right">Yêu cầu</Th>
              <Th align="right">Đã gán</Th>
              <Th align="right">Thiếu</Th>
              <Th align="right">Fill rate</Th>
            </tr>
          </thead>
          <tbody>
            {report.bySpecialty.length === 0 ? (
              <tr>
                <td colSpan={6} className="py-6 text-center text-on-surface-variant">
                  Không có dữ liệu chuyên khoa.
                </td>
              </tr>
            ) : (
              report.bySpecialty.map((row) => <Row key={row.specialtyId} row={row} />)
            )}
          </tbody>
        </table>
      </div>

      {/* Cross-off callout */}
      <div className="px-4 pb-4">
        <div
          className={`flex items-center gap-2 rounded-lg px-3 py-2 text-[12px] ${
            crossOff
              ? "bg-emerald-50 text-emerald-800 border border-emerald-200"
              : "bg-amber-50 text-amber-800 border border-amber-200"
          }`}
        >
          <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
            {crossOff ? "verified" : "warning"}
          </span>
          {crossOff ? (
            <span>
              <strong className="font-semibold">Cross-specialty OFF</strong> — 0 ca L04 bị gán chéo chuyên khoa.
              Phân bổ hoàn toàn theo chuyên khoa gốc.
            </span>
          ) : (
            <span>
              Phát hiện <strong className="font-semibold">{report.crossLeak}</strong> ca L04 bị gán cross-specialty.
              Kiểm tra lại <code className="font-mono">l04CrossSpecialty</code> = false hoặc bù nhân sự cho chuyên khoa thiếu.
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────── */

function Header({ periodName, crossOff }: { periodName?: string; crossOff?: boolean }) {
  return (
    <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-3">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-secondary-container">
        <span className="material-symbols-outlined text-[16px] text-on-secondary-container" aria-hidden="true">science</span>
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-title-sm font-semibold text-on-surface">
          Đánh giá L04 theo chuyên khoa
          {periodName ? <span className="text-on-surface-variant font-normal"> · {periodName}</span> : null}
        </p>
        <p className="text-label-xs text-on-surface-variant">
          Yêu cầu / đã gán / cross-leak — chứng minh cross OFF ngay trên màn
        </p>
      </div>
      {crossOff === true && (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
          <span className="material-symbols-outlined text-[12px]" aria-hidden="true">check</span>
          cross OFF
        </span>
      )}
      {crossOff === false && (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-amber-100 text-amber-800 border border-amber-300">
          <span className="material-symbols-outlined text-[12px]" aria-hidden="true">priority_high</span>
          có leak
        </span>
      )}
    </div>
  );
}

function KpiCell({
  label,
  value,
  icon,
  tone,
}: {
  label: string;
  value: number | string;
  icon: string;
  tone?: "ok" | "warn";
}) {
  const toneCls =
    tone === "ok"
      ? "text-emerald-700"
      : tone === "warn"
        ? "text-amber-700"
        : "text-on-surface";
  return (
    <div className="bg-surface-container-lowest px-4 py-3 flex items-center gap-2.5">
      <span className={`material-symbols-outlined text-[18px] ${toneCls}`} aria-hidden="true">
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-[10px] uppercase tracking-wide text-on-surface-variant truncate">{label}</p>
        <p className={`text-title-sm font-bold tabular-nums ${toneCls}`}>{value}</p>
      </div>
    </div>
  );
}

function Th({ children, align = "left" }: { children: React.ReactNode; align?: "left" | "right" }) {
  return (
    <th
      className={`px-2 py-1.5 text-[11px] uppercase tracking-wide font-semibold ${
        align === "right" ? "text-right" : "text-left"
      }`}
    >
      {children}
    </th>
  );
}

function Row({ row }: { row: L04EvalSpecialtyRow }) {
  const fillPct = Math.round(row.fillRate * 100);
  const isFull = row.missingL04 === 0 && row.requiredL04 > 0;
  return (
    <tr className="border-b border-outline-variant/40 last:border-0 hover:bg-surface-container-low/40">
      <td className="px-2 py-2 font-medium text-on-surface">{row.specialty}</td>
      <td className="px-2 py-2 text-right tabular-nums text-on-surface-variant">{row.staffCount}</td>
      <td className="px-2 py-2 text-right tabular-nums text-on-surface">{row.requiredL04}</td>
      <td className="px-2 py-2 text-right tabular-nums text-on-surface">{row.assignedL04}</td>
      <td className="px-2 py-2 text-right tabular-nums">
        {row.missingL04 > 0 ? (
          <span className="text-amber-700 font-semibold">{row.missingL04}</span>
        ) : (
          <span className="text-emerald-700">0</span>
        )}
      </td>
      <td className="px-2 py-2 text-right tabular-nums">
        <span
          className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-semibold ${
            isFull
              ? "bg-emerald-100 text-emerald-800"
              : row.fillRate > 0
                ? "bg-amber-100 text-amber-800"
                : "bg-surface-container-low text-on-surface-variant"
          }`}
        >
          {fillPct}%
        </span>
      </td>
    </tr>
  );
}
