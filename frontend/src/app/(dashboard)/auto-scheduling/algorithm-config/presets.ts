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
  autoCompensationEnabled: true,
  overnightRecoveryHours: 24,
  minStaffPerShift: 1,
  maxStaffPerShift: 0,
  minShiftsPerStaff: 0,
  maxShiftsPerStaff: 0,
};

export const ALGORITHM_PRESETS: Record<PresetKey, PresetConfig> = {
  balanced: {
    label: "Cân bằng",
    tagline: "Tốc độ & chất lượng hài hòa",
    icon: "psychology",
    color: "text-blue-600",
    colorBg: "bg-blue-50",
    accent: "border-blue-400",
    config: {
      ...baseConfig,
      maxIterations: 2000,
      weekendWeight: 2.5,
      greedyCoverageThreshold: 0.90,
      balanceScoreMin: 0.75,
      backtrackTimeLimitSeconds: 120,
    },
  },
  fast: {
    label: "Nhanh",
    tagline: "Phủ lịch nhanh, ưu tiên tốc độ",
    icon: "bolt",
    color: "text-amber-600",
    colorBg: "bg-amber-50",
    accent: "border-amber-400",
    config: {
      ...baseConfig,
      maxIterations: 500,
      weekendWeight: 1.5,
      greedyCoverageThreshold: 0.75,
      balanceScoreMin: 0.60,
      backtrackTimeLimitSeconds: 30,
    },
  },
  quality: {
    label: "Chất lượng cao",
    tagline: "Tìm lời giải tối ưu, chạy chậm hơn",
    icon: "verified_user",
    color: "text-emerald-600",
    colorBg: "bg-emerald-50",
    accent: "border-emerald-400",
    config: {
      ...baseConfig,
      maxIterations: 5000,
      weekendWeight: 3.0,
      greedyCoverageThreshold: 0.95,
      balanceScoreMin: 0.85,
      backtrackTimeLimitSeconds: 300,
    },
  },
  conservative: {
    label: "Thận trọng",
    tagline: "Ít thay đổi, giữ nguyên lịch hiện tại",
    icon: "shield",
    color: "text-slate-600",
    colorBg: "bg-slate-100",
    accent: "border-slate-400",
    config: {
      ...baseConfig,
      maxIterations: 1000,
      weekendWeight: 1.0,
      greedyCoverageThreshold: 0.60,
      balanceScoreMin: 0.50,
      backtrackTimeLimitSeconds: 60,
    },
  },
  // `custom` is a synthetic entry — when the runtime config doesn't match any
  // built-in preset (balanced/fast/quality/conservative) we surface a "custom"
  // tab so the user understands their current settings aren't a stock preset.
  custom: {
    label: "Tùy chỉnh",
    tagline: "Cấu hình hiện tại không khớp preset nào",
    icon: "tune",
    color: "text-on-surface-variant",
    colorBg: "bg-surface-container",
    accent: "border-outline-variant",
    config: {
      ...baseConfig,
      maxIterations: 1000,
      weekendWeight: 1.5,
      greedyCoverageThreshold: 0.85,
      balanceScoreMin: 0.75,
      backtrackTimeLimitSeconds: 60,
    },
  },
};

/** Trả PresetKey nếu cfg match chính xác 1 preset; ngược lại null */
export function detectPreset(cfg: RuntimeConfig): PresetKey | null {
  for (const [key, preset] of Object.entries(ALGORITHM_PRESETS) as [PresetKey, PresetConfig][]) {
    const p = preset.config;
    if (
      cfg.maxIterations === p.maxIterations &&
      cfg.weekendWeight === p.weekendWeight &&
      cfg.greedyCoverageThreshold === p.greedyCoverageThreshold &&
      cfg.balanceScoreMin === p.balanceScoreMin &&
      cfg.backtrackTimeLimitSeconds === p.backtrackTimeLimitSeconds &&
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