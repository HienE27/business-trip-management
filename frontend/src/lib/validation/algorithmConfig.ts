// Smart validation rules cho algorithm config parameters
// Mỗi rule trả về warning string nếu value có vấn đề, hoặc null nếu OK

export type ValidationResult = {
  level: "warning" | "error";
  message: string;
};

export type ValidationRule = (value: number | boolean | string) => ValidationResult | null;

// Mapping từ paramKey (snake_case) → validation rule
export const PARAM_VALIDATIONS: Record<string, ValidationRule> = {
  weekend_weight: (v) => {
    const num = Number(v);
    if (num < 1.0) {
      return { level: "warning", message: "Sẽ tắt ưu tiên cuối tuần. Đặt ≥ 1.0 để bật." };
    }
    if (num > 4.5) {
      return { level: "warning", message: "Trọng số quá cao (> 4.5) có thể khiến NS ít được xếp cuối tuần quá mức." };
    }
    return null;
  },
  greedy_coverage_threshold: (v) => {
    const num = Number(v);
    if (num < 0.5) {
      return { level: "error", message: "Coverage < 50% → thuật toán có thể bỏ sót nhiều ca. Khuyến nghị ≥ 0.7." };
    }
    if (num < 0.7) {
      return { level: "warning", message: "Coverage thấp, nhiều ca sẽ bị thiếu. Khuyến nghị ≥ 0.85." };
    }
    return null;
  },
  balance_score_min: (v) => {
    const num = Number(v);
    if (num > 0.85) {
      return { level: "warning", message: "Ngưỡng cân bằng cao (> 85%) khó đạt. Có thể thuật toán fail hoặc chạy rất lâu." };
    }
    if (num < 0.5) {
      return { level: "warning", message: "Cân bằng thấp, NS có thể chênh lệch nhiều ca." };
    }
    return null;
  },
  overnight_recovery_hours: (v) => {
    const num = Number(v);
    if (num < 12) {
      return { level: "error", message: "Nghỉ giữa ca trực < 12 giờ có thể vi phạm quy định an toàn lao động." };
    }
    if (num < 24) {
      return { level: "warning", message: "Nghỉ < 24 giờ giữa các ca trực 24/24. Khuyến nghị = 24." };
    }
    if (num > 48) {
      return { level: "warning", message: "Nghỉ > 48 giờ có thể giảm hiệu suất xếp lịch." };
    }
    return null;
  },
  max_shifts_per_staff: (v) => {
    const num = Number(v);
    if (num > 25) {
      return { level: "warning", message: "Mỗi NS có thể bị xếp > 25 ca/kỳ → nguy cơ quá tải. Khuyến nghị ≤ 22." };
    }
    if (num > 0 && num < 8) {
      return { level: "warning", message: "Tối đa < 8 ca/kỳ → có thể không đáp ứng requirement cao." };
    }
    return null;
  },
  min_shifts_per_staff: (v) => {
    const num = Number(v);
    if (num > 15) {
      return { level: "warning", message: "Min ca > 15 có thể ép NS nhận nhiều ca hơn mong muốn." };
    }
    return null;
  },
  max_staff_per_shift: (v) => {
    const num = Number(v);
    if (num > 0 && num < 1) {
      return { level: "error", message: "Max NS/ca < 1 → không thể xếp lịch." };
    }
    return null;
  },
  /**
   * Per-shift-type min/max validations.
   * Logic: MinPerDay/MaxPerDay = tổng ca toàn khoa mỗi ngày.
   *        MinPerWeek/MaxPerWeek = số ca mỗi nhân sự mỗi tuần.
   * Validate: max >= min; min/ngày * ngày không vượt quá tổng ca khả thi;
   * min/tuần hợp lý (1-7).
   */
  l01MinPerDay: perShiftTypeValidation("day", "min"),
  l02MinPerDay: perShiftTypeValidation("day", "min"),
  l03MinPerDay: perShiftTypeValidation("day", "min"),
  l04MinPerDay: perShiftTypeValidation("day", "min"),
  l01MaxPerDay: perShiftTypeValidation("day", "max"),
  l02MaxPerDay: perShiftTypeValidation("day", "max"),
  l03MaxPerDay: perShiftTypeValidation("day", "max"),
  l04MaxPerDay: perShiftTypeValidation("day", "max"),
  l01MinPerWeek: perShiftTypeValidation("week", "min"),
  l02MinPerWeek: perShiftTypeValidation("week", "min"),
  l03MinPerWeek: perShiftTypeValidation("week", "min"),
  l04MinPerWeek: perShiftTypeValidation("week", "min"),
  l01MaxPerWeek: perShiftTypeValidation("week", "max"),
  l02MaxPerWeek: perShiftTypeValidation("week", "max"),
  l03MaxPerWeek: perShiftTypeValidation("week", "max"),
  l04MaxPerWeek: perShiftTypeValidation("week", "max"),
};

function perShiftTypeValidation(
  scope: "day" | "week",
  bound: "min" | "max",
): ValidationRule {
  return (v) => {
    const num = Number(v);
    if (Number.isNaN(num) || num < 0) {
      return { level: "error", message: "Giá trị phải là số không âm." };
    }
    if (scope === "day") {
      // Tổng ca toàn khoa/ngày. L01 thường 1-3, L03/L04 có thể cao hơn (đa chuyên khoa).
      if (bound === "min" && num > 0 && num < 1) {
        return { level: "warning", message: "Min < 1 ca/ngày → mỗi ngày có thể không phát sinh ca nào." };
      }
      if (bound === "max" && num > 0 && num > 30) {
        return { level: "warning", message: "Trần > 30 ca/ngày → bất thường. Kiểm tra lại eligibility." };
      }
    } else {
      // Ca/người/tuần — 1 tuần tối đa 7 ngày.
      if (bound === "min" && num > 7) {
        return { level: "warning", message: "Min > 7 ca/người/tuần vượt quá 1 ca/ngày — không khả thi." };
      }
      if (bound === "max" && num > 0 && num > 7) {
        const recommendation = num > 10
          ? " Khuyến nghị ≤ 6 để đảm bảo feasibility."
          : " Khuyến nghị ≤ 6.";
        return { level: "warning", message: `Trần > 7 ca/người/tuần vượt quá 1 ca/ngày — không khả thi.${recommendation}` };
      }
    }
    return null;
  };
}

export function getParamValidation(paramKey: string, value: number | boolean | string): ValidationResult | null {
  const rule = PARAM_VALIDATIONS[paramKey];
  if (!rule) return null;
  return rule(value);
}