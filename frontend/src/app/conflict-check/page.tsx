"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictCheckResponse, ConflictDetail } from "@/types/api";

export default function ConflictCheckPage() {
  const [periodId, setPeriodId] = useState<number>(1);
  const [periods, setPeriods] = useState<{ id: number; periodName: string }[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState("");

  const fetchPeriods = useCallback(async () => {
    try {
      const res = await api.get<{ id: number; periodName: string }[]>("/periods");
      setPeriods(res ?? []);
      if (res && res.length > 0) {
        const published = res.find((p: { id: number; periodName: string }) => p.id === periodId);
        if (!published) setPeriodId(res[0].id);
      }
    } catch (err) {
      setPeriods([]);
      setMessage(getErrorMessage(err, "Không thể tải danh sách kỳ lịch."));
    }
  }, [periodId]);

  const runCheck = useCallback(async () => {
    if (!periodId) return;
    try {
      setChecking(true);
      setMessage("");
      const res = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
      setConflictData(res);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi kiểm tra xung đột."));
    } finally {
      setChecking(false);
    }
  }, [periodId]);

  useEffect(() => {
    fetchPeriods();
  }, [fetchPeriods]);

  useEffect(() => {
    if (periodId) runCheck();
  }, [periodId, runCheck]);

  const conflictRows = useMemo(() => {
    if (!conflictData?.conflicts) return [];
    return conflictData.conflicts.map((c: ConflictDetail) => [
      `CF-${c.scheduleId.toString().padStart(3, "0")}`,
      c.conflictReasons.join(", "),
      c.staffName,
      new Date(c.workDate).toLocaleDateString("vi-VN"),
      c.shiftTypeId,
      c.shiftTypeName,
    ]);
  }, [conflictData]);

  const conflictDetails = useMemo(() => {
    if (!conflictData?.conflicts) return [];
    return conflictData.conflicts.map((c: ConflictDetail, i: number) => ({
      code: `CF-${c.scheduleId.toString().padStart(3, "0")}`,
      type: c.shiftTypeName,
      staff: c.staffName,
      date: new Date(c.workDate).toLocaleDateString("vi-VN"),
      module: c.shiftTypeId,
      severity: c.conflictReasons.some((r: string) => r.toLowerCase().includes("bù"))
        ? "Chặn lưu"
        : "Cảnh báo",
      description: c.conflictReasons.join("; "),
    }));
  }, [conflictData]);

  const summary = useMemo(() => {
    if (!conflictData) return [];
    const conflicts = conflictData.conflicts ?? [];
    return [
      ["Tổng lỗi", String(conflicts.length)],
      ["Chặn lưu", String(conflicts.filter((c: ConflictDetail) => c.conflictReasons.some((r: string) => r.toLowerCase().includes("bù"))).length)],
      ["Cảnh báo", String(conflicts.filter((c: ConflictDetail) => !c.conflictReasons.some((r: string) => r.toLowerCase().includes("bù"))).length)],
      ["Đã xử lý", "0"],
    ];
  }, [conflictData]);

  const affectedScopes = useMemo(() => {
    if (!conflictData?.conflicts) return [];
    const scopes: Record<string, { block: number; warn: number }> = {};
    for (const c of conflictData.conflicts) {
      if (!scopes[c.shiftTypeName]) scopes[c.shiftTypeName] = { block: 0, warn: 0 };
      if (c.conflictReasons.some((r: string) => r.toLowerCase().includes("bù"))) scopes[c.shiftTypeName].block++;
      else scopes[c.shiftTypeName].warn++;
    }
    return Object.entries(scopes).map(([name, counts]) => [
      name,
      `${counts.block > 0 ? `${counts.block} lỗi chặn lưu` : ""}${counts.block > 0 && counts.warn > 0 ? ", " : ""}${counts.warn > 0 ? `${counts.warn} cảnh báo` : ""}`.trim(),
    ]);
  }, [conflictData]);

  function getSeverityClass(severity: string) {
    if (severity === "Chặn lưu") return "bg-error-container text-error border border-error/20";
    if (severity === "Cảnh báo") return "bg-tertiary-fixed text-on-tertiary-fixed border border-on-tertiary-fixed/10";
    return "bg-surface-container-high text-on-surface-variant border border-outline/10";
  }

  function getSummaryAccent(label: string) {
    if (label === "Chặn lưu") return "border-l-4 border-l-error";
    if (label === "Đã xử lý") return "border-l-4 border-l-secondary";
    return "border-l-4 border-l-outline";
  }

  return (
    <DashboardShell
      activeCode="M06-CONFLICT"
      description="Quét toàn bộ lịch tháng, phát hiện trùng trực 24/24, thông tầm, phòng khám và ngày nghỉ bù."
      title="Cảnh báo xung đột thời gian thực"
    >
      <div className="space-y-6">
        {/* Header */}
        <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-label-sm text-on-surface-variant uppercase tracking-widest">Cảnh báo xung đột</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">
              Quét toàn bộ lịch tháng và gom các lỗi chặn lưu trước khi công bố lịch chính thức.
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <select
              className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface shadow-sm"
              value={periodId}
              onChange={(e) => setPeriodId(Number(e.target.value))}
            >
              {periods.map((p) => (
                <option key={p.id} value={p.id}>{p.periodName}</option>
              ))}
            </select>
            <button
              className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 disabled:opacity-50"
              disabled={checking}
              onClick={runCheck}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">{checking ? "sync" : "play_circle"}</span>
              {checking ? "Đang kiểm tra..." : "Chạy kiểm tra"}
            </button>
          </div>
        </section>

        {message && (
          <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">{message}</div>
        )}

        {/* Summary Cards */}
        <section className="grid gap-4 md:grid-cols-4">
          {summary.map((item) => {
            const [label, value] = item as [string, string];
            return (
              <div
                className={`rounded-lg border-t border-r border-b border-outline-variant bg-surface-container-lowest p-5 shadow-sm hover:bg-surface-container-low ${getSummaryAccent(label)}`}
                key={label}
              >
                <p className="text-label-sm text-on-surface-variant uppercase tracking-wider opacity-80">{label}</p>
                <p className="mt-3 text-display-lg text-on-surface">{value}</p>
              </div>
            );
          })}
        </section>

        <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
          <div className="space-y-6">
            {/* Error Table */}
            <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
              <div className="flex flex-col gap-4 border-b border-outline-variant bg-surface-container-low p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-title-lg text-on-surface">Bảng lỗi xung đột</h2>
                  <p className="mt-1 font-body-sm text-on-surface-variant">
                    Danh sách lỗi tổng hợp sau khi quét toàn bộ các module lịch.
                  </p>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface-container-low">
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Mã lỗi</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Loại lỗi</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Nhân sự</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Ngày</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Module</th>
                      <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Mức độ</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant font-body-sm">
                    {conflictRows.length === 0 ? (
                      <tr>
                        <td className="px-5 py-10 text-center font-body-sm text-on-surface-variant" colSpan={6}>
                          Không có lỗi xung đột nào.
                        </td>
                      </tr>
                    ) : (
                      conflictRows.map((row) => (
                        <tr className="transition-colors hover:bg-surface-container-low group" key={row[0]}>
                          <td className="px-5 py-3 font-semibold text-primary">{row[0]}</td>
                          <td className="px-5 py-3 text-on-surface">{row[1]}</td>
                          <td className="px-5 py-3 text-on-surface">{row[2]}</td>
                          <td className="px-5 py-3 text-on-surface-variant">{row[3]}</td>
                          <td className="px-5 py-3 text-on-surface-variant">{row[4]} — {row[5]}</td>
                          <td className="px-5 py-3">
                            <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold ${getSeverityClass(row[1]?.includes("bù") ? "Chặn lưu" : "Cảnh báo")}`}>
                              {row[1]?.includes("bù") ? "Chặn lưu" : "Cảnh báo"}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            {/* Conflict Detail Cards */}
            <section className="grid gap-4">
              {conflictDetails.map((item) => (
                <article
                  className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
                  key={item.code}
                >
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-title-lg text-on-surface font-semibold">{item.code}</h3>
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold ${getSeverityClass(item.severity)}`}>
                          {item.severity}
                        </span>
                      </div>
                      <p className="mt-2 font-label-md text-on-surface">{item.type}</p>
                      <p className="mt-1 font-body-sm text-on-surface-variant">{item.description}</p>
                    </div>
                  </div>

                  <div className="mt-5 grid gap-4 md:grid-cols-4">
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Nhân sự</span>
                      <span className="font-label-md text-on-surface">{item.staff}</span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Ngày</span>
                      <span className="font-label-md text-on-surface">{item.date}</span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Module</span>
                      <span className="font-label-md text-on-surface">{item.module}</span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Khuyến nghị</span>
                      <span className="font-label-md text-on-surface">
                        {item.severity === "Chặn lưu" ? "Sửa trước khi công bố lịch" : "Cho phép lưu bản nháp để rà soát thêm"}
                      </span>
                    </div>
                  </div>
                </article>
              ))}
            </section>
          </div>

          <aside className="space-y-4">
            {/* Affected Scopes */}
            <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
              <h2 className="font-title-lg text-on-surface">Phạm vi bị ảnh hưởng</h2>
              <div className="mt-4 space-y-3">
                {affectedScopes.length === 0 ? (
                  <p className="text-sm text-on-surface-variant">Không có phạm vi bị ảnh hưởng.</p>
                ) : (
                  affectedScopes.map((item) => {
                    const [name, detail] = item as [string, string];
                    return (
                      <div className="rounded-lg bg-surface-container-low p-3" key={name}>
                        <p className="font-label-md text-on-surface">{name}</p>
                        <p className="mt-1 font-body-sm text-on-surface-variant">{detail}</p>
                      </div>
                    );
                  })
                )}
              </div>
            </section>

            {/* Logic Panel */}
            <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
              <h2 className="font-title-lg text-on-surface">Logic kiểm tra</h2>
              <div className="mt-4 space-y-3 font-body-sm leading-relaxed text-on-surface-variant">
                <p>1. L01 không được trùng L02 cùng ngày.</p>
                <p>2. L03 không được trùng L04 cùng ngày.</p>
                <p>3. Ngày nghỉ bù bị khóa với mọi loại lịch khác.</p>
                <p>4. Ngoại lệ nghỉ phép được kiểm tra trước khi lưu.</p>
              </div>
            </section>

            {/* Lock Status Panel */}
            <section className={`rounded-lg border p-5 shadow-sm ${conflictData?.hasConflicts ? "border-error-container bg-error-container/10" : "border-secondary-container bg-secondary-container/10"}`}>
              <p className={`text-label-sm uppercase tracking-wider ${conflictData?.hasConflicts ? "text-error" : "text-secondary"}`}>
                Trạng thái lưu
              </p>
              <h2 className="mt-2 font-headline-md text-on-surface">
                {conflictData?.hasConflicts ? "Cần xử lý lỗi" : "Sẵn sàng công bố"}
              </h2>
              <p className="mt-2 font-body-sm leading-relaxed text-on-surface-variant">
                {conflictData?.hasConflicts
                  ? `Cần xử lý ${conflictData.totalConflicts} lỗi trước khi công bố lịch tháng.`
                  : "Không có lỗi xung đột. Lịch tháng sẵn sàng để công bố."}
              </p>
            </section>
          </aside>
        </div>
      </div>
    </DashboardShell>
  );
}
