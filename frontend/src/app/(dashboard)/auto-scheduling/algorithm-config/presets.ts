import type { PresetKey } from "@/components/algorithm-config/PresetSelector";
import type { RuntimeConfig } from "./types";

export type PresetConfig = {
  label: string;
  tagline: string;
  icon: string;
  color: string;
  colorBg: string;
  accent: string;
  config: RuntimeConfig;
};

const baseConfig = {
  overnightRecoveryHours: 24,
  minStaffPerShift: 1,
  maxStaffPerShift: 0,
  minShiftsPerStaff: 0,
  maxShiftsPerStaff: 0,
};

export const ALGORITHM_PRESETS: Record<PresetKey, PresetConfig> = {
  balanced: {
    label: "Cân bằng",
    tagline: "Cân bằng giữa coverage và fairness",
    icon: "balance",
    color: "text-blue-600",
    colorBg: "bg-blue-50",
    accent: "border-blue-300",
    config: {
      ...baseConfig,
      weekendWeight: 1.5,
      greedyCoverageThreshold: 0.85,
      balanceScoreMin: 0.75,
    },
  },
  fast: {
    label: "Nhanh",
    tagline: "Ưu tiên tốc độ, chất lượng chấp nhận được",
    icon: "bolt",
    color: "text-tertiary",
    colorBg: "bg-amber-100",
    accent: "border-tertiary",
    config: {
      ...baseConfig,
      weekendWeight: 2.0,
      greedyCoverageThreshold: 0.70,
      balanceScoreMin: 0.60,
    },
  },
  quality: {
    label: "Chất lượng",
    tagline: "Tối ưu coverage, bỏ qua tốc độ",
    icon: "stars",
    color: "text-purple-600",
    colorBg: "bg-purple-50",
    accent: "border-purple-300",
    config: {
      ...baseConfig,
      weekendWeight: 1.0,
      greedyCoverageThreshold: 0.95,
      balanceScoreMin: 0.80,
    },
  },
  conservative: {
    label: "Bảo thủ",
    tagline: "Ít thay đổi, giữ nguyên lịch cũ",
    icon: "history",
    color: "text-green-600",
    colorBg: "bg-green-50",
    accent: "border-green-300",
    config: {
      ...baseConfig,
      weekendWeight: 2.5,
      greedyCoverageThreshold: 0.60,
      balanceScoreMin: 0.90,
    },
  },
  custom: {
    label: "Tùy chỉnh",
    tagline: "Cấu hình hiện tại không khớp preset nào",
    icon: "tune",
    color: "text-on-surface-variant",
    colorBg: "bg-surface-container",
    accent: "border-outline-variant",
    config: {
      ...baseConfig,
      weekendWeight: 1.5,
      greedyCoverageThreshold: 0.85,
      balanceScoreMin: 0.75,
    },
  },
};

/** Trả PresetKey nếu cfg match chính xác 1 preset; ngược lại null */
export function detectPreset(cfg: RuntimeConfig): PresetKey | null {
  for (const [key, preset] of Object.entries(ALGORITHM_PRESETS) as [PresetKey, PresetConfig][]) {
    const p = preset.config;
    if (
      cfg.weekendWeight === p.weekendWeight &&
      cfg.greedyCoverageThreshold === p.greedyCoverageThreshold &&
      cfg.balanceScoreMin === p.balanceScoreMin &&
      cfg.minStaffPerShift === p.minStaffPerShift &&
      cfg.maxStaffPerShift === p.maxStaffPerShift &&
      cfg.minShiftsPerStaff === p.minShiftsPerStaff &&
      cfg.maxShiftsPerStaff === p.maxShiftsPerStaff &&
      cfg.overnightRecoveryHours === p.overnightRecoveryHours
    ) {
      return key;
    }
  }
  return null;
}
