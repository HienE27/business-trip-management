"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";

export interface AssignmentExplanationDTO {
  slotId?: number;
  staffId?: number | null;
  workDate?: string;
  shiftTypeId?: string;
  chosenReason?: string;
  constraintBreakdown?: Array<{
    constraintId: string;
    hard: boolean;
    violations: number;
    weight: number;
  }>;
  rejectedCandidates?: Array<{
    staffId: number;
    reason: string;
    blockingConstraintId?: string | null;
  }>;
}

interface ExplainPayload {
  periodId: number;
  slotId: number;
  explanation?: AssignmentExplanationDTO;
  note?: string;
}

export interface ExplainPanelProps {
  periodId: number | null;
  slotId: number | null;
}

/**
 * Phase 2.1 — Read-only "Inspect" panel for explaining one slot's assignment.
 *
 * <p>Fetches {@code GET /api/v1/scheduling/explain/{periodId}/{slotId}} and
 * renders the explanation tree as a Material-style card. Shown as a side
 * drawer from the auto-scheduling page.
 */
export function ExplainPanel({ periodId, slotId }: ExplainPanelProps) {
  const [data, setData] = useState<ExplainPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchExplain = useCallback(async () => {
    if (!periodId || !slotId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<{ data: ExplainPayload }>(
        `/scheduling/explain/${periodId}/${slotId}`,
      );
      setData(res.data);
    } catch (err) {
      setError(getErrorMessage(err, "Tải dữ liệu thất bại"));
    } finally {
      setLoading(false);
    }
  }, [periodId, slotId]);

  useEffect(() => {
    void fetchExplain();
  }, [fetchExplain]);

  if (!periodId || !slotId) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="text-body-sm text-on-surface-variant">
          Chọn một ca trên lịch để xem giải thích.
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-label-md text-label-md text-on-surface">Đang tải giải thích…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-error-container bg-error-container p-4">
        <p className="font-label-md text-label-md text-on-error-container">Lỗi: {error}</p>
        <Button variant="secondary" size="sm" onClick={fetchExplain}>Thử lại</Button>
      </div>
    );
  }

  const exp = data?.explanation;
  const breakdown = exp?.constraintBreakdown ?? [];
  const rejected = exp?.rejectedCandidates ?? [];

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <h3 className="font-headline-md text-headline-md text-on-surface">
          Giải thích ca #{exp?.slotId ?? slotId}
        </h3>
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          {exp?.workDate ?? "—"} · Loại {exp?.shiftTypeId ?? "—"}
        </p>
        <div className="mt-3 flex items-center gap-2">
          <span className="font-label-sm text-label-sm text-on-surface-variant">
            Nhân sự được chọn:
          </span>
          <span className="rounded-full bg-primary-fixed px-3 py-1 font-label-md text-label-md text-primary">
            {exp?.staffId != null ? `#${exp.staffId}` : "Chưa xếp"}
          </span>
        </div>
        <p className="mt-3 font-body-sm text-body-sm text-on-surface">
          <span className="font-semibold">Lý do:</span> {exp?.chosenReason ?? "—"}
        </p>
        {data?.note && (
          <p className="mt-2 font-body-sm text-body-sm text-on-surface-variant">
            {data.note}
          </p>
        )}
      </div>

      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <h4 className="font-title-lg text-title-lg text-on-surface">Đóng góp ràng buộc</h4>
        {breakdown.length === 0 ? (
          <p className="mt-2 font-body-sm text-body-sm text-on-surface-variant">
            Không có dữ liệu ràng buộc.
          </p>
        ) : (
          <table className="mt-3 w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant">
                <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                  Ràng buộc
                </th>
                <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                  Loại
                </th>
                <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                  Vi phạm
                </th>
                <th className="py-2 px-3 font-label-sm text-label-sm uppercase text-on-surface-variant">
                  Trọng số
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {breakdown.map((row) => (
                <tr key={row.constraintId} className="h-10">
                  <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface">
                    {row.constraintId}
                  </td>
                  <td className="py-2 px-3">
                    <span
                      className={
                        row.hard
                          ? "inline-flex items-center px-2 py-0.5 rounded-full bg-error-container text-on-error-container text-[12px] font-semibold"
                          : "inline-flex items-center px-2 py-0.5 rounded-full bg-surface-container-highest text-outline text-[12px] font-semibold"
                      }
                    >
                      {row.hard ? "Hard" : "Soft"}
                    </span>
                  </td>
                  <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface">
                    {row.violations}
                  </td>
                  <td className="py-2 px-3 font-body-sm text-body-sm text-on-surface-variant">
                    {row.weight === Infinity ? "∞" : row.weight}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <h4 className="font-title-lg text-title-lg text-on-surface">Ứng viên bị loại</h4>
        {rejected.length === 0 ? (
          <p className="mt-2 font-body-sm text-body-sm text-on-surface-variant">
            Không có ứng viên nào bị loại.
          </p>
        ) : (
          <ul className="mt-3 space-y-2">
            {rejected.map((c) => (
              <li
                key={c.staffId}
                className="flex items-start gap-3 rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2"
              >
                <span className="material-symbols-outlined text-on-surface-variant" aria-hidden="true">
                  person_off
                </span>
                <div className="flex-1">
                  <p className="font-label-md text-label-md text-on-surface">
                    Nhân sự #{c.staffId}
                  </p>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    {c.reason}
                    {c.blockingConstraintId ? ` · ${c.blockingConstraintId}` : ""}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

export default ExplainPanel;