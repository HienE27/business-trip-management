import type { ConflictItem } from "@/types/schedule";

/**
 * Thông tin loại xung đột - bao gồm nhãn, màu sắc và icon.
 * @example
 * ```ts
 * { type: "Lịch trực", color: "text-red-600", icon: "emergency" }
 * ```
 */
export type ConflictTypeInfo = {
  /** Nhãn hiển thị của loại xung đột (VD: "Lịch trực", "Nghỉ phép") */
  type: string;
  /** Tailwind color class cho icon và text (VD: "text-red-600") */
  color: string;
  /** Material Symbols icon name (VD: "emergency", "event_busy") */
  icon: string;
};

/**
 * Map từ keyword trong detail message sang ConflictTypeInfo.
 * Thứ tự trong map quyết định độ ưu tiên khi match.
 */
const CONFLICT_TYPE_MAP: Record<string, ConflictTypeInfo> = {
  "trực 24/24": { type: "Lịch trực", color: "text-red-600", icon: "emergency" },
  L01: { type: "Lịch trực", color: "text-red-600", icon: "emergency" },
  "nghỉ phép": { type: "Nghỉ phép", color: "text-secondary", icon: "event_busy" },
  Leave: { type: "Nghỉ phép", color: "text-secondary", icon: "event_busy" },
  "nghỉ bù": { type: "Ngày nghỉ bù", color: "text-tertiary", icon: "calendar_month" },
  compensation: { type: "Ngày nghỉ bù", color: "text-tertiary", icon: "calendar_month" },
  "liền kề": { type: "Ca liền kề", color: "text-purple-600", icon: "schedule" },
  "back-to-back": { type: "Ca liền kề", color: "text-purple-600", icon: "schedule" },
};

/**
 * Giá trị mặc định khi không match được keyword nào.
 */
const DEFAULT_CONFLICT_TYPE: ConflictTypeInfo = {
  type: "Khác",
  color: "text-gray-600",
  icon: "warning",
};

/**
 * Trích xuất loại xung đột từ detail message.
 * 
 * @param detail - Chuỗi mô tả xung đột (có thể undefined)
 * @returns ConflictTypeInfo chứa type label, color class và icon name
 * 
 * @example
 * ```ts
 * const info = getConflictType("Trùng lịch trực 24/24 với ngày nghỉ bù");
 * // info = { type: "Lịch trực", color: "text-red-600", icon: "emergency" }
 * ```
 * 
 * @example
 * ```tsx
 * <span className={conflictType.color}>
 *   <span className="material-symbols-outlined">{conflictType.icon}</span>
 *   {conflictType.type}
 * </span>
 * ```
 */
export function getConflictType(detail: string | undefined): ConflictTypeInfo {
  if (!detail) return DEFAULT_CONFLICT_TYPE;

  for (const [keyword, info] of Object.entries(CONFLICT_TYPE_MAP)) {
    if (detail.includes(keyword)) {
      return info;
    }
  }

  return DEFAULT_CONFLICT_TYPE;
}

/**
 * Lấy Tailwind color class theo mức độ nghiêm trọng của xung đột.
 * 
 * @param severity - Mức độ nghiêm trọng ("Chặn lưu" | "Cảnh báo" | khác)
 * @returns Tailwind color class (VD: "text-error", "text-tertiary")
 * 
 * @example
 * ```tsx
 * <span className={getConflictSeverityColor(conflict.severity)}>
 *   {conflict.severity}
 * </span>
 * ```
 */
export function getConflictSeverityColor(severity: string): string {
  switch (severity) {
    case "Chặn lưu":
      return "text-error";
    case "Cảnh báo":
      return "text-tertiary";
    default:
      return "text-on-surface-variant";
  }
}

/**
 * Lấy tone cho Badge component dựa trên mức độ nghiêm trọng.
 * 
 * @param severity - Mức độ nghiêm trọng
 * @returns "error" cho "Chặn lưu", "warning" cho các trường hợp khác
 * 
 * @example
 * ```tsx
 * <Badge tone={getConflictSeverityBadgeTone(conflict.severity)}>
 *   {conflict.severity}
 * </Badge>
 * ```
 */
export function getConflictSeverityBadgeTone(severity: string): "error" | "warning" {
  return severity === "Chặn lưu" ? "error" : "warning";
}

/**
 * Lọc danh sách xung đột theo mức độ nghiêm trọng.
 * 
 * @param conflicts - Mảng các xung đột cần lọc
 * @param severity - Mức độ cần lọc ("Chặn lưu" hoặc "Cảnh báo")
 * @returns Mảng xung đột chỉ chứa các item có severity tương ứng
 * 
 * @example
 * ```ts
 * const blockingConflicts = filterConflictsBySeverity(conflicts, "Chặn lưu");
 * const warnings = filterConflictsBySeverity(conflicts, "Cảnh báo");
 * ```
 */
export function filterConflictsBySeverity(
  conflicts: ConflictItem[],
  severity: "Chặn lưu" | "Cảnh báo"
): ConflictItem[] {
  return conflicts.filter((c) => c.severity === severity);
}

/**
 * Sắp xếp danh sách xung đột theo mức độ nghiêm trọng.
 * "Chặn lưu" luôn hiển thị trước "Cảnh báo".
 * 
 * @param conflicts - Mảng xung đột cần sắp xếp
 * @returns Mảng mới đã được sắp xếp (không mutate array gốc)
 * 
 * @example
 * ```tsx
 * const sorted = sortConflictsBySeverity(conflicts);
 * // First items will be "Chặn lưu", then "Cảnh báo"
 * ```
 */
export function sortConflictsBySeverity(conflicts: ConflictItem[]): ConflictItem[] {
  return [...conflicts].sort((a, b) => {
    const priority = { "Chặn lưu": 0, "Cảnh báo": 1 };
    return (priority[a.severity] ?? 2) - (priority[b.severity] ?? 2);
  });
}

/**
 * Nhóm danh sách xung đột theo tên nhân sự.
 * 
 * @param conflicts - Mảng xung đột cần nhóm
 * @returns Map với key là staffName, value là mảng xung đột của nhân sự đó
 * 
 * @example
 * ```ts
 * const grouped = groupConflictsByStaff(conflicts);
 * for (const [staffName, staffConflicts] of grouped) {
 *   console.log(`${staffName}: ${staffConflicts.length} xung đột`);
 * }
 * ```
 */
export function groupConflictsByStaff(conflicts: ConflictItem[]): Map<string, ConflictItem[]> {
  const grouped = new Map<string, ConflictItem[]>();
  for (const conflict of conflicts) {
    const key = conflict.staffName || "Unknown";
    if (!grouped.has(key)) {
      grouped.set(key, []);
    }
    grouped.get(key)!.push(conflict);
  }
  return grouped;
}

/**
 * Normalize a list of conflict-reason strings so two conflicts with the same
 * reasons in different orders or with duplicates collide on the same key.
 * Used by /reports/conflicts to group conflicts by reason set without
 * producing duplicate buckets (REPORTS-CONFLICT-001).
 *
 * @param reasons raw conflictReasons array from the backend
 * @returns sorted, deduplicated, trimmed list
 */
export function normalizeConflictReasons(reasons: readonly string[] | undefined): string[] {
  if (!reasons || reasons.length === 0) return [];
  const set = new Set<string>();
  for (const reason of reasons) {
    if (typeof reason === "string") {
      const trimmed = reason.trim();
      if (trimmed.length > 0) set.add(trimmed);
    }
  }
  return Array.from(set).sort((a, b) => a.localeCompare(b, "vi"));
}
