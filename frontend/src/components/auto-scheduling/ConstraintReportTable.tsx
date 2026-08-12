"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

export interface ConstraintReportPayload {
  periodId: number;
  reports: Array<{
    id: number;
    runId?: string;
    algorithmType: string;
    createdAt?: string;
    report?: {
      hard?: Record<string, number>;
      soft?: Record<string, number>;
      perStaff?: Array<{
        staffId: number;
        displayName: string;
        constraintViolations: Record<string, number>;
      }>;
    };
    parseError?: string;
  }>;
}

export interface ConstraintReportTableProps {
  periodId: number | null;
}

/**
 * Phase 2.4 — Constraint report widget.
 *
 * <p>Fetches {@code GET /api/v1/scheduling/metrics/{periodId}/report} and
 * renders the hard/soft breakdown plus a per-staff violations table.
 */
export function ConstraintReportTable({ periodId }: ConstraintReportTableProps) {
  const [payload, setPayload] = useState<ConstraintReportPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchReport = useCallback(async () => {
    if (!periodId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<{ data: ConstraintReportPayload }>(
        `/scheduling/metrics/${periodId}/report`,
      );
      setPayload(res.data);
    } catch (err) {
      setError(getErrorMessage(err, "Tải dữ liệu thất bại"));
    } finally {
      setLoading(false);
    }
  }, [periodId]);

  useEffect(() => {
    void fetchReport();
  }, [fetchReport]);

  if (!periodId) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Chọn kỳ lịch để xem báo cáo ràng buộc.
        </p>
      </div>
    );
  }
  if (loading) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">Đang tải…</p>
      </div>
    );
  }
  if (error) {
    return (
      <div className="rounded-lg border border-red-300 bg-red-100 p-4">
        <p className="font-body-sm text-body-sm text-red-800">{error}</p>
        <Button variant="secondary" size="sm" onClick={fetchReport}>Thử lại</Button>
      </div>
    );
  }

  const reports = payload?.reports ?? [];
  if (reports.length === 0) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Kỳ này chưa có báo cáo ràng buộc nào được lưu.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {reports.map((row) => {
        const hard = row.report?.hard ?? {};
        const soft = row.report?.soft ?? {};
        const perStaff = row.report?.perStaff ?? [];
        const hardKeys = Object.keys(hard);
        const softKeys = Object.keys(soft);
        const allConstraintKeys = Array.from(new Set([...hardKeys, ...softKeys, ...perStaff.flatMap((p) => Object.keys(p.constraintViolations))]));

        return (
          <div
            key={row.id}
            className="space-y-3 rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <h3 className="font-headline-md text-headline-md text-on-surface">
                  Báo cáo #{row.id}
                </h3>
                <Badge tone="info">{row.algorithmType}</Badge>
              </div>
              <div className="flex items-center gap-2">
                {row.runId && (
                  <span className="font-label-sm text-label-sm text-on-surface-variant">
                    Run: <code className="font-mono text-label-sm">{row.runId}</code>
                  </span>
                )}
                {row.createdAt && (
                  <span className="font-label-sm text-label-sm text-on-surface-variant">
                    {new Date(row.createdAt).toLocaleString("vi-VN")}
                  </span>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <Bucket title="Hard" tone="error" data={hard} />
              <Bucket title="Soft" tone="primary" data={soft} />
            </div>

            <div className="overflow-x-auto">
              <h4 className="mb-2 font-title-lg text-title-lg text-on-surface">
                Vi phạm theo nhân sự
              </h4>
              <table className="min-w-full text-left border-collapse">
                <thead className="bg-surface-container-low">
                  <tr>
                    <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                      Nhân sự
                    </th>
                    {allConstraintKeys.map((k) => (
                      <th
                        key={k}
                        className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant"
                      >
                        {k}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {perStaff.length === 0 ? (
                    <tr>
                      <td
                        colSpan={allConstraintKeys.length + 1}
                        className="py-3 px-3 font-body-sm text-body-sm text-on-surface-variant"
                      >
                        Không có dữ liệu.
                      </td>
                    </tr>
                  ) : (
                    perStaff.map((p) => (
                      <tr key={p.staffId} className="hover:bg-surface-container-low">
                        <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface">
                          {p.displayName}
                          <span className="ml-2 font-label-sm text-label-sm text-on-surface-variant">
                            #{p.staffId}
                          </span>
                        </td>
                        {allConstraintKeys.map((k) => {
                          const v = p.constraintViolations[k] ?? 0;
                          return (
                            <td
                              key={k}
                              className={
                                v > 0
                                  ? "py-2 px-3 font-label-md text-label-md text-red-800"
                                  : "py-2 px-3 font-body-sm text-body-sm text-on-surface-variant"
                              }
                            >
                              {v}
                            </td>
                          );
                        })}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function Bucket({
  title,
  tone,
  data,
}: {
  title: string;
  tone: "error" | "primary";
  data: Record<string, number>;
}) {
  const entries = Object.entries(data);
  const total = entries.reduce((acc, [, v]) => acc + v, 0);
  return (
    <div
      className={
        tone === "error"
          ? "rounded-lg border border-red-300 bg-red-100 p-3"
          : "rounded-lg border border-blue-300 bg-blue-100 p-3"
      }
    >
      <div className="flex items-center justify-between">
        <h4
          className={
            tone === "error"
              ? "font-title-lg text-title-lg text-red-800"
              : "font-title-lg text-title-lg text-blue-800-container"
          }
        >
          {title}
        </h4>
        <span
          className={
            tone === "error"
              ? "rounded-full bg-error text-white"
              : "rounded-full bg-blue-100 px-3 py-1 text-[12px] font-semibold text-blue-800"
          }
        >
          Tổng: {total}
        </span>
      </div>
      <ul className="mt-2 space-y-1">
        {entries.length === 0 ? (
          <li className="font-body-sm text-body-sm opacity-70">(trống)</li>
        ) : (
          entries.map(([k, v]) => (
            <li
              key={k}
              className="flex items-center justify-between font-body-sm text-body-sm"
            >
              <span>{k}</span>
              <span className="font-label-md text-label-md">{v}</span>
            </li>
          ))
        )}
      </ul>
    </div>
  );
}

export default ConstraintReportTable;