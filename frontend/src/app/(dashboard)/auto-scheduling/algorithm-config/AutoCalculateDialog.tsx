"use client";

import { useState, useMemo, useEffect } from "react";
import { Button, FormInput } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";

type ShiftTypeId = "L01" | "L02" | "L03" | "L04";

const SHIFT_META: Record<ShiftTypeId, { label: string; subtitle: string; color: string; defaultEligible: number }> = {
  L01: { label: "L01", subtitle: "Trực 24/24", color: "text-red-600", defaultEligible: 8 },
  L02: { label: "L02", subtitle: "Thông tầm", color: "text-blue-600", defaultEligible: 8 },
  L03: { label: "L03", subtitle: "PK Dịch vụ", color: "text-green-600", defaultEligible: 8 },
  L04: { label: "L04", subtitle: "PK Chuyên gia", color: "text-purple-600", defaultEligible: 20 },
};

export type AutoCalculateInput = {
  periodDays: number;
  periodWeeks: number;
  targetsPerStaffPerMonth: Record<ShiftTypeId, number>;
  eligibleStaff: Record<ShiftTypeId, number>;
};

export type AutoCalculateResult = {
  l01MinPerDay: number; l01MaxPerDay: number; l01MinPerWeek: number; l01MaxPerWeek: number;
  l02MinPerDay: number; l02MaxPerDay: number; l02MinPerWeek: number; l02MaxPerWeek: number;
  l03MinPerDay: number; l03MaxPerDay: number; l03MinPerWeek: number; l03MaxPerWeek: number;
  l04MinPerDay: number; l04MaxPerDay: number; l04MinPerWeek: number; l04MaxPerWeek: number;
};

type Props = {
  open: boolean;
  onClose: () => void;
  onApply: (result: AutoCalculateResult) => void;
  initialValues?: Partial<AutoCalculateInput>;
};

const round1 = (x: number) => Math.round(x * 10) / 10;

function computeConfig(input: AutoCalculateInput): AutoCalculateResult {
  const out: Record<string, number> = {};
  for (const tid of ["L01", "L02", "L03", "L04"] as ShiftTypeId[]) {
    const targetPerStaff = input.targetsPerStaffPerMonth[tid] ?? 0;
    const eligible = Math.max(1, input.eligibleStaff[tid]);
    const days = Math.max(1, input.periodDays);
    const weeks = Math.max(1, input.periodWeeks);

    const minPerDay = Math.max(1, Math.floor((targetPerStaff * eligible) / days));
    const minPerWeek = Math.max(1, Math.round(targetPerStaff / weeks));
    const maxPerWeek = Math.max(minPerWeek + 1, Math.round((targetPerStaff / weeks) * 1.5));
    const maxPerDay = Math.max(minPerDay, Math.ceil(maxPerWeek * 1.2));

    out[`${tid.toLowerCase()}MinPerDay`] = minPerDay;
    out[`${tid.toLowerCase()}MaxPerDay`] = maxPerDay;
    out[`${tid.toLowerCase()}MinPerWeek`] = minPerWeek;
    out[`${tid.toLowerCase()}MaxPerWeek`] = maxPerWeek;
  }
  return out as unknown as AutoCalculateResult;
}

export function AutoCalculateDialog({ open, onClose, onApply, initialValues }: Props) {
  const [periodDays, setPeriodDays] = useState(initialValues?.periodDays ?? 30);
  const [periodWeeks, setPeriodWeeks] = useState(initialValues?.periodWeeks ?? 4);
  const [targets, setTargets] = useState<Record<ShiftTypeId, number>>({
    L01: initialValues?.targetsPerStaffPerMonth?.L01 ?? 7,
    L02: initialValues?.targetsPerStaffPerMonth?.L02 ?? 8,
    L03: initialValues?.targetsPerStaffPerMonth?.L03 ?? 9,
    L04: initialValues?.targetsPerStaffPerMonth?.L04 ?? 16,
  });
  const [eligible, setEligible] = useState<Record<ShiftTypeId, number>>({
    L01: initialValues?.eligibleStaff?.L01 ?? 8,
    L02: initialValues?.eligibleStaff?.L02 ?? 8,
    L03: initialValues?.eligibleStaff?.L03 ?? 8,
    L04: initialValues?.eligibleStaff?.L04 ?? 20,
  });
  const [expandEligibility, setExpandEligibility] = useState(false);
  const [recommendation, setRecommendation] = useState<{
    config: AutoCalculateResult;
    totalShiftsExpected: number;
    rationale: string;
  } | null>(null);
  const [recommending, setRecommending] = useState(false);
  const [recommendError, setRecommendError] = useState<string | null>(null);

  const computed = useMemo(
    () =>
      computeConfig({
        periodDays,
        periodWeeks,
        targetsPerStaffPerMonth: targets,
        eligibleStaff: eligible,
      }),
    [periodDays, periodWeeks, targets, eligible]
  );

  const totalTarget = (Object.values(targets) as number[]).reduce((s, v) => s + v, 0);
  const totalGenerated = (["L01", "L02", "L03", "L04"] as ShiftTypeId[]).reduce(
    (sum, tid) => sum + targets[tid] * eligible[tid],
    0
  );

  async function fetchAIRecommendation() {
    setRecommending(true);
    setRecommendError(null);
    try {
      const resp = await api.recommendAutoGenConfig({
        periodDays,
        periodWeeks,
        totalStaff: 20,
        eligibleStaff: { L01: eligible.L01, L02: eligible.L02, L03: eligible.L03, L04: eligible.L04 },
        targetPerStaffPerMonth: { L01: targets.L01, L02: targets.L02, L03: targets.L03, L04: targets.L04 },
        expandNonL04Eligibility: expandEligibility,
      });
      const r = resp as unknown as {
        success: boolean;
        data: {
          recommendedConfig: AutoCalculateResult;
          totalShiftsExpected: number;
          rationale: string;
        };
      };
      setRecommendation({
        config: r.data.recommendedConfig,
        totalShiftsExpected: r.data.totalShiftsExpected,
        rationale: r.data.rationale,
      });
    } catch (err) {
      setRecommendError(getErrorMessage(err, "Không thể lấy đề xuất"));
    } finally {
      setRecommending(false);
    }
  }

  useEffect(() => {
    if (!open) {
      setRecommendation(null);
      setRecommendError(null);
    }
  }, [open]);

  if (!open) return null;

  const shiftIds: ShiftTypeId[] = ["L01", "L02", "L03", "L04"];

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="auto-calc-title"
    >
      <div
        className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-lg w-full max-w-3xl max-h-[90vh] overflow-y-auto animate-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="sticky top-0 bg-surface-container-lowest border-b border-outline-variant px-6 py-4 flex items-center justify-between z-10">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-fixed text-primary">
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">calculate</span>
            </div>
            <div>
              <h2 id="auto-calc-title" className="text-title-md font-semibold text-on-surface">
                Tự động tính toán giới hạn
              </h2>
              <p className="text-[12px] text-on-surface-variant mt-0.5">
                Nhập mục tiêu → hệ thống tính min/max/ngày/tuần
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container-high transition-colors"
            aria-label="Đóng"
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
          </button>
        </div>

        <div className="p-6 space-y-6">
          <section className="bg-surface-container-low rounded-xl p-4 border border-outline-variant">
            <h3 className="text-label-md font-semibold text-on-surface mb-3 flex items-center gap-2">
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">event</span>
              Thông tin kỳ lịch
            </h3>
            <div className="grid grid-cols-2 gap-3">
              <FormInput
                label="Số ngày trong kỳ"
                type="number"
                min={1}
                max={31}
                value={periodDays}
                onChange={(v) => setPeriodDays(Math.max(1, parseInt(String(v)) || 1))}
                hint="VD: tháng 9 = 30 ngày"
              />
              <FormInput
                label="Số tuần trong kỳ"
                type="number"
                min={1}
                max={6}
                value={periodWeeks}
                onChange={(v) => setPeriodWeeks(Math.max(1, parseInt(String(v)) || 1))}
                hint="Mặc định 4 tuần"
              />
            </div>
          </section>

          <section>
            <h3 className="text-label-md font-semibold text-on-surface mb-3 flex items-center gap-2">
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">target</span>
              Mục tiêu phân bổ ca
            </h3>
            <p className="text-[12px] text-on-surface-variant mb-3">
              Số ca <strong>mỗi người</strong> mong muốn trong cả kỳ (tháng).
            </p>
            <div className="border border-outline-variant rounded-xl overflow-hidden">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-surface-container-low border-b border-outline-variant">
                    <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase">Loại</th>
                    <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase">Số người đủ điều kiện</th>
                    <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase">Ca / người / tháng</th>
                    <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-right">Tổng ca kỳ</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {shiftIds.map((tid) => (
                    <tr key={tid} className="hover:bg-surface-container-lowest transition-colors">
                      <td className="py-2 px-3">
                        <div className="flex items-center gap-2">
                          <span className={`font-mono font-bold text-[13px] ${SHIFT_META[tid].color}`}>{tid}</span>
                          <span className="text-[12px] text-on-surface-variant">{SHIFT_META[tid].subtitle}</span>
                        </div>
                      </td>
                      <td className="py-2 px-3">
                        <input
                          type="number"
                          min={1}
                          max={50}
                          value={eligible[tid]}
                          onChange={(e) =>
                            setEligible((prev) => ({ ...prev, [tid]: Math.max(1, parseInt(e.target.value) || 1) }))
                          }
                          className="w-20 h-8 px-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-sm text-right font-mono tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                      </td>
                      <td className="py-2 px-3">
                        <input
                          type="number"
                          min={0}
                          max={50}
                          value={targets[tid]}
                          onChange={(e) =>
                            setTargets((prev) => ({ ...prev, [tid]: Math.max(0, parseInt(e.target.value) || 0) }))
                          }
                          className="w-20 h-8 px-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-sm text-right font-mono tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                      </td>
                      <td className="py-2 px-3 text-right font-mono font-semibold text-on-surface tabular-nums">
                        {targets[tid] * eligible[tid]}
                      </td>
                    </tr>
                  ))}
                  <tr className="bg-primary-fixed/30 font-semibold">
                    <td className="py-2 px-3 text-on-surface">Tổng</td>
                    <td className="py-2 px-3 text-right font-mono tabular-nums">
                      {shiftIds.reduce((s, t) => s + eligible[t], 0)}
                    </td>
                    <td className="py-2 px-3 text-right font-mono tabular-nums">{totalTarget}</td>
                    <td className="py-2 px-3 text-right font-mono tabular-nums text-primary">{totalGenerated}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section className="bg-secondary-container/20 rounded-xl p-4 border border-secondary/30">
            <h3 className="text-label-md font-semibold text-on-surface mb-3 flex items-center gap-2">
              <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">preview</span>
              Kết quả tính toán (preview)
            </h3>
            <div className="flex items-center gap-2 mb-3 px-3 py-2 rounded-lg bg-surface-container-lowest border border-outline-variant">
              <input
                type="checkbox"
                id="expand-eligibility"
                checked={expandEligibility}
                onChange={(e) => setExpandEligibility(e.target.checked)}
                className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
              />
              <label htmlFor="expand-eligibility" className="text-[12px] text-on-surface cursor-pointer flex-1">
                Mở rộng eligibility L01/L02/L03 (cho tất cả 10 specialties - khuyến nghị nếu eligibility &lt; 10 người)
              </label>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {shiftIds.map((tid) => {
                const c = recommendation?.config ?? computed;
                const min = c[`${tid.toLowerCase()}MinPerDay` as keyof AutoCalculateResult] as number;
                const max = c[`${tid.toLowerCase()}MaxPerDay` as keyof AutoCalculateResult] as number;
                const minW = c[`${tid.toLowerCase()}MinPerWeek` as keyof AutoCalculateResult] as number;
                const maxW = c[`${tid.toLowerCase()}MaxPerWeek` as keyof AutoCalculateResult] as number;
                return (
                  <div key={tid} className="bg-surface-container-lowest rounded-lg p-3 border border-outline-variant">
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`font-mono font-bold ${SHIFT_META[tid].color}`}>{tid}</span>
                      <span className="text-[11px] text-on-surface-variant">{SHIFT_META[tid].subtitle}</span>
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-[12px]">
                      <div className="flex justify-between"><span className="text-on-surface-variant">Min/ngày:</span><span className="font-mono font-semibold tabular-nums">{min}</span></div>
                      <div className="flex justify-between"><span className="text-on-surface-variant">Max/ngày:</span><span className="font-mono font-semibold tabular-nums">{max}</span></div>
                      <div className="flex justify-between"><span className="text-on-surface-variant">Min/tuần:</span><span className="font-mono font-semibold tabular-nums">{minW}</span></div>
                      <div className="flex justify-between"><span className="text-on-surface-variant">Max/tuần:</span><span className="font-mono font-semibold tabular-nums">{maxW}</span></div>
                    </div>
                  </div>
                );
              })}
            </div>
            <p className="text-[11px] text-on-surface-variant mt-3 leading-relaxed">
              <strong>Công thức:</strong> min/ngày = ⌈(target × eligible) / ngày⌋ · max/tuần = ⌈(target / tuần) × 1.5⌉ (buffer 50%) · max/ngày = ⌈max/tuần × 1.2⌉
            </p>

            <div className="mt-4 pt-3 border-t border-secondary/20">
              <div className="flex items-center justify-between gap-2 flex-wrap mb-2">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">auto_awesome</span>
                  <span className="text-[13px] font-semibold text-on-surface">AI đề xuất (gọi backend)</span>
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={fetchAIRecommendation}
                  loading={recommending}
                  disabled={recommending}
                  icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">neurology</span>}
                >
                  {recommending ? "Đang tính..." : "Lấy đề xuất AI"}
                </Button>
              </div>
              {recommendError && (
                <div className="text-[12px] text-error bg-error-container/30 border border-error/30 rounded-lg px-3 py-2 mb-2">
                  {recommendError}
                </div>
              )}
              {recommendation && (
                <div className="bg-surface-container-lowest rounded-lg p-3 border border-secondary/30 space-y-2">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-[12px] text-on-surface-variant">Tổng ca dự kiến:</span>
                    <span className="font-mono font-bold text-primary tabular-nums">{recommendation.totalShiftsExpected}</span>
                  </div>
                  <p className="text-[11px] text-on-surface leading-relaxed">
                    {recommendation.rationale}
                  </p>
                </div>
              )}
            </div>
          </section>
        </div>

        <div className="sticky bottom-0 bg-surface-container-lowest border-t border-outline-variant px-6 py-4 flex items-center justify-between gap-3">
          <Button variant="ghost" size="sm" onClick={onClose}>Hủy</Button>
          <Button
            variant="primary"
            size="sm"
            icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">check</span>}
            onClick={() => {
              onApply(computed);
              onClose();
            }}
          >
            Áp dụng vào form
          </Button>
        </div>
      </div>
    </div>
  );
}