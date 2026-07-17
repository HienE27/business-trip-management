import type { RuntimeConfig } from "./types";

export type ParamCategory = "business" | "advanced" | "monitoring" | "internal";

type ParamGroup = {
  id: string;
  label: string;
  icon: string;
  color: string;
  bg: string;
  /** Màu progress bar theo group */
  progressColor: string;
  accent: string;
  /** Nhóm hiển thị. Internal = ẩn hoàn toàn (chỉ lưu data). */
  category: ParamCategory;
  /** Nếu true, chỉ hiển thị giá trị, không cho chỉnh sửa. */
  readOnly?: boolean;
  /** Nếu true, ẩn hoàn toàn khỏi UI (chỉ lưu data). */
  hidden?: boolean;
  /** Mô tả cho toàn bộ group (hiện ở card header). */
  groupDesc?: string;
  params: readonly string[];
  descriptions: Record<string, { label: string; desc: string; hint: string }>;
};

/**
 * Phân loại config theo mức độ ảnh hưởng nghiệp vụ.
 *
 * - business: Ảnh hưởng trực tiếp đến lịch. Manager được phép chỉnh.
 * - advanced: Algorithm tuning. Chỉ hiển thị, không cho Manager chỉnh.
 * - monitoring: Threshold theo dõi. Chỉ hiển thị, read-only.
 * - internal: Không hiển thị trên UI, chỉ lưu data (deprecated/reserved).
 */
export const PARAM_GROUPS: readonly ParamGroup[] = [
  // ── BUSINESS: Manager được phép chỉnh ────────────────────────────────
  {
    id: "shifts",
    label: "Giới hạn ca / nhân sự",
    icon: "groups",
    color: "text-blue-600",
    bg: "bg-blue-50",
    progressColor: "bg-blue-500",
    accent: "border-l-4 border-l-blue-500",
    category: "business",
    groupDesc: "Giới hạn tối đa mỗi nhân sự trong kỳ xếp lịch",
    params: ["max_staff_per_shift", "max_shifts_per_staff"],
    descriptions: {
      max_staff_per_shift: {
        label: "max_staff",
        desc: "Trần nhân sự tối đa mỗi ca khi thuật toán gán lịch. 0 = không giới hạn.",
        hint: "0–20 · Mặc định: 0 (không giới hạn) · Đang áp dụng",
      },
      max_shifts_per_staff: {
        label: "max_shifts",
        desc: "Số ca trực tối đa mỗi nhân sự trong kỳ. 0 = dùng giới hạn theo hồ sơ nhân sự.",
        hint: "0–100 · Mặc định: 0 · Đang áp dụng",
      },
    },
  },
  {
    id: "weights",
    label: "Ngày lễ",
    icon: "event_note",
    color: "text-teal-600",
    bg: "bg-teal-50",
    progressColor: "bg-teal-500",
    accent: "border-l-4 border-l-teal-500",
    category: "business",
    groupDesc: "Cách xử lý ngày lễ khi tạo yêu cầu nhân sự",
    params: ["holiday_mode"],
    descriptions: {
      holiday_mode: {
        label: "holiday_mode",
        desc: "Xử lý ngày lễ: SKIP = bỏ qua, PARTIAL = giảm cường độ xếp lịch.",
        hint: "SKIP · PARTIAL · Đang áp dụng",
      },
    },
  },
  {
    id: "excluded",
    label: "Loại lịch bỏ qua",
    icon: "block",
    color: "text-red-600",
    bg: "bg-red-50",
    progressColor: "bg-red-500",
    accent: "border-l-4 border-l-red-500",
    category: "business",
    groupDesc: "Các loại lịch bị bỏ qua khi tự động tạo yêu cầu cho kỳ mới",
    params: ["removed_shift_types"],
    descriptions: {
      removed_shift_types: {
        label: "removed_shift_types",
        desc: "Các mã loại lịch (L01..L04) bị bỏ qua khi tự động tạo yêu cầu cho kỳ mới.",
        hint: "Nhấn chip để bật/tắt · Mặc định: rỗng · Đang áp dụng",
      },
    },
  },
  // ── INTERNAL: Ẩn hoàn toàn ──────────────────────────────────────────
  {
    id: "internal",
    label: "Nội bộ",
    icon: "lock",
    color: "text-gray-400",
    bg: "bg-gray-50",
    progressColor: "bg-gray-300",
    accent: "border-l-4 border-l-gray-300",
    category: "internal",
    hidden: true,
    readOnly: true,
    params: [
      "balance_score_min",
      "overnight_recovery_hours",
      // ── DEPRECATED: Reserved for future use. Không dùng trong scheduler v1.0. ──
      // Xem chi tiết: audit 2026-07-17
      "min_staff_per_shift",
      "min_shifts_per_staff",
    ],
    descriptions: {
      balance_score_min: {
        label: "balance_score",
        desc: "[Reserved] Ngưỡng cân bằng tải. Hiện tại không dùng.",
        hint: "0.3–1.0 · Mặc định: 0.70 · Internal",
      },
      overnight_recovery_hours: {
        label: "recovery_hours",
        desc: "[Internal] Tham chiếu nghỉ ngơi. Ràng buộc thực tế theo ngày nghỉ bù.",
        hint: "12–72 giờ · Mặc định: 24 · Internal",
      },
      min_staff_per_shift: {
        label: "min_staff",
        desc: "[Deprecated] Đã ngừng sử dụng. Không ảnh hưởng đến scheduler v1.0. Để tương thích — sẽ xóa ở v1.1.",
        hint: "Internal · Reserved",
      },
      min_shifts_per_staff: {
        label: "min_shifts",
        desc: "[Deprecated] Đã ngừng sử dụng. Không ảnh hưởng đến scheduler v1.0. Để tương thích — sẽ xóa ở v1.1.",
        hint: "Internal · Reserved",
      },
    },
  },
];

/* ─── Param category constants ──────────────────────────────────── */

/** IDs của group hiển thị nhưng read-only (Manager không chỉnh được). */
export const READ_ONLY_GROUP_IDS = new Set(["advanced", "monitoring", "internal"]);

/* ─── Shift-type limits ────────────────────────────────────── */

export type ShiftTypeGroupId = "l01" | "l02" | "l03" | "l04";

export type ShiftTypeGroup = {
  id: ShiftTypeGroupId;
  label: string;
  subtitle: string;
  icon: string;
  color: string;
  colorBg: string;
  borderColor: string;
  description: string;
  /** Params visible in the UI card. */
  params: readonly (keyof RuntimeConfig)[];
  /** Params hidden from UI but kept in data (reserved for future use). */
  hiddenParams?: readonly (keyof RuntimeConfig)[];
};

export const SHIFT_TYPE_GROUPS: readonly ShiftTypeGroup[] = [
  {
    id: "l01", label: "L01", subtitle: "Trực 24/24", icon: "emergency",
    color: "text-red-600", colorBg: "bg-red-50", borderColor: "border-red-400",
    description: "Ca trực liên tục 24h, có nghỉ bù",
    params: ["l01MinPerDay", "l01MaxPerDay", "l01MaxPerWeek"],
    hiddenParams: ["l01MinPerWeek"], // Reserved — not used in v1.0 scheduler
  },
  {
    id: "l02", label: "L02", subtitle: "Thông tầm", icon: "schedule",
    color: "text-blue-600", colorBg: "bg-blue-50", borderColor: "border-blue-400",
    description: "Ca ngày, không nghỉ trưa",
    params: ["l02MinPerDay", "l02MaxPerDay", "l02MaxPerWeek"],
    hiddenParams: ["l02MinPerWeek"], // Reserved — not used in v1.0 scheduler
  },
  {
    id: "l03", label: "L03", subtitle: "PK Dịch vụ", icon: "medical_services",
    color: "text-green-600", colorBg: "bg-green-50", borderColor: "border-green-400",
    description: "Ca khám dịch vụ, buổi sáng hoặc chiều",
    params: ["l03MinPerDay", "l03MaxPerDay", "l03MaxPerWeek"],
    hiddenParams: ["l03MinPerWeek"], // Reserved — not used in v1.0 scheduler
  },
  {
    id: "l04", label: "L04", subtitle: "PK Chuyên gia", icon: "stethoscope",
    color: "text-purple-600", colorBg: "bg-purple-50", borderColor: "border-purple-400",
    description: "Ca khám chuyên sâu, thời gian dài hơn",
    params: ["l04MinPerDay", "l04MaxPerDay", "l04MaxPerWeek"],
    hiddenParams: ["l04MinPerWeek"], // Reserved — not used in v1.0 scheduler
  },
] as const;

const SHIFT_PARAM_LABELS: Record<string, string> = {
  MinPerDay: "Mục tiêu/ngày",
  MaxPerDay: "Trần ca/ngày",
  MinPerWeek: "Tối thiểu/người/tuần",
  MaxPerWeek: "Trần ca/người/tuần",
};

const SHIFT_PARAM_TOOLTIPS: Record<string, string> = {
  MinPerDay:
    "Số ca L0X cần tạo mỗi ngày. Thuật toán cố gắng đạt mục tiêu này, không phá ràng buộc cứng.",
  MaxPerDay:
    "Số ca tối đa mỗi ngày. Scheduler không tạo quá số lượng này. 0 = không giới hạn.",
  MinPerWeek:
    "Mỗi nhân sự tối thiểu X ca L0X trong 1 tuần — đảm bảo chia đều, tránh bỏ sót. [Reserved — chưa dùng trong scheduler v1.0]",
  MaxPerWeek:
    "HARD CONSTRAINT: Nhân sự đạt giới hạn sẽ không được xếp thêm loại lịch này trong tuần. 0 = không giới hạn.",
};

const SHIFT_PARAM_UNITS: Record<string, string> = {
  MinPerDay: "ca/ngày (toàn khoa)",
  MaxPerDay: "ca/ngày (toàn khoa)",
  MinPerWeek: "ca/người/tuần",
  MaxPerWeek: "ca/người/tuần",
};

export function getShiftRowLabel(param: string): string {
  const suffix = Object.keys(SHIFT_PARAM_LABELS).find(k => param.endsWith(k));
  return suffix ? SHIFT_PARAM_LABELS[suffix] : param;
}

export function getShiftRowTooltip(param: string): string {
  const suffix = Object.keys(SHIFT_PARAM_TOOLTIPS).find(k => param.endsWith(k));
  return suffix ? SHIFT_PARAM_TOOLTIPS[suffix] : "";
}

export function getShiftRowUnit(param: string): string {
  const suffix = Object.keys(SHIFT_PARAM_UNITS).find(k => param.endsWith(k));
  return suffix ? SHIFT_PARAM_UNITS[suffix] : "";
}

/* ─── Numeric param display helpers ─────────────────────────── */

const PERCENT_PARAMS = new Set(["greedy_coverage_threshold"]);

export function getParamBounds(param: string): { min: number; max: number; step: number } {
  if (param === "greedy_coverage_threshold") return { min: 0.5, max: 1, step: 0.05 };
  if (param === "balance_score_min") return { min: 0.3, max: 1, step: 0.05 };
  if (param === "weekend_weight") return { min: 1, max: 5, step: 0.05 };
  if (param === "min_staff_per_shift") return { min: 0, max: 10, step: 1 };
  if (param === "max_staff_per_shift" || param === "max_shifts_per_staff") return { min: 0, max: 100, step: 1 };
  if (param === "min_shifts_per_staff") return { min: 0, max: 50, step: 1 };
  if (param === "overnight_recovery_hours") return { min: 12, max: 72, step: 1 };
  // Internal params without bounds → default 0–100
  return { min: 0, max: 100, step: 1 };
}

export function formatParamDisplay(param: string, numVal: number): string {
  if (PERCENT_PARAMS.has(param)) return `${Math.round(numVal * 100)}%`;
  if (param === "weekend_weight") return `${numVal.toFixed(1)}×`;
  if (param === "overnight_recovery_hours") return `${numVal}h`;
  if (numVal === 0) return "Tắt";
  return numVal.toLocaleString();
}

export function calcProgressPct(param: string, numVal: number): number {
  const { min, max } = getParamBounds(param);
  if (max === min) return 0;
  return Math.min(100, Math.max(0, ((numVal - min) / (max - min)) * 100));
}

export function getParamProgressColor(groupId: string): string {
  return PARAM_GROUPS.find(g => g.id === groupId)?.progressColor ?? "bg-blue-500";
}