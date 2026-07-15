"use client";

import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import { BackButton } from "@/components/ui/BackButton";
import { useToast } from "@/hooks/useToast";
import { getErrorMessage } from "@/lib/errors";
import type { CompensationDay, SchedulePeriod } from "@/types/api";

function fmtDateShort(d: string) {
  const VI_DAY_SHORT = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
  const date = new Date(d + "T12:00:00");
  return `${VI_DAY_SHORT[date.getDay()]}, ${date.toLocaleDateString("vi-VN")}`;
}

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export default function CompensationDaysPage() {
  const toast = useToast();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [periodId, setPeriodId] = useState<string>("");
  const [records, setRecords] = useState<CompensationDay[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");

  // Load periods on mount
  useEffect(() => {
    api.getAllPeriods().then((data) => {
      const periodArr = (Array.isArray(data) ? data : (data?.data as SchedulePeriod[])) ?? [];
      setPeriods(periodArr);
      if (periodArr[0]) setPeriodId(String(periodArr[0].id));
    }).catch(() => {});
  }, []);

  // Load records when period changes
  useEffect(() => {
    if (!periodId) {
      setRecords([]);
      return;
    }
    setLoading(true);
    api.getCompensationDaysByPeriod(Number(periodId))
      .then((data) => setRecords(Array.isArray(data) ? data : []))
      .catch((err) => {
        toast.error(getErrorMessage(err, "Không thể tải danh sách nghỉ bù."));
        setRecords([]);
      })
      .finally(() => setLoading(false));
  }, [periodId, toast]);

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    if (!kw) return records;
    return records.filter((r) =>
      r.staffName.toLowerCase().includes(kw)
      || (r.note ?? "").toLowerCase().includes(kw)
      || r.shiftDate.includes(kw)
      || r.compensationDate.includes(kw)
    );
  }, [records, search]);

  function exportCsv() {
    const rows = filtered.map((r) => [r.id, r.staffName, r.shiftDate, r.compensationDate, r.note ?? ""]);
    const csv = [
      "ID,Nhân sự,Ngày trực,Ngày nghỉ bù,Ghi chú",
      ...rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(",")),
    ].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `nghi-bu-${todayIso()}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  return (
    <div className="flex flex-col gap-3 pb-6">
      <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />

      {/* Filter bar */}
      <section className="flex items-center gap-2 flex-wrap rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2.5 shadow-sm">
        <label className="text-[12px] font-semibold text-on-surface-variant shrink-0">Kỳ lịch</label>
        <select
          aria-label="Kỳ lịch"
          value={periodId}
          onChange={(e) => setPeriodId(e.target.value)}
          className="rounded-lg border border-outline-variant bg-surface px-2.5 h-9 text-[12px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer pr-7 shrink-0 min-w-[200px]"
        >
          {periods.length === 0 && <option value="">— Chưa có kỳ —</option>}
          {periods.map((p) => (
            <option key={p.id} value={p.id}>
              {p.periodName} ({p.startDate} → {p.endDate})
            </option>
          ))}
        </select>

        <div className="w-px h-5 bg-outline-variant shrink-0" />

        <div className="relative shrink-0" style={{ minWidth: 180, width: 240 }}>
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[16px]">search</span>
          <input
            className="w-full rounded-lg border border-outline-variant bg-surface h-9 pl-9 pr-3 text-[13px] text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
            placeholder="Tên, ngày, ghi chú…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex-1 min-w-0" />

        <span className="text-[12px] text-on-surface-variant shrink-0 tabular-nums">
          {loading ? "…" : `${filtered.length} bản ghi`}
        </span>

        <Button
          variant="secondary"
          size="sm"
          onClick={exportCsv}
          disabled={loading || filtered.length === 0}
          icon={<span className="material-symbols-outlined text-[14px]">download</span>}
        >
          Xuất CSV
        </Button>
      </section>

      {/* Table */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
        <div
          className="hidden md:grid gap-3 px-4 py-2.5 bg-surface-container-low border-b border-outline-variant"
          style={{ gridTemplateColumns: "1fr 120px 120px 1fr" }}
        >
          <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide">Nhân sự</span>
          <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide">Ngày trực</span>
          <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide">Ngày nghỉ bù</span>
          <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide">Ghi chú</span>
        </div>

        {loading ? (
          <div className="divide-y divide-outline-variant">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-3">
                <Skeleton className="h-3 w-32 rounded" />
                <Skeleton className="h-3 w-20 rounded shrink-0" />
                <Skeleton className="h-3 w-20 rounded shrink-0" />
                <Skeleton className="h-3 flex-1 rounded" />
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState
            icon="event_available"
            title={search ? "Không có kết quả phù hợp" : "Chưa có ngày nghỉ bù nào"}
            description={search ? undefined : "Không có bản ghi nghỉ bù trong kỳ lịch này."}
          />
        ) : (
          <div>
            {filtered.map((r) => (
              <div
                key={r.id}
                className="grid gap-3 px-4 py-3 border-b border-outline-variant/10 last:border-b-0 hover:bg-surface-container-low transition-colors md:grid-cols-[1fr_120px_120px_1fr] items-center"
              >
                <div className="flex flex-col min-w-0">
                  <span className="text-[13px] font-semibold text-on-surface truncate">{r.staffName}</span>
                  <span className="text-[11px] text-on-surface-variant">
                    Staff #{r.staffId}
                    {r.scheduleId ? ` · Lịch #${r.scheduleId}` : ""}
                  </span>
                </div>
                <span className="text-[13px] text-on-surface tabular-nums">{fmtDateShort(r.shiftDate)}</span>
                <span className="text-[13px] font-semibold text-primary tabular-nums">
                  {fmtDateShort(r.compensationDate)}
                </span>
                <span
                  className="text-[12px] text-on-surface-variant truncate"
                  title={r.note ?? ""}
                >
                  {r.note || <span className="text-outline italic">—</span>}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
