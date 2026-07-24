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
  maxStaffPerShift: 0,
  maxShiftsPerStaff: 12,
  maxShiftsPerDay: 0,
  autoAdjustConfig: true,
  l04CrossSpecialty: false,
  l04CrossSpecialtyRatio: 0.3,
};

/**
 * Preset "Lab-Eval" — dành cho demo/đánh giá chuyên khoa theo lời Hiến:
 *   - L04 dày (min 5, max 8 / ngày; min 3, max 0 / tuần)
 *   - cross-specialty OFF  → mỗi chuyên khoa tự gán, không mượn người ngoài
 *   - auto-adjust OFF       → config thủ công đã lưu, thuật toán không tự giảm
 *   - maxShiftsPerStaff 40 → đủ head-room cho L04 đa chuyên khoa
 *
 * Đây KHÔNG phải chế độ vận hành production — chỉ nạp sẵn config để đánh giá
 * "cross OFF" / leak = 0 trên dataset thật. Người dùng vẫn phải bấm Lưu để
 * ghi vào DB (giữ nguyên đặc tính "auto được, nhưng bắt buộc manual").
 */
const LAB_EVAL_CONFIG: RuntimeConfig = {
  ...baseConfig,
  weekendWeight: 2.5,
  greedyCoverageThreshold: 0.90,
  balanceScoreMin: 0.75,
  maxShiftsPerStaff: 40,
  autoAdjustConfig: false,
  // L04 dày
  l04MinPerDay: 5,
  l04MaxPerDay: 8,
  l04MinPerWeek: 3,
  l04MaxPerWeek: 0,
  // L01/L02/L03 giữ mục tiêu mặc định
  l01MinPerDay: 1, l02MinPerDay: 1, l03MinPerDay: 1,
  l01MaxPerDay: 0, l02MaxPerDay: 0, l03MaxPerDay: 0,
  l01MinPerWeek: 1, l02MinPerWeek: 2, l03MinPerWeek: 1,
  l01MaxPerWeek: 0, l02MaxPerWeek: 0, l03MaxPerWeek: 0,
  holidayMode: "SKIP",
  removedShiftTypes: [],
  l04CrossSpecialty: false,
  l04CrossSpecialtyRatio: 0.0,
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
      weekendWeight: 2.5,
      greedyCoverageThreshold: 0.90,
      balanceScoreMin: 0.75,
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
      weekendWeight: 1.5,
      greedyCoverageThreshold: 0.75,
      balanceScoreMin: 0.60,
    },
  },
  quality: {
    label: "Chất lượng cao",
    tagline: "CSP timeout cao hơn, tăng thời gian tìm lời giải",
    icon: "verified_user",
    color: "text-emerald-600",
    colorBg: "bg-emerald-50",
    accent: "border-emerald-400",
    config: {
      ...baseConfig,
      weekendWeight: 3.0,
      greedyCoverageThreshold: 0.95,
      balanceScoreMin: 0.85,
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
      weekendWeight: 1.0,
      greedyCoverageThreshold: 0.60,
      balanceScoreMin: 0.50,
    },
  },
  labEval: {
    label: "Lab-Eval",
    tagline: "L04 dày · cross OFF · auto-adjust OFF · đánh giá chuyên khoa",
    icon: "science",
    color: "text-purple-600",
    colorBg: "bg-purple-50",
    accent: "border-purple-400",
    config: LAB_EVAL_CONFIG,
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
      cfg.maxStaffPerShift === p.maxStaffPerShift &&
      cfg.maxShiftsPerStaff === p.maxShiftsPerStaff &&
      cfg.overnightRecoveryHours === p.overnightRecoveryHours &&
      cfg.maxShiftsPerDay === p.maxShiftsPerDay &&
      (cfg.autoAdjustConfig ?? true) === (p.autoAdjustConfig ?? true) &&
      (cfg.l04CrossSpecialty ?? false) === (p.l04CrossSpecialty ?? false) &&
      // L04 per-day/week thresholds — distinguishing Lab-Eval (L04 dày) from defaults
      (cfg.l04MinPerDay ?? 0) === (p.l04MinPerDay ?? 0) &&
      (cfg.l04MaxPerDay ?? 0) === (p.l04MaxPerDay ?? 0) &&
      (cfg.l04MinPerWeek ?? 0) === (p.l04MinPerWeek ?? 0)
    ) {
      return key;
    }
  }
  return null;
}
