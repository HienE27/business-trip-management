"use client";

import { useState, useEffect } from "react";
import { api } from "@/lib/api";

interface RuntimeParamsSummary {
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  autoCompensationEnabled: boolean;
}

interface RuntimeParamsChipsProps {
  compact?: boolean;
}

export function RuntimeParamsChips({ compact = false }: RuntimeParamsChipsProps) {
  const [params, setParams] = useState<RuntimeParamsSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    api.getRuntimeConfig()
      .then((res) => {
        const raw = (res as { data?: RuntimeParamsSummary }).data ?? (res as unknown as RuntimeParamsSummary);
        if (!ignore) setParams(raw);
      })
      .catch(() => { /* non-critical */ })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, []);

  if (loading || !params) return null;

  const chips = [
    {
      key: "weekend_weight",
      label: compact ? "T7/CN" : "weekend_weight",
      value: params.weekendWeight,
      unit: "×",
      description: "Hệ số cuối tuần",
    },
    {
      key: "greedy_coverage_threshold",
      label: compact ? "Coverage" : "greedy_coverage_threshold",
      value: `${Math.round(params.greedyCoverageThreshold * 100)}%`,
      unit: "",
      description: "Ngưỡng phủ",
    },
    {
      key: "balance_score_min",
      label: compact ? "Balance" : "balance_score_min",
      value: `${Math.round(params.balanceScoreMin * 100)}%`,
      unit: "",
      description: "Ngưỡng cân bằng",
    },
  ];

  return (
    <div className="flex items-center gap-1.5 flex-wrap" title="Cấu hình runtime đang áp dụng">
      {chips.map((chip) => (
        <div
          key={chip.key}
          className="flex items-center gap-1 px-2 py-1 rounded-md bg-surface-container-low border border-outline-variant"
          title={`${chip.description}: ${chip.value}${chip.unit}`}
        >
          <code className="text-[10px] text-primary font-mono font-semibold leading-none">
            {chip.label}
          </code>
          <span className="text-[11px] font-bold text-on-surface tabular-nums leading-none">
            {chip.value}{chip.unit}
          </span>
        </div>
      ))}
    </div>
  );
}
