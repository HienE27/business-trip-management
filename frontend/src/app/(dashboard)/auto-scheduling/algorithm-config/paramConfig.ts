import type { RuntimeConfig } from "./types";

type ParamGroup = {
  id: string;
  label: string;
  icon: string;
  color: string;
  bg: string;
  /** Màu progress bar theo group */
  progressColor: string;
  accent: string;
  params: readonly string[];
  descriptions: Record<string, { label: string; desc: string; hint: string }>;
};

export const PARAM_GROUPS: readonly ParamGroup[] = [
  {
    id: "shifts",
    label: "Số ca / nhân sự",
    icon: "groups",
    color: "text-blue-600",
    bg: "bg-blue-50",
    progressColor: "bg-blue-500",
    accent: "border-l-4 border-l-blue-500",
    params: ["min_staff_per_shift", "max_staff_per_shift", "min_shifts_per_staff", "max_shifts_per_staff"],
    descriptions: {
      min_staff_per_shift: { label: "min_staff", desc: "Ngưỡng theo dõi số nhân sự tối thiểu mỗi ca; hiện dùng để cảnh báo/chất lượng, không ép lịch vượt ràng buộc cứng.", hint: "0–10 · Mặc định: 1 · Theo dõi" },
      max_staff_per_shift: { label: "max_staff", desc: "Trần nhân sự tối đa mỗi ca khi thuật toán gán lịch. 0 = không giới hạn.", hint: "0–20 · Mặc định: 0 (không giới hạn) · Đang áp dụng" },
      min_shifts_per_staff: { label: "min_shifts", desc: "Ngưỡng theo dõi số ca tối thiểu mỗi nhân sự trong kỳ; dùng cho đánh giá cân bằng, không ép lịch giả.", hint: "0–50 · Mặc định: 0 · Theo dõi" },
      max_shifts_per_staff: { label: "max_shifts", desc: "Số ca trực tối đa mỗi nhân sự trong kỳ. 0 = dùng giới hạn theo hồ sơ nhân sự.", hint: "0–100 · Mặc định: 0 · Đang áp dụng" },
    },
  },
  {
    id: "thresholds",
    label: "Ngưỡng xếp lịch",
    icon: "donut_small",
    color: "text-blue-600",
    bg: "bg-blue-50",
    progressColor: "bg-blue-500",
    accent: "border-l-4 border-l-blue-500",
    params: ["greedy_coverage_threshold", "balance_score_min"],
    descriptions: {
      greedy_coverage_threshold: { label: "greedy_threshold", desc: "Greedy dừng sớm khi đạt ngưỡng. Giảm → chạy nhanh hơn. Tăng → phủ kỹ hơn.", hint: "0.5–1.0 · Mặc định: 0.85" },
      balance_score_min: { label: "balance_score", desc: "Ngưỡng cân bằng tải tối thiểu. Cao → phân bổ công bằng hơn nhưng khó đạt.", hint: "0.3–1.0 · Mặc định: 0.70" },
    },
  },
  {
    id: "weights",
    label: "Trọng số & ngày lễ",
    icon: "event_note",
    color: "text-teal-600",
    bg: "bg-teal-50",
    progressColor: "bg-teal-500",
    accent: "border-l-4 border-l-teal-500",
    params: ["weekend_weight", "holiday_mode"],
    descriptions: {
      weekend_weight: { label: "weekend_weight", desc: "Hệ số nhân khi tính penalty T7/CN. >1 ưu tiên tránh cuối tuần. Đặt=1 để tắt ưu tiên.", hint: "1.0–5.0 · Mặc định: 2.0" },
      holiday_mode: { label: "holiday_mode", desc: "Xử lý ngày lễ: SKIP = bỏ qua, PARTIAL = giảm cường độ xếp lịch.", hint: "SKIP · PARTIAL" },
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
    params: ["removed_shift_types"],
    descriptions: {
      removed_shift_types: { label: "removed_shift_types", desc: "Các mã loại lịch (L01..L04) bị bỏ qua khi tự động tạo yêu cầu cho kỳ mới.", hint: "Nhấn chip để bật/tắt · Mặc định: rỗng" },
    },
  },
  {
    id: "limits",
    label: "Giới hạn thuật toán",
    icon: "memory",
    color: "text-indigo-600",
    bg: "bg-indigo-50",
    progressColor: "bg-indigo-500",
    accent: "border-l-4 border-l-indigo-500",
    params: ["max_iterations", "backtrack_time_limit_seconds"],
    descriptions: {
      max_iterations: { label: "max_iterations", desc: "Số vòng lặp tối đa Backtracking. Tăng → lời giải tốt hơn nhưng chậm hơn.", hint: "100–10000 · Mặc định: 1000" },
      backtrack_time_limit_seconds: { label: "time_limit", desc: "Giới hạn thời gian Backtracking (giây). Hết thời gian → dừng và trả kết quả tốt nhất.", hint: "10–300 · Mặc định: 60s" },
    },
  },
  {
    id: "recovery",
    label: "Nghỉ ngơi",
    icon: "hotel",
    color: "text-rose-600",
    bg: "bg-rose-50",
    progressColor: "bg-rose-500",
    accent: "border-l-4 border-l-rose-500",
    params: ["overnight_recovery_hours"],
    descriptions: {
      overnight_recovery_hours: { label: "recovery_hours", desc: "Ngưỡng nghỉ ngơi tham chiếu cho L01. Ràng buộc thực tế vẫn theo ngày nghỉ bù và back-to-back checks.", hint: "12–72 giờ · Mặc định: 24 · Theo dõi" },
    },
  },
];

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
  params: readonly (keyof RuntimeConfig)[];
};

export const SHIFT_TYPE_GROUPS: readonly ShiftTypeGroup[] = [
  { id: "l01", label: "L01", subtitle: "Trực 24/24", icon: "emergency", color: "text-red-600", colorBg: "bg-red-50", borderColor: "border-red-400", description: "Ca trực liên tục 24h, có nghỉ bù", params: ["l01MinPerDay", "l01MaxPerDay", "l01MinPerWeek", "l01MaxPerWeek"] },
  { id: "l02", label: "L02", subtitle: "Thông tầm", icon: "schedule", color: "text-blue-600", colorBg: "bg-blue-50", borderColor: "border-blue-400", description: "Ca ngày, không nghỉ trưa", params: ["l02MinPerDay", "l02MaxPerDay", "l02MinPerWeek", "l02MaxPerWeek"] },
  { id: "l03", label: "L03", subtitle: "PK Dịch vụ", icon: "medical_services", color: "text-green-600", colorBg: "bg-green-50", borderColor: "border-green-400", description: "Ca khám dịch vụ, buổi sáng hoặc chiều", params: ["l03MinPerDay", "l03MaxPerDay", "l03MinPerWeek", "l03MaxPerWeek"] },
  { id: "l04", label: "L04", subtitle: "PK Chuyên gia", icon: "stethoscope", color: "text-purple-600", colorBg: "bg-purple-50", borderColor: "border-purple-400", description: "Ca khám chuyên sâu, thời gian dài hơn", params: ["l04MinPerDay", "l04MaxPerDay", "l04MinPerWeek", "l04MaxPerWeek"] },
] as const;

const SHIFT_PARAM_LABELS: Record<string, string> = {
  MinPerDay: "Tổng ca/ngày",
  MaxPerDay: "Trần ca/ngày",
  MinPerWeek: "Ca/người/tuần",
  MaxPerWeek: "Trần ca/người/tuần",
};

const SHIFT_PARAM_TOOLTIPS: Record<string, string> = {
  MinPerDay:
    "Tổng số ca L0X phải có mỗi ngày (cộng dồn mọi chuyên khoa). Thuật toán cố gắng đạt, không phá ràng buộc cứng.",
  MaxPerDay:
    "Trần tổng số ca L0X mỗi ngày. 0 = không đặt trần (theo target ca/người/tháng).",
  MinPerWeek:
    "Mỗi nhân sự tối thiểu X ca L0X trong 1 tuần — đảm bảo chia đều, tránh bỏ sót.",
  MaxPerWeek:
    "Mỗi nhân sự tối đa X ca L0X trong 1 tuần — chống tập trung quá nhiều ca vào một người. 0 = không giới hạn.",
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

const PERCENT_PARAMS = new Set(["greedy_coverage_threshold", "balance_score_min"]);

export function getParamBounds(param: string): { min: number; max: number; step: number } {
  if (param === "greedy_coverage_threshold" || param === "balance_score_min") return { min: 0.3, max: 1, step: 0.05 };
  if (param === "weekend_weight") return { min: 1, max: 5, step: 0.05 };
  if (param === "max_iterations" || param === "min_staff_per_shift") return { min: 0, max: 10, step: 1 };
  if (param === "max_staff_per_shift" || param === "max_shifts_per_staff") return { min: 0, max: 100, step: 1 };
  if (param === "min_shifts_per_staff") return { min: 0, max: 50, step: 1 };
  if (param === "backtrack_time_limit_seconds") return { min: 10, max: 300, step: 1 };
  if (param === "overnight_recovery_hours") return { min: 12, max: 72, step: 1 };
  return { min: 0, max: 100, step: 1 };
}

export function formatParamDisplay(param: string, numVal: number): string {
  if (PERCENT_PARAMS.has(param)) return `${Math.round(numVal * 100)}%`;
  if (param === "weekend_weight") return `${numVal.toFixed(1)}×`;
  if (param === "backtrack_time_limit_seconds") return `${numVal}s`;
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